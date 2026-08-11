/*
 * Copyright 2026 The shipyard Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shipyard.worker.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.worker.dto.DeployRequest;
import com.shipyard.worker.dto.WorkerTaskResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Worker HTTP 客户端 — shipyard 后端调 worker (Go) 的代理层.
 *
 * <p>架构: shipyard Java 完全不知道 k8s 存在,所有集群操作走 worker.
 * WorkerClient 是唯一接触面,调 worker 的 {@code /api/v1/cluster/*} + {@code /api/v1/tasks/*} 端点.
 *
 * <p>设计:
 * <ul>
 *   <li>用 JDK 11+ 内置 {@link HttpClient}, 零依赖, 跟 shipyard "只引必要依赖" 风格一致</li>
 *   <li>超时 5s, 重试 2 次 (1+2 总共 3 次尝试), 指数退避 100ms / 300ms</li>
 *   <li>所有方法返 worker 响应的 data 部分 (M9 commit-5: 5 deploy 方法返 {@link WorkerTaskResponse} 的 data map,
 *       3 读类方法返 {@code List<Map>})</li>
 *   <li>worker 不可达 / 4xx / 5xx → 抛 {@link BusinessException}, 业务码 500 (内部错误)</li>
 *   <li>鉴权: shipyard 调 worker 时 {@code Authorization: Bearer <token>} 头 (M9 commit-5 加),
 *       token 走 env.workerTokenEnc 解密 (M8.2 已实现, 这里只引用)</li>
 * </ul>
 *
 * <p>commit-5 加 5 deploy 方法:
 * <ul>
 *   <li>{@link #deploy(String, String, DeployRequest)} — POST /api/v1/tasks/deploy</li>
 *   <li>{@link #rollback(String, String, DeployRequest)} — POST /api/v1/tasks/rollback (复用 deploy 端点, body 同)</li>
 *   <li>{@link #scale(String, String, String, String, String, int)} — POST /api/v1/tasks/scale</li>
 *   <li>{@link #stop(String, String, String, String, String)} — POST /api/v1/tasks/stop (scale replicas=0)</li>
 *   <li>{@link #getManifest(String, String, String, String, String)} — GET /api/v1/tasks/manifest</li>
 * </ul>
 */
@Slf4j
@Component
public class WorkerClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ATTEMPTS = 3;       // 1 + 2 重试
    private static final long RETRY_BASE_MS = 100;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * 默认构造 — 注入默认 HttpClient.
     *
     * <p>用 {@code @Autowired} 显式标记, Spring 启动时挑这个 (跟测试用二参构造区分).
     */
    @Autowired
    public WorkerClient(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .version(HttpClient.Version.HTTP_1_1)
                .build(),
            objectMapper);
    }

    /** 测试用构造 — 注入 mock HttpClient. */
    public WorkerClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // Cluster 读类代理 (M8.3) — 透传 worker 响应
    // ============================================================

    /**
     * 调 worker 拿所有 namespace 列表.
     *
     * @param workerUrl worker 服务的 base URL (e.g. http://worker-dev:8888)
     * @return worker 返的 {code, message, data} 包装, 这里只返 data 部分 (List)
     */
    public List<Map<String, Object>> listNamespaces(String workerUrl) {
        return getJsonList(workerUrl + "/api/v1/cluster/namespaces");
    }

    /**
     * 调 worker 拿指定 namespace 的 pod 列表.
     */
    public List<Map<String, Object>> listPods(String workerUrl, String namespace) {
        String url = workerUrl + "/api/v1/cluster/pods?namespace=" + urlEncode(namespace);
        return getJsonList(url);
    }

    /**
     * 调 worker 拿指定 namespace 的 deployment 列表.
     */
    public List<Map<String, Object>> listDeployments(String workerUrl, String namespace) {
        String url = workerUrl + "/api/v1/cluster/deployments?namespace=" + urlEncode(namespace);
        return getJsonList(url);
    }

    // ============================================================
    // Deploy 写类代理 (M9 commit-5) — 5 deploy 方法
    // ============================================================

    /**
     * 调 worker 真 apply 资源到 k8s.
     *
     * <p>worker 走 client-go (DynamicClient + unstructured) apply yaml,
     * 返 {phase: "applied/created/updated", message, manifest}.
     *
     * <p>异步语义: shipyard 调完不等 worker 完事, 写 deploy_record 标 PENDING,
     * worker 异步 apply 完回调 shipyard /api/internal/deploy/callback 改终态
     * (M9.5 加 callback 端点, commit-5 阶段 worker 同步返 200, shipyard 标 RUNNING).
     *
     * @param workerUrl worker 服务 base URL
     * @param token 鉴权 token (env.workerTokenEnc 解密后的明文)
     * @param req DeployRequest (deployRecordId + namespace + yaml + resourceName)
     * @return WorkerTaskResponse.data (worker 返的 {phase, message, manifest})
     */
    public Map<String, Object> deploy(String workerUrl, String token, DeployRequest req) {
        return postJson(workerUrl + "/api/v1/tasks/deploy", token, req);
    }

    /**
     * 调 worker 回滚 — 把历史 snapshot 的 yaml 重 apply 一遍.
     *
     * <p>实现: 跟 deploy 走同一个 worker 端点 {@code /api/v1/tasks/deploy} (rollback 语义
     * 在 shipyard 端已经确定 — 拿旧 snapshot 的 yaml 重发, worker 不区分 deploy/rollback).
     */
    public Map<String, Object> rollback(String workerUrl, String token, DeployRequest req) {
        return postJson(workerUrl + "/api/v1/tasks/deploy", token, req);
    }

    /**
     * 调 worker scale 资源副本数.
     *
     * @param kind K8s 资源 kind (例 "Deployment")
     * @param name 资源名
     * @param namespace 目标 namespace
     * @param replicas 目标副本数
     */
    public Map<String, Object> scale(String workerUrl, String token, String kind, String name,
                                     String namespace, int replicas) {
        Map<String, Object> body = Map.of(
                "kind", kind,
                "name", name,
                "namespace", namespace,
                "replicas", replicas
        );
        return postJson(workerUrl + "/api/v1/tasks/scale", token, body);
    }

    /**
     * 调 worker 停服 — scale 到 0 副本 (快捷方法).
     */
    public Map<String, Object> stop(String workerUrl, String token, String kind, String name,
                                    String namespace) {
        return scale(workerUrl, token, kind, name, namespace, 0);
    }

    /**
     * 调 worker 拿 k8s 真生效的 manifest (高级模式 diff 用).
     *
     * @return manifest yaml 字符串
     */
    public String getManifest(String workerUrl, String token, String kind, String name,
                               String namespace) {
        String url = workerUrl + "/api/v1/tasks/manifest"
                + "?kind=" + urlEncode(kind)
                + "&name=" + urlEncode(name)
                + "&namespace=" + urlEncode(namespace);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/yaml")
                .header("Authorization", "Bearer " + (token != null ? token : ""))
                .GET()
                .build();
        String body = sendWithRetry(req, url);
        // worker 返 raw yaml 字符串 (高级模式给前端直接展示)
        return body;
    }

    // ============================================================
    // 私有 — HTTP POST + 业务码 unwrap + 重试
    // ============================================================

    /**
     * POST worker 端点, JSON body, 返 {@link WorkerTaskResponse.data} (Map).
     *
     * <p>worker 返 {code, message, data} 包装, 业务码 ≠ 0 抛错.
     */
    private Map<String, Object> postJson(String url, String token, Object body) {
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "请求体 JSON 序列化失败: " + e.getMessage());
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + (token != null ? token : ""))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();

        String respBody = sendWithRetry(req, url);
        return parseWorkerTaskData(respBody);
    }

    /**
     * GET worker 端点, 返 data 字段 (List).
     * worker 返的格式: {code:0, message:"ok", data:[...]}
     */
    private List<Map<String, Object>> getJsonList(String url) {
        Map<String, Object> body = getJsonMap(url);
        Object data = body.get("data");
        if (data == null) {
            return List.of();
        }
        if (data instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) data;
            return list;
        }
        return objectMapper.convertValue(data, new TypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * GET worker 端点, 返完整 {code, message, data} body.
     */
    private Map<String, Object> getJsonMap(String url) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        String body = sendWithRetry(req, url);
        return parseJsonMap(body);
    }

    /**
     * 解析 worker 返的 JSON 字符串成 {code, message, data} Map, 业务码 ≠ 0 抛错.
     */
    private Map<String, Object> parseWorkerTaskData(String body) {
        Map<String, Object> map = parseJsonMap(body);
        int code = ((Number) map.getOrDefault("code", -1)).intValue();
        if (code != 0) {
            String message = (String) map.getOrDefault("message", "unknown");
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "worker 业务错: code=" + code + " message=" + message);
        }
        Object data = map.get("data");
        if (data == null) return Map.of();
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) data;
            return dataMap;
        }
        return objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {});
    }

    private Map<String, Object> parseJsonMap(String body) {
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "worker 响应 JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 发请求, 重试 2 次 (总 3 次尝试), 失败抛 BusinessException.
     */
    private String sendWithRetry(HttpRequest req, String urlForLog) {
        Exception lastErr = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                String body = resp.body();

                if (status >= 200 && status < 300) {
                    return body;
                }
                // 4xx 不重试
                if (status >= 400 && status < 500) {
                    log.warn("worker 返 4xx, 不重试: status={} url={}", status, urlForLog);
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "worker 拒绝请求: status=" + status + " body=" + truncate(body, 200));
                }
                // 5xx 重试
                lastErr = new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "worker 返 5xx: status=" + status);
                log.warn("worker 返 5xx, 重试 {}/{}: status={} url={}",
                        attempt, MAX_ATTEMPTS, status, urlForLog);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                lastErr = e;
                log.warn("worker 调失败, 重试 {}/{}: url={} err={}",
                        attempt, MAX_ATTEMPTS, urlForLog, e.getMessage());
            }
            // 指数退避
            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(RETRY_BASE_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
            "worker 不可达: " + urlForLog + " cause=" +
            (lastErr != null ? lastErr.getMessage() : "unknown"));
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}

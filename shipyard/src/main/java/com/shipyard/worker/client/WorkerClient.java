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
import com.shipyard.dto.ApiResponse;
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
 * WorkerClient 是唯一接触面,调 worker 的 {@code /api/v1/cluster/*} 端点.
 *
 * <p>设计:
 * <ul>
 *   <li>用 JDK 11+ 内置 {@link HttpClient}, 零依赖, 跟 shipyard "只引必要依赖" 风格一致</li>
 *   <li>超时 5s, 重试 2 次 (1+2 总共 3 次尝试), 指数退避 100ms / 300ms</li>
 *   <li>所有方法返 {@code Map<String,Object>} — worker 返的是 {code, message, data} 包装,
 *       这里直接转给前端, 不做字段重映射</li>
 *   <li>worker 不可达 / 4xx / 5xx → 抛 {@link BusinessException}, 业务码 500 (内部错误)</li>
 * </ul>
 *
 * <p>可测性: HttpClient 注入, 测试时换成本地 mock server (见 {@code WorkerClientTest}).
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
     * 否则 Spring 看到两个构造 + 都没注解, 选不出, 抛 NoSuchMethodException&lt;init&gt;().
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
    // Cluster 读类代理 — 透传 worker 响应
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
    // 私有 — HTTP GET + 业务码 unwrap + 重试
    // ============================================================

    /**
     * GET worker 端点, 返 data 字段 (List).
     * worker 返的格式: {code:0, message:"ok", data:[...]}
     */
    private List<Map<String, Object>> getJsonList(String url) {
        Map<String, Object> body = getJson(url);
        Object data = body.get("data");
        if (data == null) {
            return List.of();
        }
        if (data instanceof List) {
            // unchecked cast 是 OK 的: data 由 worker JSON 序列化, 内容是 List<Map>
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) data;
            return list;
        }
        // 兜底: 序列化再反序列化 (防御)
        return objectMapper.convertValue(data, new TypeReference<List<Map<String, Object>>>() {});
    }

    /**
     * GET worker 端点, 返完整 {code, message, data} body.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String url) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        String body = sendWithRetry(req, url);
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
                // 4xx 不重试 (客户端错, 重试也没用)
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
                throw e;  // 4xx 不重试
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

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.worker.dto.DeployRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WorkerClient 单元测试 — 用 JDK HttpServer 起本地 mock server, 跑真 HTTP.
 *
 * <p>不 mock HttpClient (mockito 太繁琐, 真 HTTP 更可靠), 改 mock 服务端行为.
 */
@DisplayName("WorkerClient 调 worker HTTP")
class WorkerClientTest {

    private HttpServer mockServer;
    private String baseUrl;
    private ObjectMapper mapper;
    private WorkerClient client;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = mockServer.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;
        mockServer.start();
        mapper = new ObjectMapper();
        client = new WorkerClient(mapper);
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    // ==================== Happy path ====================

    @Test
    @DisplayName("listNamespaces 返 worker data 列表")
    void listNamespaces_Success() throws Exception {
        mockServer.createContext("/api/v1/cluster/namespaces", new JsonHandler(
            "{\"code\":0,\"message\":\"ok\",\"data\":[" +
            "{\"name\":\"default\",\"status\":\"Active\"}," +
            "{\"name\":\"kube-system\",\"status\":\"Active\"}" +
            "]}"
        ));

        List<Map<String, Object>> result = client.listNamespaces(baseUrl);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("name", "default");
        assertThat(result.get(1)).containsEntry("name", "kube-system");
    }

    @Test
    @DisplayName("listPods 带 namespace query 参数, 透传成功")
    void listPods_WithNamespace_Success() throws Exception {
        String[] capturedPath = {null};
        mockServer.createContext("/api/v1/cluster/pods", exchange -> {
            capturedPath[0] = exchange.getRequestURI().toString();
            String body = "{\"code\":0,\"data\":[{\"name\":\"p1\",\"namespace\":\"shipyard\"}]}";
            respondJson(exchange, 200, body);
        });

        List<Map<String, Object>> result = client.listPods(baseUrl, "shipyard");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("name", "p1");
        assertThat(capturedPath[0]).isEqualTo("/api/v1/cluster/pods?namespace=shipyard");
    }

    @Test
    @DisplayName("listDeployments 返 mock deployment 列表")
    void listDeployments_Success() throws Exception {
        mockServer.createContext("/api/v1/cluster/deployments", new JsonHandler(
            "{\"code\":0,\"data\":[{\"name\":\"shipyard-web\",\"replicas\":1}]}"
        ));

        List<Map<String, Object>> result = client.listDeployments(baseUrl, "shipyard");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("name", "shipyard-web");
    }

    @Test
    @DisplayName("data 为 null 时返空列表 (worker 端空数据兜底)")
    void listNamespaces_NullData_ReturnsEmptyList() throws Exception {
        mockServer.createContext("/api/v1/cluster/namespaces", new JsonHandler(
            "{\"code\":0,\"data\":null}"
        ));

        List<Map<String, Object>> result = client.listNamespaces(baseUrl);
        assertThat(result).isEmpty();
    }

    // ==================== 错误处理 ====================

    @Test
    @DisplayName("worker 返 4xx 不重试, 抛 BusinessException")
    void workerReturns4xx_NoRetry_ThrowsException() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        mockServer.createContext("/api/v1/cluster/namespaces", exchange -> {
            callCount.incrementAndGet();
            respondJson(exchange, 404, "{\"message\":\"not found\"}");
        });

        assertThatThrownBy(() -> client.listNamespaces(baseUrl))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("worker 拒绝请求")
                .hasMessageContaining("status=404");

        assertThat(callCount.get())
                .as("4xx 不应重试")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("worker 返 5xx 重试 2 次 (总 3 次), 最后失败抛异常")
    void workerReturns5xx_Retries3Times() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        mockServer.createContext("/api/v1/cluster/namespaces", exchange -> {
            callCount.incrementAndGet();
            respondJson(exchange, 500, "{\"error\":\"internal\"}");
        });

        assertThatThrownBy(() -> client.listNamespaces(baseUrl))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("worker 不可达");

        assertThat(callCount.get())
                .as("5xx 应该重试 3 次 (1 + 2 retry)")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("worker 不可达 (连接拒绝) 重试 3 次后失败")
    void workerUnreachable_Retries3Times() {
        // 找一个肯定没监听的端口
        String badUrl = "http://127.0.0.1:1";

        assertThatThrownBy(() -> client.listNamespaces(badUrl))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("worker 不可达");
    }

    @Test
    @DisplayName("worker 返非法 JSON 抛 BusinessException")
    void workerReturnsInvalidJson_ThrowsException() throws Exception {
        mockServer.createContext("/api/v1/cluster/namespaces", new JsonHandler("not json {"));

        assertThatThrownBy(() -> client.listNamespaces(baseUrl))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON 解析失败");
    }

    // ==================== 重试恢复 ====================

    @Test
    @DisplayName("5xx 第一次, 第二次 200 → 重试恢复, 返数据")
    void retryThenSuccess() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        mockServer.createContext("/api/v1/cluster/namespaces", exchange -> {
            int n = callCount.incrementAndGet();
            if (n < 2) {
                respondJson(exchange, 500, "{\"error\":\"oops\"}");
            } else {
                respondJson(exchange, 200, "{\"code\":0,\"data\":[{\"name\":\"recovered\"}]}");
            }
        });

        List<Map<String, Object>> result = client.listNamespaces(baseUrl);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("name", "recovered");
        assertThat(callCount.get()).isEqualTo(2);
    }

    // ==================== M9 commit-5: deploy 5 方法 ====================

    @Test
    @DisplayName("deploy: POST /api/v1/tasks/deploy, 鉴权 + body + 返 data map")
    void deploy_Success() throws Exception {
        String[] capturedAuth = {null};
        String[] capturedBody = {null};
        mockServer.createContext("/api/v1/tasks/deploy", exchange -> {
            capturedAuth[0] = exchange.getRequestHeaders().getFirst("Authorization");
            // 读 body
            try (var is = exchange.getRequestBody()) {
                capturedBody[0] = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            String body = "{\"code\":0,\"message\":\"applied\",\"data\":"
                    + "{\"phase\":\"created\",\"message\":\"deployment.apps/myapp-dev created\""
                    + ",\"manifest\":\"apiVersion: apps/v1\\nkind: Deployment\"}}";
            respondJson(exchange, 200, body);
        });

        DeployRequest req = new DeployRequest();
        req.setDeployRecordId(1234L);
        req.setNamespace("shipyard-dev");
        req.setYaml("apiVersion: apps/v1\nkind: Deployment\n...");
        req.setResourceName("myapp-dev");

        Map<String, Object> data = client.deploy(baseUrl, "test-token", req);

        assertThat(data).containsEntry("phase", "created");
        assertThat(data).containsKey("message");
        assertThat(capturedAuth[0]).isEqualTo("Bearer test-token");
        assertThat(capturedBody[0]).contains("\"deployRecordId\":1234");
        assertThat(capturedBody[0]).contains("\"namespace\":\"shipyard-dev\"");
    }

    @Test
    @DisplayName("rollback: 复用 /api/v1/tasks/deploy 端点, body 含历史 yaml")
    void rollback_Success() throws Exception {
        mockServer.createContext("/api/v1/tasks/deploy", new JsonHandler(
            "{\"code\":0,\"data\":{\"phase\":\"updated\",\"message\":\"rolled back to v1\"}}"
        ));

        DeployRequest req = new DeployRequest();
        req.setDeployRecordId(200L);
        req.setNamespace("shipyard-dev");
        req.setYaml("apiVersion: apps/v1\nkind: Deployment\n# old version");

        Map<String, Object> data = client.rollback(baseUrl, "tok", req);
        assertThat(data).containsEntry("phase", "updated");
    }

    @Test
    @DisplayName("scale: POST /api/v1/tasks/scale, body 含 kind/name/namespace/replicas")
    void scale_Success() throws Exception {
        String[] capturedBody = {null};
        mockServer.createContext("/api/v1/tasks/scale", exchange -> {
            try (var is = exchange.getRequestBody()) {
                capturedBody[0] = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            String body = "{\"code\":0,\"data\":{\"phase\":\"scaled\",\"replicas\":5}}";
            respondJson(exchange, 200, body);
        });

        Map<String, Object> data = client.scale(baseUrl, "tok", "Deployment", "myapp-dev",
                "shipyard-dev", 5);

        assertThat(data).containsEntry("phase", "scaled");
        assertThat(data).containsEntry("replicas", 5);
        assertThat(capturedBody[0]).contains("\"kind\":\"Deployment\"");
        assertThat(capturedBody[0]).contains("\"name\":\"myapp-dev\"");
        assertThat(capturedBody[0]).contains("\"replicas\":5");
    }

    @Test
    @DisplayName("stop: 走 scale 端点, replicas=0")
    void stop_Success() throws Exception {
        String[] capturedBody = {null};
        mockServer.createContext("/api/v1/tasks/scale", exchange -> {
            try (var is = exchange.getRequestBody()) {
                capturedBody[0] = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            String body = "{\"code\":0,\"data\":{\"phase\":\"scaled\",\"replicas\":0}}";
            respondJson(exchange, 200, body);
        });

        Map<String, Object> data = client.stop(baseUrl, "tok", "Deployment", "myapp-dev",
                "shipyard-dev");
        assertThat(data).containsEntry("replicas", 0);
        assertThat(capturedBody[0]).contains("\"replicas\":0");
    }

    @Test
    @DisplayName("getManifest: GET /api/v1/tasks/manifest, 返 raw yaml 字符串")
    void getManifest_Success() throws Exception {
        String[] capturedPath = {null};
        mockServer.createContext("/api/v1/tasks/manifest", exchange -> {
            capturedPath[0] = exchange.getRequestURI().toString();
            String yaml = "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: myapp-dev";
            byte[] bytes = yaml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/yaml");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        String yaml = client.getManifest(baseUrl, "tok", "Deployment", "myapp-dev", "shipyard-dev");

        assertThat(yaml).contains("apiVersion: apps/v1");
        assertThat(yaml).contains("name: myapp-dev");
        assertThat(capturedPath[0])
                .contains("kind=Deployment")
                .contains("name=myapp-dev")
                .contains("namespace=shipyard-dev");
    }

    @Test
    @DisplayName("deploy 业务错 (worker 返 code=400): 抛 BusinessException")
    void deploy_BusinessError_ThrowsException() throws Exception {
        mockServer.createContext("/api/v1/tasks/deploy", new JsonHandler(
            "{\"code\":400,\"message\":\"yaml invalid: kind required\"}"
        ));

        DeployRequest req = new DeployRequest();
        req.setDeployRecordId(1L);
        req.setNamespace("ns");
        req.setYaml("invalid");

        assertThatThrownBy(() -> client.deploy(baseUrl, "tok", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("worker 业务错")
                .hasMessageContaining("yaml invalid");
    }

    @Test
    @DisplayName("deploy token null: 鉴权头变 'Bearer' (空), worker 应返 401")
    void deploy_NullToken_StillSendsAuth() throws Exception {
        String[] capturedAuth = {null};
        mockServer.createContext("/api/v1/tasks/deploy", exchange -> {
            capturedAuth[0] = exchange.getRequestHeaders().getFirst("Authorization");
            respondJson(exchange, 401, "{\"code\":401,\"message\":\"unauthorized\"}");
        });

        DeployRequest req = new DeployRequest();
        req.setDeployRecordId(1L);
        req.setNamespace("ns");
        req.setYaml("yaml");

        // 不抛 — 4xx 路径在 client 不重试, 直接抛 BusinessException
        assertThatThrownBy(() -> client.deploy(baseUrl, null, req))
                .isInstanceOf(BusinessException.class);
        // JDK HttpClient 自动 trim 掉 "Bearer " 后的空格, 变成 "Bearer"
        assertThat(capturedAuth[0]).isEqualTo("Bearer");
    }

    // ==================== 辅助 ====================

    /** 简单 handler, 固定返指定 JSON. */
    private static class JsonHandler implements HttpHandler {
        private final String body;
        JsonHandler(String body) { this.body = body; }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            respondJson(exchange, 200, body);
        }
    }

    private static void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

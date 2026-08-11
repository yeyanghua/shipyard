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

package com.shipyard.worker.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.entity.Env;
import com.shipyard.mapper.EnvMapper;
import com.shipyard.worker.client.WorkerClient;
import com.shipyard.worker.dto.WorkerHeartbeatRequest;
import com.shipyard.worker.dto.WorkerRegisterRequest;
import com.shipyard.worker.dto.WorkerRegisterResponse;
import com.shipyard.worker.entity.Worker;
import com.shipyard.worker.mapper.WorkerMapper;
import com.shipyard.worker.service.impl.WorkerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkerServiceImpl 单元测试 — Mockito mock 掉 mapper / client, 测 service 业务逻辑.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerService 业务逻辑")
class WorkerServiceImplTest {

    @Mock private WorkerMapper workerMapper;
    @Mock private EnvMapper envMapper;
    @Mock private WorkerClient workerClient;

    @InjectMocks private WorkerServiceImpl workerService;

    private WorkerRegisterRequest validReq;

    @BeforeEach
    void setUp() {
        validReq = new WorkerRegisterRequest();
        validReq.setWorkerName("worker-test-01");
        validReq.setEnv("dev");
        validReq.setWorkerUrl("http://localhost:8888");
        validReq.setWorkerToken("test-token-xyz");
        validReq.setVersion("0.1.0");
    }

    // ==================== register ====================

    @Test
    @DisplayName("register 成功: env 存在 + worker 不存在 → 插入新行 + 返 ID")
    void register_NewWorker_Success() {
        when(envMapper.selectIdByNameRaw("dev")).thenReturn(100L);
        when(workerMapper.selectOne(any())).thenReturn(null);
        when(workerMapper.insert(any(Worker.class))).thenAnswer(inv -> {
            Worker w = inv.getArgument(0);
            w.setId(1L);
            return 1;
        });

        WorkerRegisterResponse resp = workerService.register(validReq);

        assertThat(resp.getWorkerId()).isEqualTo(1L);
        assertThat(resp.getHeartbeatIntervalSec()).isEqualTo(30);
        verify(workerMapper, times(1)).insert(any(Worker.class));
    }

    @Test
    @DisplayName("register: env 不存在 → 抛 NOT_FOUND, 不写 worker 表")
    void register_EnvNotFound_ThrowsException() {
        when(envMapper.selectIdByNameRaw("dev")).thenReturn(null);

        assertThatThrownBy(() -> workerService.register(validReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("环境不存在");

        verify(workerMapper, never()).insert(any(Worker.class));
    }

    @Test
    @DisplayName("register 幂等: worker URL + env 已存在 → 复用 ID, 不 insert 只 update")
    void register_ExistingWorker_ReusesId() {
        when(envMapper.selectIdByNameRaw("dev")).thenReturn(100L);
        Worker existing = new Worker();
        existing.setId(42L);
        existing.setEnvId(100L);
        existing.setWorkerUrl("http://localhost:8888");
        when(workerMapper.selectOne(any())).thenReturn(existing);

        WorkerRegisterResponse resp = workerService.register(validReq);

        assertThat(resp.getWorkerId()).isEqualTo(42L);
        verify(workerMapper, never()).insert(any(Worker.class));
        verify(workerMapper, times(1)).updateById(any(Worker.class));
    }

    @Test
    @DisplayName("register token 哈希入库, 不存明文")
    void register_TokenHashed_NotPlaintext() {
        when(envMapper.selectIdByNameRaw("dev")).thenReturn(100L);
        when(workerMapper.selectOne(any())).thenReturn(null);
        when(workerMapper.insert(any(Worker.class))).thenAnswer(inv -> {
            Worker w = inv.getArgument(0);
            // 验证 token hash 是 64 字符 hex, 不是 "test-token-xyz"
            assertThat(w.getWorkerTokenHash())
                    .as("token 必须 SHA-256 哈希")
                    .hasSize(64)  // SHA-256 = 32 bytes = 64 hex chars
                    .doesNotContain("test-token-xyz");
            w.setId(1L);
            return 1;
        });

        workerService.register(validReq);
        verify(workerMapper, times(1)).insert(any(Worker.class));
    }

    // ==================== heartbeat (M9 commit-4) ====================
    // commit-4 后 heartbeat 调 updateHeartbeatWithHealth, 旧 updateHeartbeat 保留兼容

    @Test
    @DisplayName("heartbeat 成功 (HEALTHY): 调 updateHeartbeatWithHealth, 默认 HEALTHY")
    void heartbeat_Success() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setWorkerId(1L);
        req.setStatus("online");
        when(workerMapper.updateHeartbeatWithHealth(eq(1L), any(LocalDateTime.class),
                eq("online"), eq("HEALTHY"), eq(null))).thenReturn(1);

        workerService.heartbeat(1L, req);

        verify(workerMapper, times(1)).updateHeartbeatWithHealth(
                eq(1L), any(LocalDateTime.class),
                eq("online"), eq("HEALTHY"), eq(null));
    }

    @Test
    @DisplayName("heartbeat 成功 (UNHEALTHY + detail): 自检失败信息能传到 DB")
    void heartbeat_Unhealthy() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setWorkerId(1L);
        req.setStatus("online");
        req.setHealth("UNHEALTHY");
        req.setHealthDetail("k8s API timeout 3s");
        when(workerMapper.updateHeartbeatWithHealth(eq(1L), any(LocalDateTime.class),
                eq("online"), eq("UNHEALTHY"), eq("k8s API timeout 3s"))).thenReturn(1);

        workerService.heartbeat(1L, req);

        verify(workerMapper, times(1)).updateHeartbeatWithHealth(
                eq(1L), any(LocalDateTime.class),
                eq("online"), eq("UNHEALTHY"), eq("k8s API timeout 3s"));
    }

    @Test
    @DisplayName("heartbeat: worker 不存在 (deleted) → 抛 NOT_FOUND")
    void heartbeat_WorkerNotFound_ThrowsException() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setStatus("online");
        when(workerMapper.updateHeartbeatWithHealth(eq(99L), any(), any(), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> workerService.heartbeat(99L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("worker 不存在");
    }

    @Test
    @DisplayName("heartbeat: status 非法 → 抛 BAD_REQUEST")
    void heartbeat_InvalidStatus_ThrowsException() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setStatus("yolo");

        assertThatThrownBy(() -> workerService.heartbeat(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status 必须是");

        verify(workerMapper, never()).updateHeartbeatWithHealth(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("heartbeat: health 非法 → 抛 BAD_REQUEST")
    void heartbeat_InvalidHealth_ThrowsException() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setStatus("online");
        req.setHealth("yolo");  // 非法

        assertThatThrownBy(() -> workerService.heartbeat(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("health 必须是");
    }

    @Test
    @DisplayName("heartbeat: body workerId 跟 URL path 不一致 → 抛 BAD_REQUEST")
    void heartbeat_WorkerIdMismatch_ThrowsException() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setWorkerId(99L);  // 跟 URL 的 1L 不一致
        req.setStatus("online");

        assertThatThrownBy(() -> workerService.heartbeat(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    // ==================== get / delete ====================

    @Test
    @DisplayName("get: worker 存在返实体")
    void get_Exists() {
        Worker w = new Worker();
        w.setId(1L);
        w.setWorkerUrl("http://localhost:8888");
        when(workerMapper.selectById(1L)).thenReturn(w);

        Worker result = workerService.get(1L);
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("get: 不存在抛 NOT_FOUND")
    void get_NotExists_ThrowsException() {
        when(workerMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> workerService.get(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("worker 不存在");
    }

    // ==================== 集群读类代理 ====================

    @Test
    @DisplayName("listNamespaces: 调 worker client, 透传 list")
    void listNamespaces_Proxy() {
        Worker w = new Worker();
        w.setId(1L);
        w.setWorkerUrl("http://localhost:8888");
        when(workerMapper.selectById(1L)).thenReturn(w);
        when(workerClient.listNamespaces("http://localhost:8888"))
                .thenReturn(List.of(Map.of("name", "default"), Map.of("name", "kube-system")));

        List<Map<String, Object>> result = workerService.listNamespaces(1L);

        assertThat(result).hasSize(2);
        verify(workerClient, times(1)).listNamespaces("http://localhost:8888");
    }

    @Test
    @DisplayName("listPods: namespace 空 → 抛 BAD_REQUEST, 不调 client")
    void listPods_EmptyNamespace_ThrowsException() {
        assertThatThrownBy(() -> workerService.listPods(1L, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("namespace 不能为空");

        verify(workerClient, never()).listPods(anyString(), anyString());
    }

    @Test
    @DisplayName("listPods: namespace null → 抛 BAD_REQUEST")
    void listPods_NullNamespace_ThrowsException() {
        assertThatThrownBy(() -> workerService.listPods(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("namespace 不能为空");
    }

    @Test
    @DisplayName("listDeployments: 正常代理")
    void listDeployments_Proxy() {
        Worker w = new Worker();
        w.setId(1L);
        w.setWorkerUrl("http://localhost:8888");
        when(workerMapper.selectById(1L)).thenReturn(w);
        when(workerClient.listDeployments(eq("http://localhost:8888"), eq("shipyard")))
                .thenReturn(List.of(Map.of("name", "shipyard-web")));

        List<Map<String, Object>> result = workerService.listDeployments(1L, "shipyard");

        assertThat(result).hasSize(1);
    }

    // ==================== list (分页) ====================

    @Test
    @DisplayName("list: envId 过滤 + 分页")
    void list_WithEnvId() {
        when(workerMapper.selectPage(any(Page.class), any())).thenReturn(new Page<>(1, 20));

        Page<Worker> result = workerService.list(1, 20, 100L);

        assertThat(result).isNotNull();
        verify(workerMapper, times(1)).selectPage(any(Page.class), any());
    }
}

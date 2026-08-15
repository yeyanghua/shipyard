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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.crypto.TokenGenerator;
import com.shipyard.entity.Env;
import com.shipyard.mapper.EnvMapper;
import com.shipyard.worker.client.WorkerClient;
import com.shipyard.worker.dto.WorkerCreateRequest;
import com.shipyard.worker.dto.WorkerHeartbeatRequest;
import com.shipyard.worker.dto.WorkerRegisterRequest;
import com.shipyard.worker.dto.WorkerRegisterResponse;
import com.shipyard.worker.dto.WorkerTokenResponse;
import com.shipyard.worker.dto.WorkerUpdateRequest;
import com.shipyard.worker.entity.Worker;
import com.shipyard.worker.mapper.WorkerMapper;
import com.shipyard.worker.service.impl.WorkerServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * WorkerServiceImpl 单元测试 — Mockito mock 掉 mapper / client, 测 service 业务逻辑.
 *
 * <p>M9.5 redesign: register 改严格模式 (必须先有预登记 row + token 校验).
 * 旧 register 测试逻辑 (无 row 就 insert) 不再适用, 重写.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerService 业务逻辑 (M9.5 redesign)")
class WorkerServiceImplTest {

    @Mock private WorkerMapper workerMapper;
    @Mock private EnvMapper envMapper;
    @Mock private WorkerClient workerClient;

    @InjectMocks private WorkerServiceImpl workerService;

    private WorkerRegisterRequest validReq;
    /** 跟 validReq.getWorkerToken() 哈希后存, 模拟 shipyard 预登记 row. */
    private static final String VALID_TOKEN = "test-token-xyz-12345";

    @BeforeEach
    void setUp() {
        validReq = new WorkerRegisterRequest();
        validReq.setPodName("shipyard-worker-dev-1");
        validReq.setEnv("dev");
        validReq.setWorkerUrl("http://192.168.91.139:30080");
        validReq.setWorkerToken(VALID_TOKEN);
        validReq.setVersion("0.1.0");
    }

    // ==================== create (M9.5 新增) ====================

    @Test
    @DisplayName("create: 成功生成 token, 存 SHA-256 哈希, status=PLANNED, 返明文 token (一次性)")
    void create_Success() {
        Env env = new Env();
        env.setId(100L);
        env.setName("dev");
        env.setDeleted(0);  // mock 必须设 deleted, 否则 service NPE
        when(envMapper.selectById(100L)).thenReturn(env);
        when(workerMapper.selectCount(any())).thenReturn(0L);  // name / podName 都不冲突
        when(workerMapper.insert(any(Worker.class))).thenAnswer(inv -> {
            Worker w = inv.getArgument(0);
            w.setId(1L);
            return 1;
        });

        WorkerCreateRequest req = new WorkerCreateRequest();
        req.setName("shipyard-worker-dev-1");
        req.setPodName("shipyard-worker-dev-1");
        req.setDescription("dev env 的第一个 worker");

        WorkerTokenResponse resp = workerService.create(100L, req, "admin@shipyard.dev");

        // 1. 返明文 token (一次性)
        assertThat(resp.getToken()).isNotNull().hasSizeGreaterThan(20);
        assertThat(resp.getWorkerId()).isEqualTo(1L);
        assertThat(resp.getName()).isEqualTo("shipyard-worker-dev-1");
        assertThat(resp.getNotice()).contains("立即复制");
    }

    @Test
    @DisplayName("create: env 不存在 → 抛 NOT_FOUND")
    void create_EnvNotFound_ThrowsException() {
        when(envMapper.selectById(999L)).thenReturn(null);

        WorkerCreateRequest req = new WorkerCreateRequest();
        req.setName("w1");
        req.setPodName("shipyard-worker-dev-1");

        assertThatThrownBy(() -> workerService.create(999L, req, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("环境不存在");
    }

    @Test
    @DisplayName("create: name 同 env 下重复 → 抛 BAD_REQUEST")
    void create_DuplicateName_ThrowsException() {
        Env env = new Env();
        env.setId(100L);
        env.setDeleted(0);  // mock 必须设
        when(envMapper.selectById(100L)).thenReturn(env);
        when(workerMapper.selectCount(any())).thenReturn(1L);  // name 冲突

        WorkerCreateRequest req = new WorkerCreateRequest();
        req.setName("duplicate");
        req.setPodName("shipyard-worker-dev-1");

        assertThatThrownBy(() -> workerService.create(100L, req, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同名 worker");
    }

    // ==================== register (M9.5 严格模式) ====================

    @Test
    @DisplayName("register 成功: 找到预登记 row + token 匹配 → 状态 PLANNED→PROVISIONING, 返 ID")
    void register_StrictMode_Success() {
        // 预登记 row (status=PLANNED, 已存 token 哈希)
        Worker existing = new Worker();
        existing.setId(42L);
        existing.setEnvId(100L);
        existing.setPodName("shipyard-worker-dev-1");
        existing.setStatus("PLANNED");
        existing.setWorkerTokenHash(TokenGenerator.hash(VALID_TOKEN));

        when(envMapper.selectIdByNameRaw("dev")).thenReturn(100L);
        when(workerMapper.selectOne(any())).thenReturn(existing);

        WorkerRegisterResponse resp = workerService.register(validReq);

        assertThat(resp.getWorkerId()).isEqualTo(42L);
        assertThat(resp.getHeartbeatIntervalSec()).isEqualTo(30);
        // 状态从 PLANNED 切到 PROVISIONING
        assertThat(existing.getStatus()).isEqualTo("PROVISIONING");
        assertThat(existing.getWorkerUrl()).isEqualTo("http://192.168.91.139:30080");
        assertThat(existing.getUpdatedBy()).isEqualTo("system:register");
        verify(workerMapper, never()).insert(any(Worker.class));  // 严格模式不 insert
        verify(workerMapper, times(1)).updateById(any(Worker.class));
    }

    @Test
    @DisplayName("register: env 不存在 → 抛 NOT_FOUND")
    void register_EnvNotFound_ThrowsException() {
        when(envMapper.selectIdByNameRaw("dev")).thenReturn(null);

        assertThatThrownBy(() -> workerService.register(validReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("环境不存在");
    }

    @Test
    @DisplayName("register 严格模式: 找不到预登记 row → 抛 NOT_FOUND, 提示用户先在 UI 创建")
    void register_NoPreRegisteredRow_ThrowsException() {
        when(envMapper.selectIdByNameRaw("dev")).thenReturn(100L);
        when(workerMapper.selectOne(any())).thenReturn(null);  // 严格模式: 找不到

        assertThatThrownBy(() -> workerService.register(validReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先")
                .hasMessageContaining("shipyard-worker-dev-1");

        verify(workerMapper, never()).insert(any(Worker.class));
    }

    @Test
    @DisplayName("register: token 校验失败 → 抛 UNAUTHORIZED")
    void register_InvalidToken_ThrowsException() {
        Worker existing = new Worker();
        existing.setId(42L);
        existing.setEnvId(100L);
        existing.setPodName("shipyard-worker-dev-1");
        existing.setStatus("PLANNED");
        // 存的哈希是另一个 token, 不是 validReq 里的
        existing.setWorkerTokenHash(TokenGenerator.hash("another-token"));

        when(envMapper.selectIdByNameRaw("dev")).thenReturn(100L);
        when(workerMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> workerService.register(validReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("token 校验失败");

        verify(workerMapper, never()).updateById(any(Worker.class));
    }

    @Test
    @DisplayName("register: 已有 row 但状态非 PLANNED (e.g. ONLINE) → 保持状态, 走 update")
    void register_ExistingOnline_KeepStatus() {
        Worker existing = new Worker();
        existing.setId(42L);
        existing.setEnvId(100L);
        existing.setPodName("shipyard-worker-dev-1");
        existing.setStatus("ONLINE");  // 已有 ONLINE, register 保持 ONLINE
        existing.setWorkerTokenHash(TokenGenerator.hash(VALID_TOKEN));

        when(envMapper.selectIdByNameRaw("dev")).thenReturn(100L);
        when(workerMapper.selectOne(any())).thenReturn(existing);

        workerService.register(validReq);

        // 状态保持 ONLINE (register 不改 ONLINE)
        assertThat(existing.getStatus()).isEqualTo("ONLINE");
    }

    // ==================== heartbeat (M9.5 status 大写) ====================

    @Test
    @DisplayName("heartbeat 成功 (HEALTHY): status 大写 ONLINE, 默认 HEALTHY")
    void heartbeat_Success() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setWorkerId(1L);
        req.setStatus("ONLINE");
        when(workerMapper.updateHeartbeatWithHealth(eq(1L), any(LocalDateTime.class),
                eq("ONLINE"), eq("HEALTHY"), eq(null))).thenReturn(1);

        workerService.heartbeat(1L, req);

        verify(workerMapper, times(1)).updateHeartbeatWithHealth(
                eq(1L), any(LocalDateTime.class),
                eq("ONLINE"), eq("HEALTHY"), eq(null));
    }

    @Test
    @DisplayName("heartbeat 成功 (UNHEALTHY + detail): 自检失败信息能传到 DB")
    void heartbeat_Unhealthy() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setWorkerId(1L);
        req.setStatus("ONLINE");
        req.setHealth("UNHEALTHY");
        req.setHealthDetail("k8s API timeout 3s");
        when(workerMapper.updateHeartbeatWithHealth(eq(1L), any(LocalDateTime.class),
                eq("ONLINE"), eq("UNHEALTHY"), eq("k8s API timeout 3s"))).thenReturn(1);

        workerService.heartbeat(1L, req);

        verify(workerMapper, times(1)).updateHeartbeatWithHealth(
                eq(1L), any(LocalDateTime.class),
                eq("ONLINE"), eq("UNHEALTHY"), eq("k8s API timeout 3s"));
    }

    @Test
    @DisplayName("heartbeat: worker 不存在 (deleted) → 抛 NOT_FOUND")
    void heartbeat_WorkerNotFound_ThrowsException() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setStatus("ONLINE");
        when(workerMapper.updateHeartbeatWithHealth(eq(99L), any(), any(), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> workerService.heartbeat(99L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("worker 不存在");
    }

    @Test
    @DisplayName("heartbeat: status 非法 (lowercase) → 抛 BAD_REQUEST (M9.5 改成大写)")
    void heartbeat_LowercaseStatus_ThrowsException() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setStatus("online");  // 旧 lowercase, M9.5 不接受

        assertThatThrownBy(() -> workerService.heartbeat(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("status 必须是");
    }

    @Test
    @DisplayName("heartbeat: health 非法 → 抛 BAD_REQUEST")
    void heartbeat_InvalidHealth_ThrowsException() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setStatus("ONLINE");
        req.setHealth("yolo");

        assertThatThrownBy(() -> workerService.heartbeat(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("health 必须是");
    }

    @Test
    @DisplayName("heartbeat: body workerId 跟 URL path 不一致 → 抛 BAD_REQUEST")
    void heartbeat_WorkerIdMismatch_ThrowsException() {
        WorkerHeartbeatRequest req = new WorkerHeartbeatRequest();
        req.setWorkerId(99L);
        req.setStatus("ONLINE");

        assertThatThrownBy(() -> workerService.heartbeat(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    // ==================== regenerateToken (M9.5 新增) ====================

    @Test
    @DisplayName("regenerateToken: 旧 token 立即失效, 返新 token 明文")
    void regenerateToken_Success() {
        Worker existing = new Worker();
        existing.setId(42L);
        existing.setEnvId(100L);
        existing.setName("w1");
        existing.setWorkerTokenHash(TokenGenerator.hash("old-token"));
        when(workerMapper.selectById(42L)).thenReturn(existing);

        WorkerTokenResponse resp = workerService.regenerateToken(42L, "admin");

        // 1. 返新 token
        assertThat(resp.getToken()).isNotEqualTo("old-token");
        assertThat(resp.getToken()).hasSizeGreaterThan(20);
        // 2. 新 token 通过 verify 校验通过
        assertThat(TokenGenerator.verify(resp.getToken(), existing.getWorkerTokenHash())).isTrue();
        // 3. 旧 token 不再通过
        assertThat(TokenGenerator.verify("old-token", existing.getWorkerTokenHash())).isFalse();
        // 4. updated_by 是当前操作人 (currentUser 优先于 system:regenerate-token)
        assertThat(existing.getUpdatedBy()).isEqualTo("admin");
    }

    // ==================== update (M9.5 新增) ====================

    @Test
    @DisplayName("update: 改 description 成功")
    void update_Description() {
        Worker existing = new Worker();
        existing.setId(42L);
        existing.setName("w1");
        existing.setDescription("old");
        when(workerMapper.selectById(42L)).thenReturn(existing);

        WorkerUpdateRequest req = new WorkerUpdateRequest();
        req.setDescription("new desc");

        Worker result = workerService.update(42L, req, "admin");

        assertThat(result.getDescription()).isEqualTo("new desc");
        assertThat(result.getUpdatedBy()).isEqualTo("admin");
    }

    // ==================== get / delete ====================

    @Test
    @DisplayName("get: worker 存在返实体")
    void get_Exists() {
        Worker w = new Worker();
        w.setId(1L);
        w.setWorkerUrl("http://192.168.91.139:30080");
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

    @Test
    @DisplayName("delete: 软删 (MyBatis-Plus @TableLogic 转 UPDATE deleted=1)")
    void delete_SoftDelete() {
        Worker existing = new Worker();
        existing.setId(1L);
        existing.setEnvId(100L);
        existing.setName("w1");
        when(workerMapper.selectById(1L)).thenReturn(existing);

        workerService.delete(1L);

        verify(workerMapper, times(1)).deleteById(1L);
    }

    // ==================== 集群读类代理 ====================

    @Test
    @DisplayName("listNamespaces: 调 worker client, 透传 list")
    void listNamespaces_Proxy() {
        Worker w = new Worker();
        w.setId(1L);
        w.setWorkerUrl("http://192.168.91.139:30080");
        when(workerMapper.selectById(1L)).thenReturn(w);
        when(workerClient.listNamespaces("http://192.168.91.139:30080"))
                .thenReturn(List.of(Map.of("name", "default"), Map.of("name", "kube-system")));

        List<Map<String, Object>> result = workerService.listNamespaces(1L);

        assertThat(result).hasSize(2);
        verify(workerClient, times(1)).listNamespaces("http://192.168.91.139:30080");
    }

    @Test
    @DisplayName("listNamespaces: workerUrl 没设 (未 register) → 抛 BAD_REQUEST")
    void listNamespaces_NoWorkerUrl_ThrowsException() {
        Worker w = new Worker();
        w.setId(1L);
        w.setWorkerUrl(null);  // 还没 register
        when(workerMapper.selectById(1L)).thenReturn(w);

        assertThatThrownBy(() -> workerService.listNamespaces(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("workerUrl 未知");
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
    @DisplayName("listDeployments: 正常代理")
    void listDeployments_Proxy() {
        Worker w = new Worker();
        w.setId(1L);
        w.setWorkerUrl("http://192.168.91.139:30080");
        when(workerMapper.selectById(1L)).thenReturn(w);
        when(workerClient.listDeployments(eq("http://192.168.91.139:30080"), eq("shipyard")))
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

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

package com.shipyard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shipyard.common.enums.DeployStatus;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.dto.DeployCreateRequest;
import com.shipyard.dto.DeployResponse;
import com.shipyard.entity.BuildRecord;
import com.shipyard.entity.DeployRecord;
import com.shipyard.entity.DeploySnapshot;
import com.shipyard.entity.Env;
import com.shipyard.entity.PipelineTemplate;
import com.shipyard.entity.Project;
import com.shipyard.mapper.BuildRecordMapper;
import com.shipyard.mapper.DeployRecordMapper;
import com.shipyard.mapper.DeploySnapshotMapper;
import com.shipyard.mapper.EnvMapper;
import com.shipyard.mapper.PipelineTemplateMapper;
import com.shipyard.mapper.ProjectMapper;
import com.shipyard.service.DeployTemplateRenderer;
import com.shipyard.service.PipelineTemplateService;
import com.shipyard.worker.entity.Worker;
import com.shipyard.worker.mapper.WorkerMapper;
import com.shipyard.worker.selector.WorkerSelector;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * DeployServiceImpl 单元测试 — mock 所有依赖 (10 个 mapper / service / selector).
 *
 * <p>覆盖:
 * <ul>
 *   <li>createDeploy 完整流程: 校验 project/env/buildRecordId → 选 worker → 渲染 yaml → 写 record + snapshot → 返 DeployResponse</li>
 *   <li>imageTag 走 buildRecordId / imageTag 两种方式</li>
 *   <li>找不到 worker 抛 NOT_FOUND</li>
 *   <li>rollback: 复用原 imageTag, 复用 snapshot 的 deployYaml, 写新 record + snapshot</li>
 *   <li>cancelDeploy: PENDING/RUNNING 能取消, 终态不能</li>
 *   <li>markRunning / markFinished 状态机</li>
 * </ul>
 *
 * <p>注: deployRecordMapper.insert 不会自动设 id (MyBatis-Plus 的特性), 测试用
 * {@code doAnswer} 自己设 id.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DeployServiceImpl — deploy 链路核心")
class DeployServiceImplTest {

    @Mock private DeployRecordMapper deployRecordMapper;
    @Mock private DeploySnapshotMapper deploySnapshotMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private EnvMapper envMapper;
    @Mock private BuildRecordMapper buildRecordMapper;
    @Mock private PipelineTemplateMapper pipelineTemplateMapper;
    @Mock private PipelineTemplateService pipelineTemplateService;
    @Mock private WorkerMapper workerMapper;
    @Mock private WorkerSelector activeWorkerSelector;
    @Mock private DeployTemplateRenderer templateRenderer;

    @InjectMocks
    private DeployServiceImpl deployService;

    private Project project;
    private Env env;
    private PipelineTemplate template;
    private Worker worker;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(1L);
        project.setName("myapp");

        env = new Env();
        env.setId(10L);
        env.setName("dev");

        template = new PipelineTemplate();
        template.setId(100L);
        template.setProjectId(1L);
        template.setVersion(1);
        template.setContainerPort(8080);
        template.setReplicas(3);
        template.setNamespacePattern("shipyard-{env_name}");

        worker = new Worker();
        worker.setId(555L);
        worker.setEnvId(10L);
        worker.setWorkerUrl("http://worker-1:8888");
        worker.setStatus("online");
        worker.setLastHeartbeatAt(LocalDateTime.now());
    }

    // ============================================================
    // createDeploy
    // ============================================================

    @Test
    @DisplayName("createDeploy: 走 buildRecordId 路径, 完整流程")
    void createDeployViaBuildRecordId() {
        BuildRecord build = new BuildRecord();
        build.setId(99L);
        build.setImageTag("nginx:1.27.0");
        build.setStatus("SUCCESS");

        DeployCreateRequest req = new DeployCreateRequest();
        req.setEnvId(10L);
        req.setBuildRecordId(99L);
        req.setTriggeredBy("alice");

        when(projectMapper.selectById(1L)).thenReturn(project);
        when(envMapper.selectById(10L)).thenReturn(env);
        when(buildRecordMapper.selectById(99L)).thenReturn(build);
        when(workerMapper.selectByEnvAndStatus(10L, "online"))
                .thenReturn(List.of(worker));
        when(activeWorkerSelector.select(any())).thenReturn(worker);
        when(pipelineTemplateService.getActive(1L)).thenReturn(template);
        when(templateRenderer.render(any(), any(), any(), any(), any()))
                .thenReturn("apiVersion: apps/v1\nkind: Deployment\n...");
        when(templateRenderer.renderNamespace(any(), any())).thenReturn("shipyard-dev");
        // 自动设 id
        org.mockito.Mockito.doAnswer(invocation -> {
            DeployRecord r = invocation.getArgument(0);
            r.setId(1234L);
            r.setCreatedAt(LocalDateTime.now());
            return 1;
        }).when(deployRecordMapper).insert(any(DeployRecord.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            DeploySnapshot s = invocation.getArgument(0);
            s.setId(5678L);
            s.setCreatedAt(LocalDateTime.now());
            return 1;
        }).when(deploySnapshotMapper).insert(any(DeploySnapshot.class));

        DeployResponse resp = deployService.createDeploy(1L, req);

        assertThat(resp.getId()).isEqualTo(1234L);
        assertThat(resp.getProjectId()).isEqualTo(1L);
        assertThat(resp.getEnvId()).isEqualTo(10L);
        assertThat(resp.getBuildRecordId()).isEqualTo(99L);
        assertThat(resp.getImageTag()).isEqualTo("nginx:1.27.0");
        assertThat(resp.getStatus()).isEqualTo(DeployStatus.PENDING.name());
        assertThat(resp.getTriggeredBy()).isEqualTo("alice");
        // snapshot 写过
        verify(deploySnapshotMapper, times(1)).insert(any(DeploySnapshot.class));
        // currentSnapshotId 回填过
        verify(deployRecordMapper, times(1)).updateCurrentSnapshot(1234L, 5678L);
    }

    @Test
    @DisplayName("createDeploy: 走 imageTag 路径 (跳过 build)")
    void createDeployViaImageTag() {
        DeployCreateRequest req = new DeployCreateRequest();
        req.setEnvId(10L);
        req.setImageTag("myregistry.io/myapp:v1.0.0");

        when(projectMapper.selectById(1L)).thenReturn(project);
        when(envMapper.selectById(10L)).thenReturn(env);
        when(workerMapper.selectByEnvAndStatus(10L, "online"))
                .thenReturn(List.of(worker));
        when(activeWorkerSelector.select(any())).thenReturn(worker);
        when(pipelineTemplateService.getActive(1L)).thenReturn(template);
        when(templateRenderer.render(any(), any(), any(), any(), any()))
                .thenReturn("apiVersion: apps/v1\nkind: Deployment");
        when(templateRenderer.renderNamespace(any(), any())).thenReturn("shipyard-dev");
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, DeployRecord.class).setId(1234L);
            return 1;
        }).when(deployRecordMapper).insert(any(DeployRecord.class));
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, DeploySnapshot.class).setId(5678L);
            return 1;
        }).when(deploySnapshotMapper).insert(any(DeploySnapshot.class));

        DeployResponse resp = deployService.createDeploy(1L, req);

        assertThat(resp.getImageTag()).isEqualTo("myregistry.io/myapp:v1.0.0");
        assertThat(resp.getBuildRecordId()).isNull();
    }

    @Test
    @DisplayName("createDeploy: 找不到 worker 抛 NOT_FOUND")
    void createDeployNoWorker() {
        DeployCreateRequest req = new DeployCreateRequest();
        req.setEnvId(10L);
        req.setImageTag("nginx:1.27.0");

        when(projectMapper.selectById(1L)).thenReturn(project);
        when(envMapper.selectById(10L)).thenReturn(env);
        when(workerMapper.selectByEnvAndStatus(10L, "online")).thenReturn(List.of());

        assertThatThrownBy(() -> deployService.createDeploy(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可用的 worker");
    }

    @Test
    @DisplayName("createDeploy: project 不存在 抛 NOT_FOUND")
    void createDeployProjectNotFound() {
        DeployCreateRequest req = new DeployCreateRequest();
        req.setEnvId(10L);
        req.setImageTag("nginx:1.27.0");

        when(projectMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> deployService.createDeploy(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    @DisplayName("createDeploy: env 不存在 抛 NOT_FOUND")
    void createDeployEnvNotFound() {
        DeployCreateRequest req = new DeployCreateRequest();
        req.setEnvId(99L);
        req.setImageTag("nginx:1.27.0");

        when(projectMapper.selectById(1L)).thenReturn(project);
        when(envMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> deployService.createDeploy(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("环境不存在");
    }

    @Test
    @DisplayName("createDeploy: buildRecordId 指向不存在的 build 抛 NOT_FOUND")
    void createDeployBuildNotFound() {
        DeployCreateRequest req = new DeployCreateRequest();
        req.setEnvId(10L);
        req.setBuildRecordId(999L);

        when(projectMapper.selectById(1L)).thenReturn(project);
        when(envMapper.selectById(10L)).thenReturn(env);
        when(buildRecordMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> deployService.createDeploy(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("build_record 不存在");
    }

    @Test
    @DisplayName("createDeploy: buildRecordId 指向的 build 还没镜像 (status≠SUCCESS) 抛 BAD_REQUEST")
    void createDeployBuildNotReady() {
        BuildRecord build = new BuildRecord();
        build.setId(99L);
        build.setImageTag(null);  // 还没镜像
        build.setStatus("RUNNING");

        DeployCreateRequest req = new DeployCreateRequest();
        req.setEnvId(10L);
        req.setBuildRecordId(99L);

        when(projectMapper.selectById(1L)).thenReturn(project);
        when(envMapper.selectById(10L)).thenReturn(env);
        when(buildRecordMapper.selectById(99L)).thenReturn(build);

        assertThatThrownBy(() -> deployService.createDeploy(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("还没镜像");
    }

    @Test
    @DisplayName("createDeploy: pipeline_template.active 为空 抛 BAD_REQUEST")
    void createDeployNoActivePipeline() {
        DeployCreateRequest req = new DeployCreateRequest();
        req.setEnvId(10L);
        req.setImageTag("nginx:1.27.0");

        when(projectMapper.selectById(1L)).thenReturn(project);
        when(envMapper.selectById(10L)).thenReturn(env);
        when(workerMapper.selectByEnvAndStatus(10L, "online"))
                .thenReturn(List.of(worker));
        when(activeWorkerSelector.select(any())).thenReturn(worker);
        when(pipelineTemplateService.getActive(1L)).thenReturn(null);

        assertThatThrownBy(() -> deployService.createDeploy(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active 的 pipeline_template");
    }

    @Test
    @DisplayName("createDeploy: req.replicas 覆盖 template.replicas")
    void createDeployReplicasOverride() {
        DeployCreateRequest req = new DeployCreateRequest();
        req.setEnvId(10L);
        req.setImageTag("nginx:1.27.0");
        req.setReplicas(7);  // 覆盖

        when(projectMapper.selectById(1L)).thenReturn(project);
        when(envMapper.selectById(10L)).thenReturn(env);
        when(workerMapper.selectByEnvAndStatus(10L, "online"))
                .thenReturn(List.of(worker));
        when(activeWorkerSelector.select(any())).thenReturn(worker);
        when(pipelineTemplateService.getActive(1L)).thenReturn(template);
        when(templateRenderer.render(any(), any(), any(), any(), any()))
                .thenReturn("apiVersion: apps/v1\nkind: Deployment");
        when(templateRenderer.renderNamespace(any(), any())).thenReturn("shipyard-dev");
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, DeployRecord.class).setId(1234L);
            return 1;
        }).when(deployRecordMapper).insert(any(DeployRecord.class));
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, DeploySnapshot.class).setId(5678L);
            return 1;
        }).when(deploySnapshotMapper).insert(any(DeploySnapshot.class));

        // 用 ArgumentCaptor 验证 replicas 已被覆盖
        org.mockito.ArgumentCaptor<DeployRecord> captor =
                org.mockito.ArgumentCaptor.forClass(DeployRecord.class);

        deployService.createDeploy(1L, req);

        verify(deployRecordMapper).insert(captor.capture());
        // 注意: template 被 service 内部改了 replicas=7, 但写入 record 的 imageTag 是
        // 走 templateRenderer.render 时用的 (即 template.replicas=7)
        // 这里不直接验 record 字段, 而是验 templateRenderer.render 调用时 template.replicas 已经是 7
        // 通过 verify template.render 的入参来验证
        org.mockito.ArgumentCaptor<PipelineTemplate> templateCaptor =
                org.mockito.ArgumentCaptor.forClass(PipelineTemplate.class);
        verify(templateRenderer).render(org.mockito.ArgumentMatchers.eq(env),
                templateCaptor.capture(),
                any(), any(), any());
        assertThat(templateCaptor.getValue().getReplicas()).isEqualTo(7);
    }

    // ============================================================
    // rollback
    // ============================================================

    @Test
    @DisplayName("rollback: 复用原 imageTag, 写新 record + snapshot")
    void rollbackBasic() {
        DeployRecord original = new DeployRecord();
        original.setId(100L);
        original.setProjectId(1L);
        original.setEnvId(10L);
        original.setImageTag("nginx:1.27.0");
        original.setNamespace("shipyard-dev");
        original.setStatus(DeployStatus.SUCCESS.name());

        DeploySnapshot snap = new DeploySnapshot();
        snap.setId(50L);
        snap.setDeployRecordId(100L);
        snap.setProjectId(1L);
        snap.setEnvId(10L);
        snap.setDeployYaml("apiVersion: apps/v1\nkind: Deployment\n...");
        snap.setDeployYamlSha256("abc".repeat(22));  // 64 字符

        when(deployRecordMapper.selectById(100L)).thenReturn(original);
        when(deploySnapshotMapper.selectById(50L)).thenReturn(snap);
        when(workerMapper.selectByEnvAndStatus(10L, "online"))
                .thenReturn(List.of(worker));
        when(activeWorkerSelector.select(any())).thenReturn(worker);
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, DeployRecord.class).setId(200L);
            return 1;
        }).when(deployRecordMapper).insert(any(DeployRecord.class));
        org.mockito.Mockito.doAnswer(inv -> {
            inv.getArgument(0, DeploySnapshot.class).setId(60L);
            return 1;
        }).when(deploySnapshotMapper).insert(any(DeploySnapshot.class));

        DeployResponse resp = deployService.rollback(100L, 50L, "bob");

        assertThat(resp.getId()).isEqualTo(200L);
        assertThat(resp.getProjectId()).isEqualTo(1L);
        assertThat(resp.getEnvId()).isEqualTo(10L);
        assertThat(resp.getImageTag()).isEqualTo("nginx:1.27.0");
        assertThat(resp.getBuildRecordId()).isNull();
        assertThat(resp.getStatus()).isEqualTo(DeployStatus.PENDING.name());
        assertThat(resp.getTriggerType()).isEqualTo("ROLLBACK");
        assertThat(resp.getTriggeredBy()).isEqualTo("bob");
        assertThat(resp.getDeployYamlSha256()).isEqualTo("abc".repeat(22));
    }

    @Test
    @DisplayName("rollback: snapshot 不在同一 project/env 抛 BAD_REQUEST")
    void rollbackSnapshotEnvMismatch() {
        DeployRecord original = new DeployRecord();
        original.setId(100L);
        original.setProjectId(1L);
        original.setEnvId(10L);

        DeploySnapshot snap = new DeploySnapshot();
        snap.setId(50L);
        snap.setProjectId(2L);  // 不匹配
        snap.setEnvId(10L);

        when(deployRecordMapper.selectById(100L)).thenReturn(original);
        when(deploySnapshotMapper.selectById(50L)).thenReturn(snap);

        assertThatThrownBy(() -> deployService.rollback(100L, 50L, "bob"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在同一 project/env");
    }

    // ============================================================
    // cancelDeploy
    // ============================================================

    @Test
    @DisplayName("cancelDeploy: PENDING 状态能取消")
    void cancelPending() {
        DeployRecord r = new DeployRecord();
        r.setId(100L);
        r.setStatus(DeployStatus.PENDING.name());

        // selectById 第一次返 PENDING, markFinished 后返 CANCELED
        when(deployRecordMapper.selectById(100L))
                .thenReturn(r)  // 第一次 selectById (检查状态)
                .thenReturn(updatedRecord());  // 第二次 selectById (拉最新)

        DeployResponse resp = deployService.cancelDeploy(100L);

        assertThat(resp.getStatus()).isEqualTo(DeployStatus.CANCELED.name());
        verify(deployRecordMapper, times(1))
                .markFinished(anyLong(), eq(DeployStatus.CANCELED.name()), any(), any());
    }

    private DeployRecord updatedRecord() {
        DeployRecord r = new DeployRecord();
        r.setId(100L);
        r.setStatus(DeployStatus.CANCELED.name());
        r.setFinishedAt(LocalDateTime.now());
        return r;
    }

    @Test
    @DisplayName("cancelDeploy: SUCCESS 终态不能取消, 抛 BAD_REQUEST")
    void cancelAlreadyTerminal() {
        DeployRecord r = new DeployRecord();
        r.setId(100L);
        r.setStatus(DeployStatus.SUCCESS.name());

        when(deployRecordMapper.selectById(100L)).thenReturn(r);

        assertThatThrownBy(() -> deployService.cancelDeploy(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("终态");
    }

    // ============================================================
    // 状态机
    // ============================================================

    @Test
    @DisplayName("markRunning: 调 mapper.markRunning(id, now)")
    void markRunning() {
        when(deployRecordMapper.markRunning(anyLong(), any())).thenReturn(1);
        deployService.markRunning(123L);
        verify(deployRecordMapper, times(1)).markRunning(org.mockito.ArgumentMatchers.eq(123L), any());
    }

    @Test
    @DisplayName("markFinished(SUCCESS): 调 mapper.markFinished(SUCCESS)")
    void markFinishedSuccess() {
        when(deployRecordMapper.markFinished(anyLong(), any(), any(), any())).thenReturn(1);
        deployService.markFinished(123L, "SUCCESS", null);
        verify(deployRecordMapper, times(1))
                .markFinished(org.mockito.ArgumentMatchers.eq(123L),
                        eq("SUCCESS"), eq(null), any());
    }

    @Test
    @DisplayName("markFinished(无效 status): log + no-op, 不抛")
    void markFinishedInvalidStatus() {
        deployService.markFinished(123L, "GARBAGE", "err");
        // 不调 mapper
        verify(deployRecordMapper, times(0)).markFinished(anyLong(), any(), any(), any());
    }

    // ============================================================
    // helper: computeResourceName
    // ============================================================

    @Test
    @DisplayName("computeResourceName: project-env 小写")
    void computeResourceName() {
        assertThat(deployService.computeResourceName("MyApp", "DEV"))
                .isEqualTo("myapp-dev");
        assertThat(deployService.computeResourceName("a", "b")).isEqualTo("a-b");
    }
}

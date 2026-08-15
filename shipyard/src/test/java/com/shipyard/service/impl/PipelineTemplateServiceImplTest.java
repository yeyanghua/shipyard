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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shipyard.common.enums.ReviewStatus;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.entity.PipelineTemplate;
import com.shipyard.entity.Project;
import com.shipyard.mapper.PipelineTemplateMapper;
import com.shipyard.service.ProjectService;
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
 * PipelineTemplateServiceImpl 单元测试 — 覆盖核心业务规则:
 * <ul>
 *   <li>版本自增 (MAX+1)</li>
 *   <li>approved immutable</li>
 *   <li>active 唯一约束 (一项目同时只有一个 active)</li>
 *   <li>active 必须 approved</li>
 *   <li>删除约束 (approved + active 不能删)</li>
 *   <li>projectId 存在性校验</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineTemplateServiceImplTest {

    @Mock
    private PipelineTemplateMapper pipelineTemplateMapper;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private PipelineTemplateServiceImpl service;

    private static final Long PROJECT_ID = 100L;
    private static final String YAML = "kind: pipeline\nname: test\nsteps: []\n";

    @BeforeEach
    void setUp() {
        // 通用: projectService.get(PROJECT_ID) 默认成功 (个别测试覆盖)
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setName("test-project");
        when(projectService.get(PROJECT_ID)).thenReturn(project);
    }

    // ==================== create ====================

    @Test
    @DisplayName("create 成功: 首个版本 version=1, draft, isActive=0, createdBy 正确填")
    void create_firstVersion_success() {
        when(pipelineTemplateMapper.selectMaxVersion(PROJECT_ID)).thenReturn(0);

        PipelineTemplate input = new PipelineTemplate();
        input.setProjectId(PROJECT_ID);
        input.setYamlContent(YAML);

        PipelineTemplate result = service.create(input, "alice");

        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getReviewStatus()).isEqualTo(ReviewStatus.DRAFT.name());
        assertThat(result.getIsActive()).isEqualTo(0);
        assertThat(result.getCreatedBy()).isEqualTo("alice");
        assertThat(result.getYamlContent()).isEqualTo(YAML);
        verify(pipelineTemplateMapper, times(1)).insert(any(PipelineTemplate.class));
    }

    @Test
    @DisplayName("create 成功: 已有 3 个版本, 新版本 version=4")
    void create_nextVersion_incrementsMaxPlusOne() {
        when(pipelineTemplateMapper.selectMaxVersion(PROJECT_ID)).thenReturn(3);

        PipelineTemplate input = new PipelineTemplate();
        input.setProjectId(PROJECT_ID);
        input.setYamlContent(YAML);

        PipelineTemplate result = service.create(input, "bob");

        assertThat(result.getVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("create 成功: AI 生成的版本, aiModifiedBy / aiPrompt 正确填")
    void create_aiGenerated_fieldsPersisted() {
        when(pipelineTemplateMapper.selectMaxVersion(PROJECT_ID)).thenReturn(0);

        PipelineTemplate input = new PipelineTemplate();
        input.setProjectId(PROJECT_ID);
        input.setYamlContent(YAML);
        input.setAiModifiedBy("mock/v1");
        input.setAiPrompt("为 Java Maven 项目生成标准 pipeline");

        PipelineTemplate result = service.create(input, "ai-bot");

        assertThat(result.getAiModifiedBy()).isEqualTo("mock/v1");
        assertThat(result.getAiPrompt()).isEqualTo("为 Java Maven 项目生成标准 pipeline");
        assertThat(result.getCreatedBy()).isEqualTo("ai-bot");
    }

    @Test
    @DisplayName("create 失败: yamlContent 为空抛 BAD_REQUEST")
    void create_emptyYaml_throwsBadRequest() {
        PipelineTemplate input = new PipelineTemplate();
        input.setProjectId(PROJECT_ID);
        input.setYamlContent("");

        assertThatThrownBy(() -> service.create(input, "alice"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("yamlContent 不能为空");

        verify(pipelineTemplateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create 失败: projectId 不存在抛 NOT_FOUND (复用 ProjectService.get)")
    void create_projectNotFound_throwsNotFound() {
        when(projectService.get(999L)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "项目不存在: id=999"));

        PipelineTemplate input = new PipelineTemplate();
        input.setProjectId(999L);
        input.setYamlContent(YAML);

        assertThatThrownBy(() -> service.create(input, "alice"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(pipelineTemplateMapper, never()).insert(any());
    }

    @Test
    @DisplayName("create 成功: createdBy 为 null 时默认 'unknown'")
    void create_nullCreatedBy_defaultsToUnknown() {
        when(pipelineTemplateMapper.selectMaxVersion(PROJECT_ID)).thenReturn(0);

        PipelineTemplate input = new PipelineTemplate();
        input.setProjectId(PROJECT_ID);
        input.setYamlContent(YAML);

        PipelineTemplate result = service.create(input, null);

        assertThat(result.getCreatedBy()).isEqualTo("unknown");
    }

    // ==================== update ====================

    @Test
    @DisplayName("update 成功: draft 状态可以改 yamlContent")
    void update_draft_canChangeYaml() {
        PipelineTemplate existing = templateWithId(10L, 2, ReviewStatus.DRAFT, 0);
        when(pipelineTemplateMapper.selectById(10L)).thenReturn(existing);

        PipelineTemplate patch = new PipelineTemplate();
        patch.setYamlContent("kind: pipeline\nname: v2\n");

        PipelineTemplate result = service.update(10L, patch);

        assertThat(result.getYamlContent()).isEqualTo("kind: pipeline\nname: v2\n");
        verify(pipelineTemplateMapper, times(1)).updateById(existing);
    }

    @Test
    @DisplayName("update 失败: approved 状态 immutable, 抛 RESOURCE_CONFLICT")
    void update_approved_throwsConflict() {
        PipelineTemplate existing = templateWithId(10L, 2, ReviewStatus.APPROVED, 1);
        when(pipelineTemplateMapper.selectById(10L)).thenReturn(existing);

        PipelineTemplate patch = new PipelineTemplate();
        patch.setYamlContent("new content");

        assertThatThrownBy(() -> service.update(10L, patch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已审批的 Pipeline 不可修改");

        verify(pipelineTemplateMapper, never()).updateById(any());
    }

    // ==================== approve / reject ====================

    @Test
    @DisplayName("approve 成功: draft → approved")
    void approve_draft_succeeds() {
        PipelineTemplate existing = templateWithId(20L, 1, ReviewStatus.DRAFT, 0);
        when(pipelineTemplateMapper.selectById(20L)).thenReturn(existing);

        PipelineTemplate result = service.approve(20L);

        assertThat(result.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED.name());
    }

    @Test
    @DisplayName("approve 失败: 非 draft 状态抛 BAD_REQUEST")
    void approve_approved_throwsBadRequest() {
        PipelineTemplate existing = templateWithId(20L, 1, ReviewStatus.APPROVED, 0);
        when(pipelineTemplateMapper.selectById(20L)).thenReturn(existing);

        assertThatThrownBy(() -> service.approve(20L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能审批 DRAFT 状态");
    }

    @Test
    @DisplayName("reject 成功: draft → rejected")
    void reject_draft_succeeds() {
        PipelineTemplate existing = templateWithId(30L, 1, ReviewStatus.DRAFT, 0);
        when(pipelineTemplateMapper.selectById(30L)).thenReturn(existing);

        PipelineTemplate result = service.reject(30L);

        assertThat(result.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED.name());
    }

    @Test
    @DisplayName("reject 失败: rejected 状态不能再 reject")
    void reject_rejected_throwsBadRequest() {
        PipelineTemplate existing = templateWithId(30L, 1, ReviewStatus.REJECTED, 0);
        when(pipelineTemplateMapper.selectById(30L)).thenReturn(existing);

        assertThatThrownBy(() -> service.reject(30L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能驳回 DRAFT 状态");
    }

    // ==================== activate ====================

    @Test
    @DisplayName("activate 成功: approved 版本激活, 同 project 其他 active 被 unactivate")
    void activate_approved_unactivatesOthers() {
        PipelineTemplate target = templateWithId(40L, 3, ReviewStatus.APPROVED, 0);
        when(pipelineTemplateMapper.selectById(40L)).thenReturn(target);
        when(pipelineTemplateMapper.unactivateOthersByProjectId(PROJECT_ID, 40L))
                .thenReturn(1);

        PipelineTemplate result = service.activate(40L);

        // 1) unactivateOthers 必须被调用
        verify(pipelineTemplateMapper, times(1)).unactivateOthersByProjectId(PROJECT_ID, 40L);
        // 2) 目标版本 isActive 置 1
        verify(pipelineTemplateMapper, times(1)).updateById(target);
        assertThat(result.getIsActive()).isEqualTo(1);
    }

    @Test
    @DisplayName("activate 失败: draft 版本不能激活, 抛 BAD_REQUEST")
    void activate_draft_throwsBadRequest() {
        PipelineTemplate existing = templateWithId(40L, 1, ReviewStatus.DRAFT, 0);
        when(pipelineTemplateMapper.selectById(40L)).thenReturn(existing);

        assertThatThrownBy(() -> service.activate(40L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能激活 APPROVED 状态");

        verify(pipelineTemplateMapper, never()).unactivateOthersByProjectId(anyLong(), anyLong());
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete 成功: draft 版本可以删")
    void delete_draft_succeeds() {
        PipelineTemplate existing = templateWithId(50L, 1, ReviewStatus.DRAFT, 0);
        when(pipelineTemplateMapper.selectById(50L)).thenReturn(existing);

        service.delete(50L);

        verify(pipelineTemplateMapper, times(1)).deleteByIdForce(50L);
    }

    @Test
    @DisplayName("delete 成功: approved + 非 active 可以删 (主动废弃)")
    void delete_approvedButNotActive_succeeds() {
        PipelineTemplate existing = templateWithId(50L, 2, ReviewStatus.APPROVED, 0);
        when(pipelineTemplateMapper.selectById(50L)).thenReturn(existing);

        service.delete(50L);

        verify(pipelineTemplateMapper, times(1)).deleteByIdForce(50L);
    }

    @Test
    @DisplayName("delete 失败: approved + active 不能删, 抛 RESOURCE_CONFLICT")
    void delete_approvedActive_throwsConflict() {
        PipelineTemplate existing = templateWithId(50L, 2, ReviewStatus.APPROVED, 1);
        when(pipelineTemplateMapper.selectById(50L)).thenReturn(existing);

        assertThatThrownBy(() -> service.delete(50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已审批且 active 的 Pipeline 不能删除");

        verify(pipelineTemplateMapper, never()).deleteByIdForce(anyLong());
    }

    @Test
    @DisplayName("forceDelete 成功: 跳过业务约束, 物理删 approved+active")
    void forceDelete_approvedActive_succeeds() {
        when(pipelineTemplateMapper.deleteByIdForce(50L)).thenReturn(1);

        service.forceDelete(50L);

        verify(pipelineTemplateMapper, times(1)).deleteByIdForce(50L);
    }

    @Test
    @DisplayName("forceDelete 失败: 不存在抛 NOT_FOUND (affected=0)")
    void forceDelete_notExists_throwsNotFound() {
        when(pipelineTemplateMapper.deleteByIdForce(999L)).thenReturn(0);

        assertThatThrownBy(() -> service.forceDelete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ==================== get / listByProject / getActive ====================

    @Test
    @DisplayName("get 不存在时抛 NOT_FOUND")
    void get_notFound_throwsNotFound() {
        when(pipelineTemplateMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.get(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("listByProject: projectId null 抛 BAD_REQUEST")
    void listByProject_nullProjectId_throwsBadRequest() {
        assertThatThrownBy(() -> service.listByProject(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("projectId 不能为空");
    }

    @Test
    @DisplayName("listByProject 正常: 透传 mapper 结果")
    void listByProject_returnsMapperResult() {
        PipelineTemplate t1 = templateWithId(1L, 1, ReviewStatus.APPROVED, 1);
        PipelineTemplate t2 = templateWithId(2L, 2, ReviewStatus.DRAFT, 0);
        when(pipelineTemplateMapper.selectVersionsByProjectId(PROJECT_ID)).thenReturn(List.of(t1, t2));

        List<PipelineTemplate> result = service.listByProject(PROJECT_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getVersion()).isEqualTo(1);
        assertThat(result.get(1).getVersion()).isEqualTo(2);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造指定状态的 PipelineTemplate.
     */
    private PipelineTemplate templateWithId(Long id, int version, ReviewStatus status, int isActive) {
        PipelineTemplate t = new PipelineTemplate();
        t.setId(id);
        t.setProjectId(PROJECT_ID);
        t.setVersion(version);
        t.setYamlContent(YAML);
        t.setReviewStatus(status.name());
        t.setIsActive(isActive);
        t.setCreatedBy("alice");
        return t;
    }
}

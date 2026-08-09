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

package com.shipyard.controller;

import com.shipyard.ai.LlmService;
import com.shipyard.ai.handler.AiRequestContext;
import com.shipyard.common.enums.AiCapability;
import com.shipyard.common.enums.LlmProvider;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.dto.ApiResponse;
import com.shipyard.dto.PipelineCreateRequest;
import com.shipyard.dto.PipelineResponse;
import com.shipyard.dto.PipelineUpdateRequest;
import com.shipyard.entity.PipelineTemplate;
import com.shipyard.entity.Project;
import com.shipyard.service.PipelineTemplateService;
import com.shipyard.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Pipeline Controller — 流水线模板版本管理 API.
 *
 * <p>7 个端点:
 * <ul>
 *   <li>{@code GET    /api/projects/{projectId}/pipeline}                  查项目当前 active 版本 (可能为 null)</li>
 *   <li>{@code GET    /api/projects/{projectId}/pipeline/versions}         列项目所有版本 (按 version DESC)</li>
 *   <li>{@code POST   /api/projects/{projectId}/pipeline}                  创建新版本 (支持 AI 生成)</li>
 *   <li>{@code PUT    /api/projects/{projectId}/pipeline/{versionId}}      更新 draft/rejected 版本</li>
 *   <li>{@code POST   /api/projects/{projectId}/pipeline/{versionId}/approve}  审批通过 (draft → approved)</li>
 *   <li>{@code POST   /api/projects/{projectId}/pipeline/{versionId}/reject}   驳回 (draft → rejected, 终态)</li>
 *   <li>{@code POST   /api/projects/{projectId}/pipeline/{versionId}/activate} 激活 (approved → active, unactivate 同 project 其他)</li>
 * </ul>
 *
 * <p>AI 集成 (M6 2): POST 创建时如果 {@code aiGenerate=true}, 调 LlmService 生成 yamlContent,
 * LlmService 自动落痕 ai_interaction 表 (含 prompt 脱敏 + response 完整存).
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineTemplateService pipelineTemplateService;
    private final ProjectService projectService;
    private final LlmService llmService;

    @GetMapping
    public ApiResponse<PipelineResponse> getActive(@PathVariable Long projectId) {
        PipelineTemplate active = pipelineTemplateService.getActive(projectId);
        // active 可能为 null (项目还没建过 pipeline), 返 ApiResponse.ok(null) 走 200 + data=null
        return ApiResponse.ok(PipelineResponse.from(active));
    }

    @GetMapping("/versions")
    public ApiResponse<List<PipelineResponse>> listVersions(
        @PathVariable Long projectId,
        @RequestParam(defaultValue = "false") boolean includeDeleted
    ) {
        // V1 demo 简化: 默认只返 deleted=0, E2E 清理阶段加 ?includeDeleted=true 看软删行
        List<PipelineTemplate> versions = includeDeleted
            ? pipelineTemplateService.listByProjectIncludeDeleted(projectId)
            : pipelineTemplateService.listByProject(projectId);
        List<PipelineResponse> resp = versions.stream()
            .map(PipelineResponse::from)
            .toList();
        return ApiResponse.ok(resp);
    }

    @PostMapping
    public ApiResponse<PipelineResponse> create(
        @PathVariable Long projectId,
        @RequestBody @Valid PipelineCreateRequest req
    ) {
        // 校验 project 存在
        Project project = projectService.get(projectId);

        PipelineTemplate template = new PipelineTemplate();
        template.setProjectId(projectId);

        if (Boolean.TRUE.equals(req.getAiGenerate())) {
            // AI 生成路径
            String aiYaml = generateWithAi(project, req.getAiPrompt(), req.getAiProvider());
            template.setYamlContent(aiYaml);
            template.setAiModifiedBy(formatAiModifiedBy(req.getAiProvider()));
            template.setAiPrompt(req.getAiPrompt());
        } else {
            // 用户手动
            if (req.getYamlContent() == null || req.getYamlContent().isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "aiGenerate=false 时 yamlContent 必填");
            }
            template.setYamlContent(req.getYamlContent());
        }

        // 业务上: 创建人 V1 写 "demo-user", V1.5 从 JWT 取
        PipelineTemplate created = pipelineTemplateService.create(template, "demo-user");
        return ApiResponse.ok(PipelineResponse.from(created));
    }

    @PutMapping("/{versionId}")
    public ApiResponse<PipelineResponse> update(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        @RequestBody @Valid PipelineUpdateRequest req
    ) {
        // projectId 在 path 里 — 校验它跟 versionId 对应的 pipeline 一致
        PipelineTemplate existing = pipelineTemplateService.get(versionId);
        if (!existing.getProjectId().equals(projectId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "pipeline " + versionId + " 不属于 project " + projectId);
        }

        PipelineTemplate patch = new PipelineTemplate();
        if (req.getYamlContent() != null) {
            patch.setYamlContent(req.getYamlContent());
        }
        if (req.getAiPrompt() != null) {
            patch.setAiPrompt(req.getAiPrompt());
        }
        if (Boolean.TRUE.equals(req.getAiModified())) {
            patch.setAiModifiedBy("user-marked");
        }

        PipelineTemplate updated = pipelineTemplateService.update(versionId, patch);
        return ApiResponse.ok(PipelineResponse.from(updated));
    }

    @PostMapping("/{versionId}/approve")
    public ApiResponse<PipelineResponse> approve(
        @PathVariable Long projectId,
        @PathVariable Long versionId
    ) {
        assertBelongsToProject(projectId, versionId);
        return ApiResponse.ok(PipelineResponse.from(pipelineTemplateService.approve(versionId)));
    }

    @PostMapping("/{versionId}/reject")
    public ApiResponse<PipelineResponse> reject(
        @PathVariable Long projectId,
        @PathVariable Long versionId
    ) {
        assertBelongsToProject(projectId, versionId);
        return ApiResponse.ok(PipelineResponse.from(pipelineTemplateService.reject(versionId)));
    }

    @PostMapping("/{versionId}/activate")
    public ApiResponse<PipelineResponse> activate(
        @PathVariable Long projectId,
        @PathVariable Long versionId
    ) {
        assertBelongsToProject(projectId, versionId);
        return ApiResponse.ok(PipelineResponse.from(pipelineTemplateService.activate(versionId)));
    }

    @DeleteMapping("/{versionId}")
    public ApiResponse<Void> delete(
        @PathVariable Long projectId,
        @PathVariable Long versionId,
        @RequestParam(defaultValue = "false") boolean force
    ) {
        // force=true 路径: 跳过 assertBelongsToProject (因为 row 可能 deleted=1 找不到了)
        if (!force) {
            assertBelongsToProject(projectId, versionId);
            pipelineTemplateService.delete(versionId);
        } else {
            // V1 demo: E2E 重跑 / 紧急清理场景, 跳过业务约束
            pipelineTemplateService.forceDelete(versionId);
        }
        return ApiResponse.ok();
    }

    // ==================== 私有辅助 ====================

    /**
     * 校验 pipeline 属于指定 project — 防止跨 project 操作.
     */
    private void assertBelongsToProject(Long projectId, Long versionId) {
        PipelineTemplate t = pipelineTemplateService.get(versionId);
        if (!t.getProjectId().equals(projectId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "pipeline " + versionId + " 不属于 project " + projectId);
        }
    }

    /**
     * 调 LlmService 生成 pipeline YAML.
     *
     * <p>流程:
     * <ol>
     *   <li>构造 AiRequestContext (project + extras)</li>
     *   <li>单次覆盖 LlmProvider (如果 request 指定了 aiProvider)</li>
     *   <li>调 LlmService.call(PIPELINE_GEN, ctx) 拿 String YAML</li>
     * </ol>
     */
    private String generateWithAi(Project project, String aiPrompt, String aiProvider) {
        AiRequestContext.Builder ctxBuilder = AiRequestContext.builder()
            .userId("demo-user")
            .project(project)
            .put("projectType", project.getProjectType());

        // 把 user 加的 aiPrompt 拼进 context (PipelineGenHandler 可以读)
        if (aiPrompt != null && !aiPrompt.isBlank()) {
            ctxBuilder.put("userHint", aiPrompt);
        }

        AiRequestContext ctx = ctxBuilder.build();

        // 单次 provider 覆盖: 用 AiRequestContext + 直接调 PipelineGenHandler
        // 简化: LlmService 不直接支持 provider override, 我们走一个临时方案 —
        //   PipelineGenHandler.buildRequest 拼好 prompt, 然后调 LlmAdapter
        // V1 简化: 走默认 provider, aiProvider 字段在 PROGRESS.md 留 TODO V1.5 加
        // TODO V1.5: LlmService.callWithProvider(capability, provider, ctx)
        log.info("[PipelineController] AI 生成 pipeline project={} provider={} hint={}",
            project.getId(), aiProvider, aiPrompt);
        return llmService.call(AiCapability.PIPELINE_GEN, ctx);
    }

    /**
     * 把 aiProvider 字符串格式化成 ai_modified_by 字段.
     * 例: "mock" → "mock/v1" (model 走 default), "tongyi" → "tongyi/qwen-turbo".
     *
     * <p>简化: V1 只填 provider 名字, model 后填.
     */
    private String formatAiModifiedBy(String aiProvider) {
        if (aiProvider == null || aiProvider.isBlank()) {
            return "mock/v1";
        }
        try {
            LlmProvider p = LlmProvider.fromValue(aiProvider);
            return p.getValue() + "/(model-todo)";
        } catch (IllegalArgumentException e) {
            return aiProvider + "/(unknown)";
        }
    }
}

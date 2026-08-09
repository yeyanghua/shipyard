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

package com.shipyard.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * AI 对话留痕 — shipyard 调用 LLM 的所有记录都进这表.
 *
 * <p>对应 V1__init.sql 的 {@code ai_interaction} 表 (M2 已落库).
 *
 * <p><b>不可变流水表</b> — 不继承 {@link BaseEntity}, 没有 {@code updated_at} 也没有 {@code deleted}.
 * 业务上一旦写入永不修改永不删除 (审计要求, 防止赖账), 跟 {@code build_log} 一个性质.
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code capability} — {@link com.shipyard.common.enums.AiCapability} 字符串值</li>
 *   <li>{@code llmProvider} — {@link com.shipyard.common.enums.LlmProvider} 字符串值</li>
 *   <li>{@code llmRequest} / {@code llmResponse} — JSON 字符串, 业务层 parse; 脱敏后再写</li>
 *   <li>{@code outputAction} — 业务动作描述, 例 {@code "改了 pipeline 模板 ID 5 → 6"}</li>
 * </ul>
 *
 * <p>查询索引 (V1__init.sql):
 * <ul>
 *   <li>{@code idx_ai_user} (user_id, created_at DESC) — 按用户查历史</li>
 *   <li>{@code idx_ai_capability} (capability) — 按能力查</li>
 *   <li>{@code idx_ai_provider} (llm_provider) — 按 provider 查</li>
 * </ul>
 */
@Data
@TableName("ai_interaction")
public class AiInteraction {

    /** 主键 (雪花 ID) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户 ID — 当前 V1 写死 {@code "demo-user"}, V1.5 改从 JWT 取.
     */
    @TableField("user_id")
    private String userId;

    /**
     * 能力 — {@link com.shipyard.common.enums.AiCapability} 字符串值
     * (例 {@code "pipeline_gen"} / {@code "diagnosis"} / {@code "decision"}).
     */
    @TableField("capability")
    private String capability;

    /**
     * 输入 prompt — 业务侧组装好的完整 prompt (含项目元数据/构建日志片段等).
     *
     * <p>注意: <b>不能含明文 secret</b>! 写表前必须脱敏 (env var 走 "***" 占位).
     */
    @TableField("input_prompt")
    private String inputPrompt;

    /**
     * LLM provider — {@link com.shipyard.common.enums.LlmProvider} 字符串值
     * (例 {@code "mock"} / {@code "tongyi"} / {@code "deepseek"}).
     */
    @TableField("llm_provider")
    private String llmProvider;

    /**
     * LLM 模型名 — 例 {@code "qwen-turbo"} / {@code "deepseek-chat"} / {@code "mock-v1"}.
     * NULL 表示 mock provider (不区分模型).
     */
    @TableField("llm_model")
    private String llmModel;

    /**
     * LLM 请求 JSON — 脱敏后的完整请求体 (provider 协议的 format).
     * 存 String, 业务层 parse; V1.5 改 JacksonTypeHandler.
     */
    @TableField("llm_request")
    private String llmRequest;

    /**
     * LLM 响应 JSON — 完整响应体 (含 finish_reason / usage 等元信息).
     */
    @TableField("llm_response")
    private String llmResponse;

    /**
     * 输出动作描述 — 自然语言, 例:
     * <ul>
     *   <li>{@code "为 project 5 创建 pipeline v3 (draft)"}</li>
     *   <li>{@code "诊断 build 12 失败: 根因是 test 步骤 assert 失败, 建议..."}</li>
     *   <li>{@code "决策 build 12: 建议手动发布 (p50 耗时超阈值)"}</li>
     * </ul>
     */
    @TableField("output_action")
    private String outputAction;

    /**
     * 创建时间 — INSERT 自动填.
     *
     * <p>不可变, 业务代码不要 set.
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

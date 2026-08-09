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

package com.shipyard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * drone webhook payload — V1.5 接真实 drone 时, drone 端推过来的 JSON 格式.
 *
 * <p>V1 mock 阶段 shipyard 内部 {@code MockDroneClient} 不走 webhook, 但 webhook
 * 端点 {@code POST /webhook/drone} 仍然暴露, 用于:
 * <ol>
 *   <li>HMAC 验签端到端验证 (E2E test 模拟 drone 签名发请求)</li>
 *   <li>V1.5 接真实 drone 时, 业务逻辑零改动</li>
 * </ol>
 *
 * <p>字段说明 (跟 drone 1.x webhook 格式对齐子集):
 * <ul>
 *   <li>{@code event} — {@code "build_started"} / {@code "step_finished"} / {@code "build_finished"}</li>
 *   <li>{@code droneBuildId} — shipyard 生成的 ID, 跟 build_record.drone_build_id 关联</li>
 *   <li>{@code status} — 仅 {@code build_finished} 事件需要 (SUCCESS / FAILED / TIMEOUT / CANCELED)</li>
 *   <li>{@code stepName} / {@code stepOrder} / {@code logContent} — 仅 {@code step_finished} 需要</li>
 *   <li>{@code imageTag} / {@code harborImageUrl} — 仅 SUCCESS 的 {@code build_finished} 需要</li>
 * </ul>
 *
 * <p>V1 demo 阶段只测 {@code build_finished} 一类, 其他类型 V1.5 接真实 drone 一起测.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DroneWebhookPayload {

    /** 事件类型 */
    private String event;

    /** drone 端 build id (shipyard 传的, 这里 echo 回来) */
    private String droneBuildId;

    /** 终态 status — {@code build_finished} 事件需要 */
    private String status;

    /** step 名 — {@code step_finished} 需要 */
    private String stepName;

    /** step 顺序 — {@code step_finished} 需要 */
    private Integer stepOrder;

    /** step 完整日志 — {@code step_finished} 需要 */
    private String logContent;

    /** step 开始时间 — {@code step_finished} 需要 */
    private LocalDateTime stepStartedAt;

    /** step 结束时间 — {@code step_finished} 需要 */
    private LocalDateTime stepFinishedAt;

    /** 镜像 tag — 仅 SUCCESS 终态 */
    private String imageTag;

    /** Harbor URL — 仅 SUCCESS 终态 */
    private String harborImageUrl;
}

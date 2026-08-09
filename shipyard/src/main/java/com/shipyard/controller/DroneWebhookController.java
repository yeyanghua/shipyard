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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.config.DroneProperties;
import com.shipyard.crypto.HmacVerifier;
import com.shipyard.dto.ApiResponse;
import com.shipyard.dto.DroneWebhookPayload;
import com.shipyard.entity.BuildRecord;
import com.shipyard.mapper.BuildRecordMapper;
import com.shipyard.service.BuildService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * drone webhook 接收端 — V1.5 真实 drone 接入用.
 *
 * <p>端点: {@code POST /webhook/drone}
 * <br>白名单: 已在 {@code application.yml} 配 (V1 demo 模式走 permitAll, 仍验签防误推).
 *
 * <p>流程:
 * <ol>
 *   <li>读 raw body (验签需要原文, 不能反序列化后重序列化, 字段顺序会变)</li>
 *   <li>读 {@code X-Drone-Signature} header</li>
 *   <li>{@link HmacVerifier#verify(String, String)} 验签 — 失败返 401</li>
 *   <li>反序列化 payload + dispatch 到 {@link BuildService} 业务方法</li>
 * </ol>
 *
 * <p>V1 mock 阶段 shipyard 内部 {@code MockDroneClient} 不走这个端点 (直接调 Service),
 * 但端点仍暴露, 用于 E2E 测试模拟 drone 推送.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DroneWebhookController {

    private final DroneProperties droneProperties;
    private final ObjectMapper objectMapper;
    private final BuildRecordMapper buildRecordMapper;
    private final BuildService buildService;

    /**
     * drone webhook 接收 — 处理 3 类事件:
     * <ul>
     *   <li>{@code step_finished} — 落 {@code build_log}</li>
     *   <li>{@code build_finished} — 标终态 + image_tag + harbor_url</li>
     * </ul>
     *
     * <p>返回 200 = drone 已收到, shipyard 不会重推; 返非 200 = drone 会重试.
     */
    @PostMapping("/webhook/drone")
    public ApiResponse<Void> handleDroneWebhook(
        HttpServletRequest request,
        @RequestBody String rawBody  // 拿原文, 验签需要 byte-perfect
    ) {
        // 1. 验签
        String signature = request.getHeader("X-Drone-Signature");
        HmacVerifier verifier = new HmacVerifier(droneProperties.getWebhookSecret());
        if (!verifier.verify(rawBody, signature)) {
            log.warn("[DroneWebhook] HMAC verify failed, sig={} body-len={}",
                signature == null ? "<null>" : signature.substring(0, Math.min(8, signature.length())),
                rawBody.length());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "drone webhook HMAC verify failed");
        }

        // 2. 反序列化
        DroneWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, DroneWebhookPayload.class);
        } catch (Exception e) {
            log.error("[DroneWebhook] payload parse failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "payload parse failed: " + e.getMessage());
        }

        // 3. dispatch
        log.info("[DroneWebhook] event={} droneBuildId={}", payload.getEvent(), payload.getDroneBuildId());
        BuildRecord record = buildRecordMapper.selectByDroneBuildId(payload.getDroneBuildId());
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                "build record not found: droneBuildId=" + payload.getDroneBuildId());
        }

        switch (payload.getEvent()) {
            case "step_finished" -> handleStepFinished(record.getId(), payload);
            case "build_finished" -> handleBuildFinished(record.getId(), payload);
            case "build_started" -> handleBuildStarted(record.getId(), payload);
            default -> log.warn("[DroneWebhook] unknown event: {}", payload.getEvent());
        }
        return ApiResponse.ok();
    }

    private void handleBuildStarted(Long buildRecordId, DroneWebhookPayload p) {
        buildService.markBuildRunning(buildRecordId, LocalDateTime.now());
        log.info("[DroneWebhook] build_started buildRecordId={}", buildRecordId);
    }

    private void handleStepFinished(Long buildRecordId, DroneWebhookPayload p) {
        if (p.getStepName() == null || p.getLogContent() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "step_finished requires stepName + logContent");
        }
        buildService.saveStepLog(buildRecordId,
            p.getStepOrder() != null ? p.getStepOrder() : 0,
            p.getStepName(), p.getLogContent(),
            p.getStepStartedAt() != null ? p.getStepStartedAt() : LocalDateTime.now(),
            p.getStepFinishedAt() != null ? p.getStepFinishedAt() : LocalDateTime.now());
        log.info("[DroneWebhook] step_finished buildRecordId={} step={}", buildRecordId, p.getStepName());
    }

    private void handleBuildFinished(Long buildRecordId, DroneWebhookPayload p) {
        if (p.getStatus() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "build_finished requires status");
        }
        buildService.markBuildFinished(buildRecordId, p.getStatus(),
            p.getImageTag(), p.getHarborImageUrl(), LocalDateTime.now());
        buildRecordMapper.markLogPersisted(buildRecordId);
        log.info("[DroneWebhook] build_finished buildRecordId={} status={} imageTag={}",
            buildRecordId, p.getStatus(), p.getImageTag());
    }
}

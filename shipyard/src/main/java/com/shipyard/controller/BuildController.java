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

import com.shipyard.dto.ApiResponse;
import com.shipyard.dto.BuildCreateRequest;
import com.shipyard.dto.BuildLogResponse;
import com.shipyard.dto.BuildResponse;
import com.shipyard.dto.PageResponse;
import com.shipyard.realtime.BuildLogNotifier;
import com.shipyard.service.BuildService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Build Controller — 构建记录 API.
 *
 * <p>7 个端点:
 * <ul>
 *   <li>{@code POST   /api/builds}                              触发构建</li>
 *   <li>{@code GET    /api/builds/{id}}                         查详情</li>
 *   <li>{@code GET    /api/projects/{projectId}/builds}         项目下构建列表 (分页 + status 过滤)</li>
 *   <li>{@code POST   /api/builds/{id}/cancel}                  取消构建</li>
 *   <li>{@code GET    /api/builds/{id}/steps}                   列 step 元信息</li>
 *   <li>{@code GET    /api/builds/{id}/steps/{stepName}}        查单个 step 日志</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BuildController {

    private final BuildService buildService;
    private final BuildLogNotifier buildLogNotifier;

    @PostMapping("/api/builds")
    public ApiResponse<BuildResponse> create(@RequestBody @Valid BuildCreateRequest request) {
        return ApiResponse.ok(buildService.createBuild(request));
    }

    @GetMapping("/api/builds/{id}")
    public ApiResponse<BuildResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(buildService.getBuild(id));
    }

    @GetMapping("/api/projects/{projectId}/builds")
    public ApiResponse<PageResponse<BuildResponse>> list(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        List<BuildResponse> records = buildService.listBuilds(projectId, status, pageNum, pageSize);
        long total = records.size(); // V1 简化, 返当前页条数; 完整 total 需另查 count, M5 E2E 验证够用
        return ApiResponse.ok(new PageResponse<>(records, total, pageNum, pageSize));
    }

    @PostMapping("/api/builds/{id}/cancel")
    public ApiResponse<BuildResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(buildService.cancelBuild(id));
    }

    @GetMapping("/api/builds/{id}/steps")
    public ApiResponse<List<BuildLogResponse>> listSteps(@PathVariable("id") Long buildRecordId) {
        return ApiResponse.ok(buildService.listStepLogs(buildRecordId));
    }

    @GetMapping("/api/builds/{id}/steps/{stepName}")
    public ApiResponse<String> getStepLog(@PathVariable("id") Long buildRecordId, @PathVariable String stepName) {
        return ApiResponse.ok(buildService.getStepLog(buildRecordId, stepName));
    }

    /**
     * SSE 实时日志订阅 — {@code GET /api/builds/{id}/stream}.
     *
     * <p>MIME: {@code text/event-stream}, 长连接.
     *
     * <p>事件流:
     * <ul>
     *   <li>{@code event: step} — 新 step log 落库时推, data = {@code BuildLogEvent.step*} 字段</li>
     *   <li>{@code event: build} — build 终态时推, data = {@code BuildLogEvent.status} + image info
     *       (推完后连接自动关)</li>
     * </ul>
     *
     * <p><b>客户端建议</b>:
     * <ol>
     *   <li>先 GET /api/builds/{id}/steps 拿已有 step (snapshot)</li>
     *   <li>再 EventSource 订阅本端点, 拿后续事件</li>
     * </ol>
     *
     * <p>V1 demo: mock drone 跑 3 step × 3s = 9s 后推终态关连接.
     */
    @GetMapping(value = "/api/builds/{id}/stream", produces = "text/event-stream")
    public SseEmitter streamBuildLogs(@PathVariable("id") Long buildId) {
        log.info("[BuildController] SSE subscribe buildId={}", buildId);
        return buildLogNotifier.subscribe(buildId);
    }
}

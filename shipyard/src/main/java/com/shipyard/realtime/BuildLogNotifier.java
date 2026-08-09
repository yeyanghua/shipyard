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

package com.shipyard.realtime;

import com.shipyard.entity.BuildLog;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 构建日志实时通知器 — shipyard V1 实时日志 (SSE) 的核心.
 *
 * <p><b>职责</b>:
 * <ol>
 *   <li>{@link #subscribe(Long)} — 客户端订阅, 返回 {@link SseEmitter} 长连接</li>
 *   <li>{@link #notifyStepLog(BuildLog)} — Mock drone 落完 step log 后调, 推给该 buildId 的所有订阅者</li>
 *   <li>{@link #notifyBuildFinished(Long, String)} — 终态时推, 客户端收到后断连接</li>
 * </ol>
 *
 * <p><b>线程安全</b>:
 * <ul>
 *   <li>{@code Map<Long, List<SseEmitter>>} — {@link ConcurrentHashMap} + {@link CopyOnWriteArrayList}</li>
 *   <li>推消息时遍历 list, 某个 emitter 异常不影响其他</li>
 *   <li>客户端断线后 emitter 报 IOException → catch + 从 list 移除</li>
 * </ul>
 *
 * <p><b>V1 demo 限制</b>:
 * <ul>
 *   <li>不持久化事件历史 (重启 shipyard 会丢)</li>
 *   <li>同 buildId 多订阅者, 一份事件所有人收</li>
 *   <li>无重试 / 无 ack — 客户端连上后才推 (之前的事件拿不到, 这就是为啥 SSE endpoint
 *       第一次要先返 build_log 全量 + then subscribe 推新事件)</li>
 * </ul>
 */
@Slf4j
@Component
public class BuildLogNotifier {

    /**
     * 内存订阅表 — buildId → 该 build 的所有订阅者.
     *
     * <p>{@link CopyOnWriteArrayList} 适合"读多写少"场景, 推消息 (读) 多, 订阅/取消 (写) 少.
     */
    private final Map<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /**
     * 默认超时: 5 分钟 (够 build 跑完 3 step × 5s/step).
     */
    private static final long DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L;

    /**
     * 订阅 buildId — 返回 {@link SseEmitter}, 客户端断线 / 异常自动清理.
     */
    public SseEmitter subscribe(Long buildId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        List<SseEmitter> list = subscribers.computeIfAbsent(buildId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        // 客户端断线 (timeout / error / complete) 时, 从 list 移除
        emitter.onCompletion(() -> remove(buildId, emitter));
        emitter.onTimeout(() -> {
            log.debug("[BuildLogNotifier] emitter timeout buildId={}", buildId);
            remove(buildId, emitter);
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.debug("[BuildLogNotifier] emitter error buildId={} ex={}", buildId, ex.getMessage());
            remove(buildId, emitter);
        });

        log.info("[BuildLogNotifier] subscribe buildId={}, total subscribers for this build={}", buildId, list.size());
        return emitter;
    }

    /**
     * 推 step log 给 buildId 的所有订阅者.
     *
     * <p>事件格式 (SSE):
     * <pre>
     * event: step
     * data: {"id":123,"stepName":"compile","logContent":"...","stepOrder":1,...}
     * </pre>
     */
    public void notifyStepLog(BuildLog buildLog) {
        List<SseEmitter> list = subscribers.get(buildLog.getBuildRecordId());
        if (list == null || list.isEmpty()) {
            return; // 没人订阅
        }
        BuildLogEvent event = BuildLogEvent.fromStep(buildLog);
        for (SseEmitter emitter : list) {
            sendQuietly(emitter, "step", event);
        }
        log.debug(
                "[BuildLogNotifier] notify step buildId={} step={} subscribers={}",
                buildLog.getBuildRecordId(),
                buildLog.getStepName(),
                list.size());
    }

    /**
     * 推 build 终态 — 客户端收到后可以断连接.
     *
     * <p>事件格式:
     * <pre>
     * event: build
     * data: {"buildId":123,"status":"SUCCESS","imageTag":"v1.0.0-..."}
     * </pre>
     */
    public void notifyBuildFinished(Long buildId, String status, String imageTag, String harborImageUrl) {
        List<SseEmitter> list = subscribers.get(buildId);
        if (list == null || list.isEmpty()) {
            return;
        }
        BuildLogEvent event = BuildLogEvent.fromFinished(buildId, status, imageTag, harborImageUrl);
        for (SseEmitter emitter : list) {
            sendQuietly(emitter, "build", event);
        }
        log.info(
                "[BuildLogNotifier] notify finished buildId={} status={} subscribers={}", buildId, status, list.size());

        // 推完终态后, 关所有连接
        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
        subscribers.remove(buildId);
    }

    private void sendQuietly(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.debug("[BuildLogNotifier] send failed, removing subscriber: {}", e.getMessage());
            // 客户端可能已断, 找机会 remove (onError 也会兜底)
        } catch (IllegalStateException e) {
            // emitter 已 complete, 跳过
            log.debug("[BuildLogNotifier] emitter already complete");
        }
    }

    private void remove(Long buildId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(buildId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                subscribers.remove(buildId);
            }
        }
    }

    // ============== helper for testing ==============

    /**
     * 当前订阅数 (测试用).
     */
    public int subscriberCount(Long buildId) {
        List<SseEmitter> list = subscribers.get(buildId);
        return list == null ? 0 : list.size();
    }
}

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

package com.shipyard.worker;

import com.shipyard.worker.entity.Worker;
import com.shipyard.worker.mapper.WorkerMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker 心跳扫描 — shipyard 端 @Scheduled 30s 扫一次心跳超时的 worker, 标 offline.
 *
 * <p>M9 fix-commit 设计要点:
 * <ul>
 *   <li>shipyard 不主动 promote worker 角色 (worker 自治, 不管主备)</li>
 *   <li>shipyard 只标 stale (last_heartbeat_at < now-90s) 的 worker 为 offline,
 *       从 WorkerSelector 候选池剔除 — 等 worker 自己再来 register 自愈</li>
 *   <li>90s 阈值 = heartbeat 30s × 3 (允许漏 1-2 次, 避免网络抖动误标)</li>
 * </ul>
 *
 * <p>整体行为跟 K8s Deployment controller 一致: 不主动调度, 只 reconcile desired
 * state (online) vs actual (last_heartbeat_at). worker 自治 + shipyard 被动路由.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerHealthScanner {

    /**
     * 心跳超时阈值: 90s = heartbeat 30s × 3. 漏 1-2 次是网络抖动, 不算挂.
     */
    static final int HEARTBEAT_STALE_SECONDS = 90;

    private final WorkerMapper workerMapper;

    /**
     * 30s 扫一次, 找心跳超时的 online worker 标 offline.
     *
     * <p>实现:
     * <ol>
     *   <li>查所有 status=online 的 worker (不限 env, 一次性全扫)</li>
     *   <li>过滤 last_heartbeat_at < now - 90s</li>
     *   <li>逐个调 mapper.markOffline(id) (status=online 才标, 防止重复)</li>
     *   <li>log 告警 (warn 级, shipyard log 能 grep)</li>
     * </ol>
     *
     * <p>{@code fixedDelay = 30_000} — 上次执行完 30s 后再跑 (不是 fixedRate, 防止任务堆积).
     * 启动时 {@code initialDelay = 30_000} 推 30s, 等 shipyard 启动完再扫 (避免启动期误标).
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void scanStaleWorkers() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(HEARTBEAT_STALE_SECONDS);

        // V1 简版: 全表扫 (worker 表 N 通常 < 100, 不需要分页 + env 索引)
        // V1.5+ worker 多起来: 加 idx_worker_heartbeat + 分页, 或按 env 切分多线程
        List<Worker> stale = workerMapper.selectStaleOnline(threshold);

        if (stale.isEmpty()) {
            log.debug("WorkerHealthScanner: 没有过期 worker (threshold={})", threshold);
            return;
        }

        log.warn("WorkerHealthScanner: 发现 {} 个过期 worker, 标 offline", stale.size());
        for (Worker w : stale) {
            int affected = workerMapper.markOffline(w.getId());
            if (affected > 0) {
                log.warn("  worker 标 offline: id={} name={} envId={} url={} lastHeartbeat={}",
                        w.getId(), w.getWorkerUrl(), w.getEnvId(), w.getWorkerUrl(),
                        w.getLastHeartbeatAt());
            }
        }
    }
}

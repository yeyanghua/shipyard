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

package com.shipyard.worker.selector;

import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.worker.entity.Worker;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * RoundRobinSelector — 严格轮询, V1 默认策略.
 *
 * <p>状态: 进程内 ConcurrentHashMap&lt;envId, AtomicLong&gt; 记录每个 env 上次选到
 * 列表第几个. shipyard 单实例够用, 多实例 shipyard HA 时计数不一致(每个 shipyard
 * 各自轮询) — M9.5 改 Redis 共享.
 *
 * <p>选法:
 * <ol>
 *   <li>读 envId 对应的 AtomicLong, 拿到上次选到的 index</li>
 *   <li>next = (last + 1) % size, 取 candidates[next], AtomicLong.set(next)</li>
 *   <li>如果 size 变了 (worker 增/减), modulo 算出来仍然合法, 无需重置</li>
 * </ol>
 *
 * <p>并发安全: ConcurrentHashMap 存, AtomicLong 自增, 多线程同时 select 同 env
 * 也能保证每个 index 都被选到 (虽然不是严格顺序).
 */
@Component
public class RoundRobinSelector implements WorkerSelector {

    /**
     * envId → 上次选到列表第几个的计数器.
     *
     * <p>key: envId (Long); value: AtomicLong (上次选到的 index).
     * M9 fix-commit 后, candidates 是 env 下 online worker 列表 (按 last_heartbeat_at DESC).
     * RR 只在 size > 1 才有意义, size = 1 直接返回.
     */
    private final ConcurrentHashMap<Long, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public Worker select(List<Worker> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "没有可用的 worker (candidates 为空)");
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // 用 worker 表的第一个 (last_heartbeat_at 最新) 的 envId 当 key
        Long envId = candidates.get(0).getEnvId();
        AtomicLong counter = counters.computeIfAbsent(envId, k -> new AtomicLong(-1));
        long last = counter.get();
        long next = (last + 1) % candidates.size();
        // CAS 自增, 防止并发 select 拿到同一 index
        while (!counter.compareAndSet(last, next)) {
            last = counter.get();
            next = (last + 1) % candidates.size();
        }
        return candidates.get((int) next);
    }

    @Override
    public String name() {
        return "ROUND_ROBIN";
    }

    /**
     * 清空计数器 — 单元测试 + WorkerHealthScanner 删 env 时调用.
     */
    public void resetCounter(Long envId) {
        counters.remove(envId);
    }
}

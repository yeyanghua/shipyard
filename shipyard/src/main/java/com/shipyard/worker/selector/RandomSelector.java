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
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * RandomSelector — 随机选 1 个.
 *
 * <p>简单负载均: 每次 deploy 独立随机, 不维护状态. 跟 round-robin 区别:
 * <ul>
 *   <li>RR — 严格轮询, 短期看每个 worker 任务数差距 ≤1</li>
 *   <li>RANDOM — 长期看均匀, 短期可能有多个任务砸到同 worker</li>
 * </ul>
 *
 * <p>用 {@link ThreadLocalRandom} 而不是 {@code new Random()}, 避免多线程争用.
 */
@Component
public class RandomSelector implements WorkerSelector {

    @Override
    public Worker select(List<Worker> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "没有可用的 worker (candidates 为空)");
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    @Override
    public String name() {
        return "RANDOM";
    }
}

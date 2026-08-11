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
import org.springframework.stereotype.Component;

/**
 * FirstAvailableSelector — 取候选列表的第 1 个.
 *
 * <p>实现假设 WorkerMapper.selectByEnvAndStatus 已经按 last_heartbeat_at DESC 排好序,
 * 所以第 1 个就是 "最新心跳的 worker". 1 env 1 worker 时直接命中, 1 env N worker 时
 * 永远选最新心跳那个.
 *
 * <p>用 {@code @Component} 装配, Spring 启动时会扫到. 装配的 selector 实际被
 * {@link WorkerSelectorConfig} 按 yml 切换, 这个类不直接被 DeployService 引用 —
 * DeployService 只看到接口.
 */
@Component
public class FirstAvailableSelector implements WorkerSelector {

    @Override
    public Worker select(List<Worker> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "没有可用的 worker (candidates 为空)");
        }
        return candidates.get(0);
    }

    @Override
    public String name() {
        return "FIRST_AVAILABLE";
    }
}

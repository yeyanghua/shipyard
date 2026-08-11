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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 可选的 WorkerSelector 策略枚举 — application.yml 配 {@code shipyard.worker.selector}
 * 时用字符串匹配, 避免手敲 bean 名字.
 *
 * <p>3 个值跟 M9 实现的 3 个 Selector 一一对应.
 *
 * <p>新增策略: 加 enum 值 + 写新 Selector class + 在 {@link WorkerSelectorConfig} 装配即可.
 */
public enum SelectorStrategy {

    /** 取最新心跳的 1 个 (1 env 1 worker 直接命中) */
    FIRST_AVAILABLE,

    /** 随机 (简单负载均) */
    RANDOM,

    /** 轮询 (进程内计数器, shipyard 单实例够用) — V1 默认 */
    ROUND_ROBIN;

    /**
     * 把所有 WorkerSelector bean 转成 strategy → bean map, 装配时用.
     *
     * @param selectors Spring 注入的 selector 列表
     * @return strategy 字符串到 selector 的映射
     */
    public static Map<SelectorStrategy, WorkerSelector> index(List<WorkerSelector> selectors) {
        return selectors.stream()
                .collect(Collectors.toMap(
                        s -> SelectorStrategy.valueOf(s.name()),
                        Function.identity()
                ));
    }

    /**
     * 从 index 找策略对应的 selector — 找不到返 empty (装配错误时).
     */
    public static Optional<WorkerSelector> lookup(Map<SelectorStrategy, WorkerSelector> index,
                                                   SelectorStrategy strategy) {
        return Optional.ofNullable(index.get(strategy));
    }
}

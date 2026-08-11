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

package com.shipyard.config;

import com.shipyard.worker.selector.SelectorStrategy;
import com.shipyard.worker.selector.WorkerSelector;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WorkerSelector 装配 — 按 application.yml {@code shipyard.worker.selector} 选实现.
 *
 * <p>3 个 Selector 都用 {@code @Component} 自动扫到, 启动时把它们 index 到
 * {@code strategy → selector} map, 暴露 {@code activeWorkerSelector} bean 给
 * DeployService 注入.
 *
 * <p>M9 fix-commit 设计要点:
 * <ul>
 *   <li>不引入 Spring profile 切策略 (yml 切更直观, 切时不用重启 cluster 维度)</li>
 *   <li>启动时 log 当前选中的策略, 方便 ops 排查</li>
 *   <li>策略名错时启动失败 (fail-fast), 避免上线后才发现</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WorkerSelectorConfig {

    private final List<WorkerSelector> allSelectors;

    @Value("${shipyard.worker.selector:ROUND_ROBIN}")
    private String strategyName;

    private Map<SelectorStrategy, WorkerSelector> index;

    /**
     * 启动时建好 index — 装配时把 list 转成 map 方便 O(1) lookup.
     */
    @PostConstruct
    public void buildIndex() {
        this.index = SelectorStrategy.index(allSelectors);
        log.info("WorkerSelector 候选策略: {}",
                index.keySet().stream().map(Enum::name).toList());
    }

    /**
     * 暴露当前激活的 selector 给 DeployService 注入.
     *
     * <p>如果 yml 配错 (策略名不合法 / 没对应实现), 这里 fail-fast 抛
     * IllegalStateException, 应用启动失败 — 跟 Spring 启动期校验一致的策略.
     */
    @Bean
    public WorkerSelector activeWorkerSelector() {
        SelectorStrategy strategy;
        try {
            strategy = SelectorStrategy.valueOf(strategyName);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "shipyard.worker.selector 配置错误: " + strategyName
                    + " 不在可选范围 "
                    + java.util.Arrays.toString(SelectorStrategy.values()), e);
        }
        WorkerSelector selector = index.get(strategy);
        if (selector == null) {
            throw new IllegalStateException(
                    "shipyard.worker.selector=" + strategyName
                    + " 没找到对应实现 (检查 " + strategyName + "Selector 类是否 @Component 扫到)");
        }
        log.info("WorkerSelector 激活策略: {} (实现: {})", strategyName, selector.getClass().getSimpleName());
        return selector;
    }
}

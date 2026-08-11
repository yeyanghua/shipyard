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

import com.shipyard.worker.entity.Worker;
import java.util.List;

/**
 * Worker 调度策略接口 — 决定"deploy 任务派给哪个 worker 跑".
 *
 * <p>设计动机 (M9 fix-commit, 仔哥 2026-08-11 拍板):
 * <ul>
 *   <li>之前 M9 spec 想在 shipyard 里管 worker 主备 (PRIMARY/STANDBY) — 但 shipyard 单点 +
 *       env 多了 worker 多了会让 shipyard 调度逻辑膨胀, 不是好设计</li>
 *   <li>改为: worker 是自治服务, shipyard 只被动路由, 调度策略可插拔</li>
 *   <li>跟 K8s Deployment controller / Consul service registry 设计哲学一致 — 调度层 + 业务层
 *       解耦, 加新策略不碰业务代码</li>
 * </ul>
 *
 * <p>3 个内置实现 (M9):
 * <ul>
 *   <li>{@link FirstAvailableSelector} — 取最新心跳的 1 个 (1 env 1 worker 直接命中)</li>
 *   <li>{@link RandomSelector} — 随机 (简单负载均)</li>
 *   <li>{@link RoundRobinSelector} — 轮询 (进程内计数器, shipyard 单实例够用) <b>默认</b></li>
 * </ul>
 *
 * <p>V1.5+ 候选 (留接口, 不实现):
 * <ul>
 *   <li>{@code WeightSelector} — worker 表加 weight 字段, 按权重</li>
 *   <li>{@code LatencySelector} — 选心跳延迟最低的</li>
 *   <li>{@code LocalFirstSelector} — 优先选 URL 跟 shipyard 同主机的</li>
 * </ul>
 *
 * <p>选哪个实现走 Spring 装配 — {@link com.shipyard.config.WorkerSelectorConfig} 读 yml
 * 决定注入哪个, 默认 RoundRobinSelector.
 */
public interface WorkerSelector {

    /**
     * 从候选 worker 列表里选 1 个执行 deploy 任务.
     *
     * @param candidates 候选 worker 列表, 已按 last_heartbeat_at DESC 排好序
     *                   (M9 fix-commit: 只过滤 status=online, 不再 role 限定)
     * @return 选中的 worker; 如果列表为空, 抛 BusinessException
     * @throws com.shipyard.common.exception.BusinessException 找不到可用 worker
     */
    Worker select(List<Worker> candidates);

    /**
     * 策略名 — 跟 application.yml 配置对应.
     *
     * <p>例: {@code shipyard.worker.selector: ROUND_ROBIN} 配的就是
     * {@link RoundRobinSelector} 的 {@code name()}.
     */
    String name();
}

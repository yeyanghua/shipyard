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

package com.shipyard.drone;

import java.util.Map;

/**
 * drone 客户端抽象 — shipyard 调 drone CI 的统一接口.
 *
 * <p>V1 demo 用 {@link MockDroneClient} (本地异步模拟),
 * V1.5 接真实 drone 时换 {@code RealDroneClient} (调 drone REST API).
 *
 * <p>设计原则: BuildService 只依赖这个 interface, 不知道背后是 mock 还是 real.
 * 这样 E2E 测 + 单元测试可以注入 mock 不用起真 drone.
 */
public interface DroneClient {

    /**
     * 触发一次构建.
     *
     * <p>行为:
     * <ul>
     *   <li>V1 mock: 立即返回 droneBuildId, 后台异步模拟 build 跑 3 个 step + 落日志</li>
     *   <li>V1.5 real: 调 drone {@code POST /api/v1/repos/{owner}/{repo}/builds} 拿到真实 build id</li>
     * </ul>
     *
     * @param request 构建请求 (含 shipyard droneBuildId, repoUrl, commit 信息, env vars)
     * @return drone 端 build id (V1 mock 跟 shipyard 生成的一致; V1.5 是 drone 自己的 ID)
     */
    String triggerBuild(DroneBuildRequest request);

    /**
     * 取消一次构建.
     *
     * <p>V1 mock: 仅标记 cancelled = true, BuildService 改 build_record status = CANCELED.
     * <br>V1.5 real: 调 drone {@code POST /api/v1/builds/{id}?action=cancel}.
     *
     * @param droneBuildId shipyard 生成的 drone build id
     */
    void cancelBuild(String droneBuildId);

    /**
     * drone 构建请求 — shipyard → drone 方向.
     *
     * <p>字段对齐 drone REST API 的最小子集.
     */
    record DroneBuildRequest(
            String droneBuildId, // shipyard 生成的 ID, 用作关联 key
            Long projectId, // shipyard project ID (drone 不知道, 但 shipyard log 要有)
            String repoUrl, // git repo URL
            String commitSha, // 拉这个 commit
            String commitMessage, // 仅供 shipyard log 用
            Map<String, String> envVars // 注入到 drone 构建环境 (V5 接 EnvVariableService.resolveAll)
            ) {}
}

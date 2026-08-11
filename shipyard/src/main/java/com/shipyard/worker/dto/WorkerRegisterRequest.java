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

package com.shipyard.worker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Worker 主动注册请求 — POST /api/workers/register.
 *
 * <p>由 worker 端 (Go 进程) 启动时调用, 告诉 shipyard "我在, 我是 xxx".
 *
 * <p>字段命名跟 worker Go 端 {@code types.RegisterRequest} 对齐 (camelCase, JSON 一致).
 */
@Data
public class WorkerRegisterRequest {

    /** worker 唯一名, 默认 {@code worker-${HOSTNAME}}. */
    @NotBlank
    private String workerName;

    /** 所属环境名 (dev / test / prod), 用于查 env_id. */
    @NotBlank
    private String env;

    /** worker URL — shipyard → worker 调用的目标. */
    @NotBlank
    private String workerUrl;

    /** worker token 明文 — shipyard 端 SHA-256 哈希入库, 不存明文. */
    @NotBlank
    private String workerToken;

    /** worker 版本 (Go ldflags 注入的 WORKER_VERSION). */
    private String version;

    /** (M8.3+) k8s 集群版本, M8.2 阶段不存. */
    private String k8sVersion;

    /** (M8.3+) 节点名, M8.2 阶段不存. */
    private String nodeName;

    /**
     * (M9+) 角色自荐 — worker 启动时自己希望当什么角色.
     * <ul>
     *   <li>{@code PRIMARY} (默认) — 想当主, 跑 deploy 任务</li>
     *   <li>{@code STANDBY} — 想当备, 只 heartbeat + 故障接管</li>
     * </ul>
     * shipyard 端会按同 env 已有 online worker 数 self-elect, 不一定接受 hint.
     */
    private String roleHint;
}

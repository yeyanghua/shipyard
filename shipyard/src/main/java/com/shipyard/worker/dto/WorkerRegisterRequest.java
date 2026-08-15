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
import lombok.Data;

/**
 * Worker 主动注册请求 — POST /api/workers/register.
 *
 * <p>由 worker 端 (Go 进程) 启动时调用, 告诉 shipyard "我在, 我是哪个 pod, token 是 xxx".
 *
 * <p>M9.5 redesign: register 改成 <b>严格模式</b>:
 * <ul>
 *   <li>shipyard 端必须先有预登记 row (用户在 UI 创建)</li>
 *   <li>register 用 {@code (env_id, pod_name)} 严格匹配预登记 row</li>
 *   <li>找不到 → 返 404, 提示 "请先在 shipyard UI 创建 worker"</li>
 *   <li>token SHA-256 校验必须通过 (shipyard 端生成, worker 端从 env 读)</li>
 *   <li>校验通过 → 状态从 PLANNED 变 PROVISIONING, 返回 workerId 用于心跳</li>
 * </ul>
 *
 * <p>字段命名跟 worker Go 端 {@code types.RegisterRequest} 对齐 (camelCase, JSON 一致).
 */
@Data
public class WorkerRegisterRequest {

    /**
     * k8s pod metadata.name — M9.5 register 严格匹配主键之一 (跟 env 一起).
     *
     * <p>来源: worker 端从 downward API 读 {@code POD_NAME} env var.
     * shipyard 端拿这个跟预登记 row 的 {@code pod_name} 比对.
     */
    @NotBlank
    private String podName;

    /** 所属环境名 (dev / test / prod), 用于查 env_id. */
    @NotBlank
    private String env;

    /**
     * worker URL — worker 启动后上报, shipyard → worker 调用走这个.
     *
     * <p>worker 自己拼: NodePort 模式 {@code http://<node-ip>:<nodePort>},
     * 或者 svc DNS 模式 {@code http://shipyard-worker.shipyard.svc.cluster.local:8888}.
     */
    @NotBlank
    private String workerUrl;

    /** worker token 明文 — shipyard 端 SHA-256 哈希后跟预登记 row 的 hash 比对, 不存明文. */
    @NotBlank
    private String workerToken;

    /** worker 版本 (Go ldflags 注入的 WORKER_VERSION). */
    private String version;

    /** (M8.3+) k8s 集群版本, M9.5 register 阶段不强制. */
    private String k8sVersion;

    /** (M8.3+) 节点名, M9.5 register 阶段不强制 (downward API 注入). */
    private String nodeName;

    /** (M8.3+) pod IP, M9.5 register 阶段不强制 (downward API 注入). */
    private String podIp;

    // ============================================================
    // M9.5 删除字段
    // ============================================================
    // workerName: 旧模型用 (env_id, worker_name) 联合主键, M9.5 改成 (env_id, pod_name),
    //             workerName 只作展示名, 不参与唯一性约束, 所以 register 不再必填.
    //             用户在 UI 创建 worker 时已经填了 name 字段.
    // roleHint:   M9 fix-commit 已经删 (worker 自治, shipyard 不参与角色分配).
}

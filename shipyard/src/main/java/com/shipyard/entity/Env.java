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

package com.shipyard.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 环境定义 — 一套 k8s 集群的元数据.
 *
 * <p>对应 V5__undo_worker_redesign.sql 的 {@code env} 表 (V1 阶段 V3 模式: env 自管 workerUrl / k8sNamespace).
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code name} — 英文唯一标识 (dev / staging / prod)</li>
 *   <li>{@code isProduction} — 生产环境标记, 影响前端确认弹窗和告警级别</li>
 *   <li>{@code workerUrl} — 该 env 下的 worker 服务 URL (V1 阶段 shipyard 内部维护, 演示 K8s 调谁用)</li>
 *   <li>{@code k8sNamespace} — 该 env 对应的 k8s namespace (shipyard 调 K8s API 用)</li>
 *   <li>{@code workerTokenEnc} — AES-256 加密的 worker 鉴权 token (V1 阶段 shipyard 内部存, 不暴露明文)</li>
 * </ul>
 *
 * <p>V1 阶段: worker 部署细节在 env 上, 不再拆 worker 表 (M9.5 严格 register / pre-register 模型回退).
 * V1.5+ 重新设计时: 如果决定真接 worker, 写 V6 migration 把 workerUrl / k8sNamespace / workerTokenEnc 拆到 worker 表.
 *
 * <p>被引用方:
 * <ul>
 *   <li>{@link com.shipyard.entity.ProjectEnv} — 关联项目 (N:N)</li>
 *   <li>{@link com.shipyard.entity.EnvVariable} — 环境变量 (一对多, 必填 env_id)</li>
 *   <li>{@link com.shipyard.service.WorkerService} — worker 业务 (V1 in-process 模拟, 1 env 可挂多个 worker)</li>
 *   <li>{@link com.shipyard.entity.DeployRecord} — 发布记录 (一对多)</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("env")
public class Env extends BaseEntity {

    /**
     * 环境唯一名 (英文, 小写字母/数字/中划线), 例如 {@code dev} / {@code staging} / {@code prod}.
     */
    private String name;

    /**
     * 显示名, 例如 "开发环境" / "预发环境" / "生产环境".
     */
    private String displayName;

    /**
     * 集群类型 — V1 只支持 {@code k8s}. V1.5 可能加 {@code docker-compose}.
     */
    private String clusterType;

    /**
     * 是否生产环境 — 影响:
     * <ul>
     *   <li>前端: 触发构建/发布时二次确认弹窗</li>
     *   <li>告警: P0/P1 升级策略</li>
     *   <li>审计: 单独标记</li>
     * </ul>
     */
    private Integer isProduction;

    /**
     * 该 env 下的 worker 服务 URL.
     *
     * <p>V1 阶段 shipyard 后端内部维护, shipyard 调 K8s API 走这个 (V1 阶段无真 worker, shipyard 直连 K8s).
     * 默认: {@code http://localhost:8080} (shipyard 自指, V1 阶段演示用, V1.5+ 真接 worker 时改 workerUrl)
     */
    private String workerUrl;

    /**
     * 该 env 对应的 k8s namespace — shipyard 调 K8s API 时指定这个 ns.
     *
     * <p>env.name == k8sNamespace (1:1 对应, V1 阶段简化).
     * shipyard UI 创 env 时自动用 env.name 当默认 k8s_namespace.
     */
    private String k8sNamespace;

    /**
     * worker 鉴权 token (AES-256 加密, V1 阶段 shipyard 内部维护, 演示用).
     *
     * <p>V1 阶段 shipyard → K8s API 调用不走这个 token, 但字段保留兼容 V3 模式 + V1.5+ 真接 worker 时用.
     */
    private String workerTokenEnc;
}

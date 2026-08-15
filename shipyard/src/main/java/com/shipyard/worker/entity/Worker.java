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

package com.shipyard.worker.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.shipyard.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Worker 实体 — M9.5 redesign (1 worker = 1 pod, 预登记 + token 鉴权).
 *
 * <p>对应 V4__redesign_worker_table.sql 的 {@code worker} 表.
 *
 * <p>关键设计 (M9.5):
 * <ul>
 *   <li><b>1 worker = 1 pod</b> — 每条 row 对应一个具体的 k8s pod, 2 pod 不会再共享 1 row
 *       (旧 M9 commit-16 的 2 pod 1 row 模型已废弃, 跟实际 pod 数量对不齐, UI "实例数" 永远 1)</li>
 *   <li><b>预登记</b> — 用户在 shipyard UI 填 worker 基础信息 (name / podName),
 *       shipyard 入库 (status=PLANNED), 用户再去 k8s 部署 pod, pod register 时
 *       shipyard 严格匹配 (env_id, pod_name), 找不到就报错 "请先在 UI 创建 worker"</li>
 *   <li><b>token 鉴权</b> — shipyard 端生成 32 字节随机 base64 token, 存 SHA-256 哈希,
 *       明文只展示一次 (用户复制到 k8s manifest 的 WORKER_TOKEN env var), shipyard → worker
 *       调用带 Bearer token 头, worker 端用同样哈希校验</li>
 *   <li><b>状态机</b> — PLANNED → PROVISIONING (register 找到 row) → ONLINE (首次心跳)
 *       → OFFLINE (心跳超时 90s) / UNHEALTHY (worker 自检失败) → 软删 (UI 删 / k8s pod 没了)</li>
 * </ul>
 *
 * <p>字段职责:
 * <ul>
 *   <li>{@code envId} — 所属环境, 1:N 关系 (1 env — N worker)</li>
 *   <li>{@code name} — shipyard 内部展示名, 同 env 下唯一</li>
 *   <li>{@code podName} — 匹配 k8s pod metadata.name, 同 env 下唯一, register 严格匹配</li>
 *   <li>{@code workerUrl} — worker 服务 URL, worker 启动后 register 时上报,
 *       shipyard → worker 调用走这个</li>
 *   <li>{@code workerTokenHash} — token SHA-256 哈希 (Hex 64字符), 不存明文</li>
 *   <li>{@code status} — PLANNED / PROVISIONING / ONLINE / OFFLINE / UNHEALTHY</li>
 *   <li>{@code health} / {@code healthDetail} — worker 自检状态 (HEALTHY / UNHEALTHY + 原因)</li>
 *   <li>{@code lastHeartbeatAt} — worker 30s 上报一次, WorkerHealthScanner 扫超时</li>
 *   <li>{@code createdBy} / {@code updatedBy} / {@code createdAt} / {@code updatedAt} — 审计字段
 *       (V1 默认 'system', V1.5 接用户体系后存 userId/email; register/heartbeat 等系统行为
 *       updated_by = 'system:register' / 'system:heartbeat' 标记)</li>
 * </ul>
 *
 * @see com.shipyard.worker.service.WorkerService
 * @see docs/M9.5-redesign.md
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("worker")
public class Worker extends BaseEntity {

    /** 所属环境 ID (1:N, 1 env — N worker). */
    private Long envId;

    /** shipyard 内部展示名 (同 env 下唯一, 用户创建时填). */
    private String name;

    /** k8s pod metadata.name (同 env 下唯一, register 严格匹配). */
    private String podName;

    /** 备注 / 描述 (用户可选填). */
    private String description;

    /**
     * worker 服务 URL — worker 启动后 register 时上报, shipyard → worker 调用走这个.
     *
     * <p>示例:
     * <ul>
     *   <li>V1 dev (NodePort 模式): {@code http://192.168.91.139:30080}</li>
     *   <li>k3d 集群内: {@code http://shipyard-worker.shipyard.svc.cluster.local:8888}</li>
     *   <li>生产集群: {@code http://shipyard-worker-prod.shipyard-prod.svc.cluster.local:8888}</li>
     * </ul>
     */
    private String workerUrl;

    /**
     * worker 鉴权 token 的 SHA-256 哈希 (Hex 64 字符).
     *
     * <p>不存明文. shipyard 端生成 32 字节随机 base64 token, 哈希后入库.
     * 明文只展示一次 (用户复制到 k8s manifest 的 WORKER_TOKEN env var).
     * shipyard → worker 调用时, 把明文 token 加 {@code Authorization: Bearer xxx} 头,
     * worker 端用同样哈希比对.
     */
    private String workerTokenHash;

    /**
     * 状态机: PLANNED / PROVISIONING / ONLINE / OFFLINE / UNHEALTHY.
     *
     * <p>迁移:
     * <ul>
     *   <li>PLANNED → PROVISIONING: register 时 shipyard 找到预登记 row, 状态切换</li>
     *   <li>PROVISIONING → ONLINE: 第一次心跳到达</li>
     *   <li>ONLINE → OFFLINE: 心跳超时 90s (WorkerHealthScanner @Scheduled 30s 扫)</li>
     *   <li>ONLINE/OFFLINE → UNHEALTHY: worker 自检失败 (k8s API / 内存 / 磁盘)</li>
     *   <li>UNHEALTHY → ONLINE: worker 自检恢复</li>
     *   <li>* → deleted=1: 软删 (UI 删除 / k8s pod 永久消失)</li>
     * </ul>
     */
    private String status;

    /** worker 自检状态: HEALTHY (默认) / UNHEALTHY. null = worker 还没上报过. */
    private String health;

    /** worker 自检失败原因 (例: "k8s API timeout 3s"). */
    private String healthDetail;

    /** 最后心跳时间 — worker 30s 上报一次. */
    private LocalDateTime lastHeartbeatAt;

    /** worker 版本 (从 ldflags 注入的 WORKER_VERSION). */
    private String version;

    // ============================================================
    // 审计字段 (M9.5 新增, BaseEntity 没有, 自己加, 跟 env_variable 风格一致)
    // ============================================================
    // V1 demo 默认 'system' (跟 BaseEntity 一致, 不强制建用户体系).
    // V1.5 接用户体系后, 存 userId/email.
    // register / heartbeat 等系统行为 updated_by 走 'system:register' / 'system:heartbeat' 标记.

    /** 创建人 (V1 默认 'system', V1.5 接用户体系后存 userId/email). */
    private String createdBy;

    /** 修改人 (register/heartbeat 等系统行为 updated_by = 'system:register' 等). */
    private String updatedBy;

    // ============================================================
    // M9 fix-commit: 删除 role 字段 (worker 自治模式)
    // ============================================================
    // 仔哥 2026-08-11 拍板: worker 是自治服务, 不在 shipyard 里管主备.
    // 选 worker 由 WorkerSelector 抽象包 (ROUND_ROBIN / FIRST_AVAILABLE / RANDOM)
    // 决定, 跟 K8s Deployment controller / Consul service registry 设计哲学一致 —
    // worker 多了不爆炸, shipyard 是被动路由层.
    // 详细见 docs/M9-detail.md §1 决策 6/7/8.
}

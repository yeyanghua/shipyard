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
 * Worker 注册表 — shipyard 端记录每个跑起来的 worker (Go 进程, in k8s pod).
 *
 * <p>对应 V1__init.sql 的 {@code worker} 表. spec §5.1 "一环境多个 worker" (高可用),
 * V1.2 + replicas=2 起 2 个 pod 都注册到同一 env_id.
 *
 * <p>关键字段:
 * <ul>
 *   <li>{@code envId} — 所属环境, 一对多 (env 1 — N worker)</li>
 *   <li>{@code workerUrl} — worker 服务的 URL, shipyard → worker 走这个</li>
 *   <li>{@code workerTokenHash} — token 哈希 (SHA-256), 用于 shipyard → worker 调用的 HMAC 验签</li>
 *   <li>{@code status} — online / offline / unhealthy (30s 没心跳 → unhealthy)</li>
 *   <li>{@code lastHeartbeatAt} — worker 每次心跳更新</li>
 * </ul>
 *
 * <p>token 不存明文: worker 主动注册时 shipyard 哈希入库, 后续 shipyard → worker 调用
 * 走 HMAC (跟 M5 drone webhook 验签同一机制, M8.3+ 接入).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("worker")
public class Worker extends BaseEntity {

    /**
     * 所属环境 ID — 一对多, 一 env 可挂多个 worker (高可用).
     */
    private Long envId;

    /**
     * worker 服务 URL — shipyard → worker 调用的目标.
     *
     * <p>示例:
     * <ul>
     *   <li>本地开发: {@code http://localhost:8888}</li>
     *   <li>k3d 集群内: {@code http://shipyard-worker.shipyard.svc.cluster.local:8888}</li>
     *   <li>生产集群: {@code http://shipyard-worker-prod.shipyard-prod.svc.cluster.local:8888}</li>
     * </ul>
     */
    private String workerUrl;

    /**
     * worker 鉴权 token 的 SHA-256 哈希 (Base16 / hex 编码, 64 字符).
     *
     * <p>不存明文. shipyard → worker 调 HTTP 时, 把明文 token (从 shipyard 端配置读)
     * 加 {@code Authorization: Bearer xxx} 头, worker 端用同样哈希比对.
     */
    private String workerTokenHash;

    /**
     * 最后心跳时间 — worker 30s 上报一次.
     */
    private LocalDateTime lastHeartbeatAt;

    /**
     * 状态: online / offline / unhealthy.
     *
     * <p>shipyard 定时任务扫描: {@code last_heartbeat_at < now() - 90s} → unhealthy.
     * unhealthy 超过 5min → 软删 (或保留 + 标 offline, M8.3+ 决定).
     */
    private String status;

    /**
     * worker 版本 (Go 二进制 ldflags 注入的 WORKER_VERSION).
     */
    private String version;

    /**
     * worker 角色 (M9 新增, V2 加字段):
     * <ul>
     *   <li>{@code PRIMARY} — 跑 deploy 任务 (1 env 最多 1 个, 推荐)</li>
     *   <li>{@code STANDBY} — 备机, 只 heartbeat + 故障接管</li>
     * </ul>
     *
     * <p>角色由 shipyard 端在 worker register 时 self-elect 决定:
     * 同 env 已有 0 个 online worker → 新 worker 给 PRIMARY;
     * 已 ≥1 个 → 给 STANDBY.
     *
     * <p>M9.5+ 考虑:加 {@code weight} 字段做 round-robin, 暂不实现.
     */
    private String role;
}

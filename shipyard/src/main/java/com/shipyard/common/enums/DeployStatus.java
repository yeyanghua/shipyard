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

package com.shipyard.common.enums;

import lombok.Getter;

/**
 * 部署状态机 — 跟 deploy_record.status 字段对应 (M9 新增).
 *
 * <p>状态流转:
 * <pre>
 *   PENDING ──trigger──▶ RUNNING ──worker 200──▶ SUCCESS
 *                            │
 *                            ├──worker 4xx/5xx──▶ FAILED
 *                            ├──30min 超时──────▶ TIMEOUT
 *                            └──用户取消──────▶ CANCELED
 * </pre>
 *
 * <p>跟 {@link BuildStatus} 几乎一致 — 简化为没有"部分成功"概念(因为 deploy 资源是 atomic apply,
 * 要么 apply 完要么没 apply 完,不像 build 有多 step 累计).
 *
 * <p>终态: {@link #SUCCESS} / {@link #FAILED} / {@link #TIMEOUT} / {@link #CANCELED}.
 * 终态 deploy_record 不再 transition.
 */
@Getter
public enum DeployStatus {

    /** 刚创建, shipyard 在拼 yaml + 调 worker */
    PENDING,

    /** worker 正在 apply 资源到 k8s */
    RUNNING,

    /** worker 返 200, 资源已 apply 成功 */
    SUCCESS,

    /** worker 返 4xx/5xx / 资源 apply 失败 / k8s API 拒绝 */
    FAILED,

    /** 30 分钟 shipyard 侧超时(防 worker 挂死, deploy_record 卡 PENDING/RUNNING 永远) */
    TIMEOUT,

    /** 用户主动取消 (PENDING / RUNNING 阶段) */
    CANCELED;

    /**
     * 是否为终态 — 终态 deploy_record 不再 transition.
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT || this == CANCELED;
    }
}

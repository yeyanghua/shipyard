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
 * 流水线模板审核状态 — 跟 {@code pipeline_template.review_status} 字段对应.
 *
 * <p>M6 V1 业务规则:
 * <ul>
 *   <li>用户手动创建或 AI 生成的 pipeline 一律 {@link #DRAFT}, 需要"审批通过"才能被 build 引用</li>
 *   <li>{@link #APPROVED} 才是"生效"状态, 业务上可以绑到 {@code is_active=1} 的当前版本</li>
 *   <li>{@link #REJECTED} 是终态 (被打回), 不能复活, 用户必须 fork 出一个新版本重审</li>
 * </ul>
 *
 * <p>状态流转:
 * <pre>
 *   (新建) ──▶ DRAFT ──approve──▶ APPROVED
 *                  │
 *                  └──reject───▶ REJECTED (终态)
 * </pre>
 *
 * <p>V1.5 可加 {@code PENDING_REVIEW} (异步审核) + {@code SUPERSEDED} (被新版本替代).
 */
@Getter
public enum ReviewStatus {

    /** 草稿 — 创建/AI 生成后的初始状态, 业务上不能被 build 引用 */
    DRAFT,

    /** 已通过 — 审批通过, 可以被 build 引用 + 设为 active */
    APPROVED,

    /** 已驳回 — 终态, 不能复活, 需要 fork 新版本 */
    REJECTED;

    /**
     * 是否为终态 — 终态不能 transition 到其他状态.
     */
    public boolean isTerminal() {
        return this == REJECTED;
    }

    /**
     * 是否可以被 build 引用 — 只有 {@link #APPROVED} 可以.
     */
    public boolean isBuildable() {
        return this == APPROVED;
    }
}

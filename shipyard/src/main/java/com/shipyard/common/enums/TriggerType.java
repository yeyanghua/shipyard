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
 * 构建触发方式 — 跟 build_record.trigger_type 字段对应.
 *
 * <p>V1 demo 阶段只支持 {@link #MANUAL} (用户点页面上"构建"按钮).
 * V1.5 加上 {@link #WEBHOOK} (仓库 push 触发) + {@link #API} (CI 上游调 shipyard API).
 */
@Getter
public enum TriggerType {

    /** 用户手动触发 — V1 demo 主路径 */
    MANUAL,

    /** 仓库 webhook 触发 — V1.5 接入 GitLab/Gitee webhook */
    WEBHOOK,

    /** 外部系统 API 触发 — V1.5 接 CI 上游 */
    API
}

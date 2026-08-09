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
 * 构建状态机 — 跟 build_record.status 字段对应.
 *
 * <p>状态流转:
 * <pre>
 *   PENDING ──trigger──▶ RUNNING ──success──▶ SUCCESS
 *                            │                 
 *                            ├──failed──▶ FAILED
 *                            ├──timeout──▶ TIMEOUT
 *                            └──canceled──▶ CANCELED
 * </pre>
 *
 * <p>终态: {@link #SUCCESS} / {@link #FAILED} / {@link #TIMEOUT} / {@link #CANCELED}.
 * 终态不能再 cancel 或重跑 (M5 业务规则, V1 不并发跑同一 build).
 */
@Getter
public enum BuildStatus {

    /** 刚创建,等待 drone 调度 */
    PENDING,

    /** drone 正在跑 (含 step 1-N 任意一个进行中) */
    RUNNING,

    /** 全部 step 成功,镜像已推 Harbor */
    SUCCESS,

    /** 任意 step 失败 */
    FAILED,

    /** drone 调度超时 (M5 V1 不实现,先占位) */
    TIMEOUT,

    /** 用户主动取消 */
    CANCELED;

    /**
     * 是否为终态 — 终态不能再 transition.
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == TIMEOUT || this == CANCELED;
    }
}

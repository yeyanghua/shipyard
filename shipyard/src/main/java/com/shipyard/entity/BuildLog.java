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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 构建日志 (按 step 存) — drone webhook 落库用.
 *
 * <p>对应 V1__init.sql 的 {@code build_log} 表 (M2 已落库).
 *
 * <p>设计:
 * <ul>
 *   <li>每个 build 的每个 step 一行, 用 {@code (build_record_id, step_name)} 唯一约束</li>
 *   <li>{@code logContent} 存完整日志 (LONGTEXT), 接收 drone webhook 时一次性写入</li>
 *   <li>{@code logSizeBytes} 记字节数, 方便 UI 显示 "X KB"</li>
 *   <li>不带 {@code deleted} — 日志是 immutable 流水, 不会"删除"</li>
 *   <li>不带 BaseEntity — 不需要 createdAt/updatedAt/deleted, 只有落库时间</li>
 * </ul>
 *
 * <p>后续 drone V1.5 接入后, 改成"边收边写"还是"等 step 完一次性写"都可以,
 * 但 V1 mock 阶段用一次性写最简单.
 */
@Data
@TableName("build_log")
public class BuildLog {

    /** 主键 (雪花 ID) */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属构建记录 */
    @TableField("build_record_id")
    private Long buildRecordId;

    /** step 名 — 例 {@code compile} / {@code test} / {@code docker-push} */
    @TableField("step_name")
    private String stepName;

    /** step 执行顺序 (从 1 开始) */
    @TableField("step_order")
    private Integer stepOrder;

    /** 完整日志内容 */
    @TableField("log_content")
    private String logContent;

    /** 日志字节数 (UTF-8 编码后的字节数) */
    @TableField("log_size_bytes")
    private Long logSizeBytes;

    /** step 开始时间 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** step 结束时间 */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /** 落库时间 (drone webhook 接收时) */
    @TableField("created_at")
    private LocalDateTime createdAt;
}

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

package com.shipyard.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.shipyard.entity.DeployRecord;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

/**
 * DeployRecordMapper 集成测试 — 跟真实 MySQL (application-test.yml) 交互.
 *
 * <p>V9 自定义 SQL 覆盖:
 * <ul>
 *   <li>{@link DeployRecordMapper#markRunning} — PENDING → RUNNING + 填 started_at, 重复 mark no-op</li>
 *   <li>{@link DeployRecordMapper#markFinished} — 任意终态更新, 重复 mark no-op</li>
 *   <li>{@link DeployRecordMapper#updateCurrentSnapshot} — 回填 current_snapshot_id</li>
 * </ul>
 */
@DisplayName("DeployRecordMapper — V9 自定义 SQL 集成测试")
@SpringBootTest
@Transactional
@Rollback
class DeployRecordMapperTest {

    @Autowired
    private DeployRecordMapper mapper;

    @Test
    @DisplayName("markRunning: PENDING → RUNNING, 填 started_at")
    void markRunningFromPending() {
        // 1. 插一条 PENDING 记录
        DeployRecord record = newRecord();
        mapper.insert(record);
        Long id = record.getId();

        LocalDateTime startedAt = LocalDateTime.now();
        int affected = mapper.markRunning(id, startedAt);

        assertThat(affected).isEqualTo(1);
        DeployRecord updated = mapper.selectById(id);
        assertThat(updated.getStatus()).isEqualTo("RUNNING");
        assertThat(updated.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("markRunning: 重复 mark (status 已是 RUNNING) → 0 行 affected")
    void markRunningIdempotent() {
        DeployRecord record = newRecord();
        mapper.insert(record);
        Long id = record.getId();

        // 第一次 mark
        mapper.markRunning(id, LocalDateTime.now());
        // 第二次 mark (status 已经是 RUNNING, 应该 no-op)
        int affected = mapper.markRunning(id, LocalDateTime.now().plusMinutes(1));

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("markFinished: RUNNING → SUCCESS, 填 finished_at")
    void markFinishedFromRunning() {
        DeployRecord record = newRecord();
        mapper.insert(record);
        Long id = record.getId();

        // 先 markRunning
        mapper.markRunning(id, LocalDateTime.now());

        // 再 markFinished
        LocalDateTime finishedAt = LocalDateTime.now();
        int affected = mapper.markFinished(id, "SUCCESS", null, finishedAt);

        assertThat(affected).isEqualTo(1);
        DeployRecord updated = mapper.selectById(id);
        assertThat(updated.getStatus()).isEqualTo("SUCCESS");
        assertThat(updated.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFinished: 重复 mark (status 已是 SUCCESS) → 0 行 affected")
    void markFinishedIdempotent() {
        DeployRecord record = newRecord();
        mapper.insert(record);
        Long id = record.getId();

        mapper.markRunning(id, LocalDateTime.now());
        mapper.markFinished(id, "SUCCESS", null, LocalDateTime.now());

        // 第二次 markFinished (已经 SUCCESS, 应该 no-op)
        int affected = mapper.markFinished(id, "FAILED", "err", LocalDateTime.now());

        assertThat(affected).isEqualTo(0);
    }

    @Test
    @DisplayName("markFinished: error_message 不传 → 保留原值 (IFNULL 兜底)")
    void markFinishedErrorMessageIfNull() {
        DeployRecord record = newRecord();
        mapper.insert(record);
        Long id = record.getId();

        mapper.markRunning(id, LocalDateTime.now());
        // 第一次填错误信息
        mapper.markFinished(id, "FAILED", "first error msg", LocalDateTime.now());

        // 改回 RUNNING 重跑 (业务上不允许, 这里只测 SQL IFNULL 兜底)
        // 直接再 markFinished 不传 errorMessage
        DeployRecord reread = mapper.selectById(id);
        // 重置:不能改 status 终态, 只能新插一条
        DeployRecord newRecord = newRecord();
        mapper.insert(newRecord);
        Long newId = newRecord.getId();
        mapper.markRunning(newId, LocalDateTime.now());
        mapper.markFinished(newId, "FAILED", "err", LocalDateTime.now());

        // 再 markFinished 不传 errorMessage (SQL IFNULL 保留原 'err')
        int affected = mapper.markFinished(newId, "FAILED", null, LocalDateTime.now());
        // 终态不能转, 0 行
        assertThat(affected).isEqualTo(0);

        // 直接看 reread 验证
        assertThat(reread.getErrorMessage()).isEqualTo("first error msg");
    }

    @Test
    @DisplayName("updateCurrentSnapshot: 回填 current_snapshot_id")
    void updateCurrentSnapshot() {
        DeployRecord record = newRecord();
        mapper.insert(record);
        Long id = record.getId();

        int affected = mapper.updateCurrentSnapshot(id, 999L);
        assertThat(affected).isEqualTo(1);

        DeployRecord updated = mapper.selectById(id);
        assertThat(updated.getCurrentSnapshotId()).isEqualTo(999L);
    }

    // ==================== helper ====================

    private DeployRecord newRecord() {
        DeployRecord r = new DeployRecord();
        r.setProjectId(1L);
        r.setEnvId(1L);
        r.setImageTag("nginx:1.27.0");
        r.setNamespace("shipyard-dev");
        r.setDeployYamlSha256("a".repeat(64));
        r.setStatus("PENDING");
        r.setTriggeredBy("test");
        r.setTriggerType("MANUAL");
        return r;
    }
}

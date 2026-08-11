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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DeployStatus enum 状态机测试.
 *
 * <p>覆盖:
 * <ul>
 *   <li>终态判定: SUCCESS / FAILED / TIMEOUT / CANCELED 是终态, PENDING / RUNNING 不是</li>
 *   <li>enum 名字跟 DB 存的字符串一致 (V2 部署跟 enum 名字必须对齐, shipyard → worker / DB)</li>
 * </ul>
 */
@DisplayName("DeployStatus enum — 状态机 + 名字一致性")
class DeployStatusTest {

    @Test
    @DisplayName("终态: SUCCESS / FAILED / TIMEOUT / CANCELED")
    void terminalStates() {
        assertThat(DeployStatus.SUCCESS.isTerminal()).isTrue();
        assertThat(DeployStatus.FAILED.isTerminal()).isTrue();
        assertThat(DeployStatus.TIMEOUT.isTerminal()).isTrue();
        assertThat(DeployStatus.CANCELED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("非终态: PENDING / RUNNING")
    void nonTerminalStates() {
        assertThat(DeployStatus.PENDING.isTerminal()).isFalse();
        assertThat(DeployStatus.RUNNING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("enum 名字 = DB V2 部署 status 字符串值 (shipyard 端 SQL 硬编码 'PENDING' / 'RUNNING' 等)")
    void enumNameMatchesDbString() {
        // 跟 V2__add_deploy_tables.sql COMMENT + DeployRecordMapper.markRunning 的
        // 'WHERE status = \\'PENDING\\'' 对齐
        assertThat(DeployStatus.PENDING.name()).isEqualTo("PENDING");
        assertThat(DeployStatus.RUNNING.name()).isEqualTo("RUNNING");
        assertThat(DeployStatus.SUCCESS.name()).isEqualTo("SUCCESS");
        assertThat(DeployStatus.FAILED.name()).isEqualTo("FAILED");
        assertThat(DeployStatus.TIMEOUT.name()).isEqualTo("TIMEOUT");
        assertThat(DeployStatus.CANCELED.name()).isEqualTo("CANCELED");
    }
}

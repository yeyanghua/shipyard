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

package com.shipyard.worker;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shipyard.worker.entity.Worker;
import com.shipyard.worker.mapper.WorkerMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * WorkerHealthScanner 单元测试 — shipyard 端心跳过期扫描.
 *
 * <p>覆盖:
 * <ul>
 *   <li>没过期 worker: 不调 markOffline</li>
 *   <li>1 个过期 worker: 调 1 次 markOffline + warn log</li>
 *   <li>3 个过期 worker: 调 3 次</li>
 *   <li>markOffline 返回 0 (race condition 已标 offline): 静默跳过</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkerHealthScanner — 30s 扫心跳过期 worker")
class WorkerHealthScannerTest {

    @Mock
    private WorkerMapper workerMapper;

    @InjectMocks
    private WorkerHealthScanner scanner;

    @Test
    @DisplayName("没过期 worker: 不调 markOffline")
    void noStaleWorkers() {
        when(workerMapper.selectStaleOnline(any())).thenReturn(List.of());

        scanner.scanStaleWorkers();

        verify(workerMapper, times(1)).selectStaleOnline(any());
        verify(workerMapper, never()).markOffline(any());
    }

    @Test
    @DisplayName("1 个过期 worker: 调 1 次 markOffline")
    void oneStaleWorker() {
        Worker w = newWorker(1L, 100L, "http://w-1:8888",
                LocalDateTime.now().minusSeconds(120));
        when(workerMapper.selectStaleOnline(any())).thenReturn(List.of(w));
        when(workerMapper.markOffline(1L)).thenReturn(1);

        scanner.scanStaleWorkers();

        verify(workerMapper, times(1)).markOffline(1L);
    }

    @Test
    @DisplayName("3 个过期 worker: 调 3 次 markOffline (跨 env)")
    void multipleStaleWorkers() {
        Worker w1 = newWorker(1L, 10L, "http://w-1:8888",
                LocalDateTime.now().minusSeconds(120));
        Worker w2 = newWorker(2L, 10L, "http://w-2:8888",
                LocalDateTime.now().minusSeconds(200));
        Worker w3 = newWorker(3L, 20L, "http://w-3:8888",
                LocalDateTime.now().minusSeconds(95));
        when(workerMapper.selectStaleOnline(any())).thenReturn(List.of(w1, w2, w3));
        when(workerMapper.markOffline(any())).thenReturn(1);

        scanner.scanStaleWorkers();

        verify(workerMapper, times(1)).markOffline(1L);
        verify(workerMapper, times(1)).markOffline(2L);
        verify(workerMapper, times(1)).markOffline(3L);
    }

    @Test
    @DisplayName("markOffline 返 0 (race condition 已标 offline): 静默跳过, 不抛")
    void markOfflineRaceCondition() {
        Worker w = newWorker(1L, 10L, "http://w-1:8888",
                LocalDateTime.now().minusSeconds(120));
        when(workerMapper.selectStaleOnline(any())).thenReturn(List.of(w));
        when(workerMapper.markOffline(1L)).thenReturn(0);  // race, 已经被标了

        scanner.scanStaleWorkers();

        // 不抛, 静默完成
        verify(workerMapper, times(1)).markOffline(1L);
    }

    // ==================== helper ====================

    private Worker newWorker(Long id, Long envId, String url, LocalDateTime lastHeartbeat) {
        Worker w = new Worker();
        w.setId(id);
        w.setEnvId(envId);
        w.setWorkerUrl(url);
        w.setWorkerTokenHash("a".repeat(64));
        w.setStatus("online");
        w.setHealth("HEALTHY");
        w.setLastHeartbeatAt(lastHeartbeat);
        w.setVersion("dev");
        return w;
    }
}

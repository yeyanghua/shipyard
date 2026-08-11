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

package com.shipyard.worker.selector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shipyard.common.exception.BusinessException;
import com.shipyard.worker.entity.Worker;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WorkerSelector 3 个实现单测 — 选 worker 逻辑必须正确.
 *
 * <p>覆盖:
 * <ul>
 *   <li>FirstAvailableSelector — 永远选 candidates.get(0) (最新心跳)</li>
 *   <li>RandomSelector — 返回的元素 ∈ candidates, 不空</li>
 *   <li>RoundRobinSelector — N 次循环后每个 index 都选到, 计数持久</li>
 *   <li>3 个 selector 空列表都抛 BusinessException(NOT_FOUND)</li>
 * </ul>
 */
@DisplayName("WorkerSelector — 调度策略单测")
class WorkerSelectorTest {

    private List<Worker> candidates;

    @BeforeEach
    void setUp() {
        candidates = new ArrayList<>();
        candidates.add(newWorker(1L, "http://w-1:8888"));
        candidates.add(newWorker(2L, "http://w-2:8888"));
        candidates.add(newWorker(3L, "http://w-3:8888"));
    }

    @Test
    @DisplayName("FirstAvailableSelector: 永远选 candidates.get(0) (最新心跳)")
    void firstAvailableReturnsFirst() {
        FirstAvailableSelector s = new FirstAvailableSelector();
        for (int i = 0; i < 5; i++) {
            Worker picked = s.select(candidates);
            assertThat(picked.getWorkerUrl()).isEqualTo("http://w-1:8888");
        }
    }

    @Test
    @DisplayName("RandomSelector: 返回的元素 ∈ candidates, 不空")
    void randomReturnsInCandidates() {
        RandomSelector s = new RandomSelector();
        for (int i = 0; i < 50; i++) {
            Worker picked = s.select(candidates);
            assertThat(picked).isIn(candidates.toArray());
        }
    }

    @Test
    @DisplayName("RandomSelector: 跑 1000 次, 3 个 worker 分布大体均匀 (每个 ≥ 200)")
    void randomDistributionEven() {
        RandomSelector s = new RandomSelector();
        int[] hits = new int[3];
        for (int i = 0; i < 1000; i++) {
            Worker picked = s.select(candidates);
            int idx = candidates.indexOf(picked);
            hits[idx]++;
        }
        // 1/3 平均, 容差 100 次, 简单 sanity
        assertThat(hits[0]).isGreaterThanOrEqualTo(200);
        assertThat(hits[1]).isGreaterThanOrEqualTo(200);
        assertThat(hits[2]).isGreaterThanOrEqualTo(200);
    }

    @Test
    @DisplayName("RoundRobinSelector: N 次循环, 每个 index 都被选到 (size 次)")
    void roundRobinCyclesAll() {
        RoundRobinSelector s = new RoundRobinSelector();
        int size = candidates.size();

        // 第一个 select: counter 初始 -1, (last+1)%3 = 0 → candidates.get(0) = w-1
        // 第二个: counter=0, (0+1)%3=1 → w-2
        // 第三个: counter=1, (1+1)%3=2 → w-3
        // 第四个: counter=2, (2+1)%3=0 → w-1 (循环)
        String[] expected = {"http://w-1:8888", "http://w-2:8888", "http://w-3:8888", "http://w-1:8888"};
        for (int i = 0; i < expected.length; i++) {
            Worker picked = s.select(candidates);
            assertThat(picked.getWorkerUrl())
                    .as("第 %d 次 select 应该是 %s", i + 1, expected[i])
                    .isEqualTo(expected[i]);
        }
    }

    @Test
    @DisplayName("RoundRobinSelector: 跑 12 次 (4 轮), 每个 worker 命中 4 次")
    void roundRobinEqualDistribution() {
        RoundRobinSelector s = new RoundRobinSelector();
        int[] hits = new int[3];
        for (int i = 0; i < 12; i++) {
            Worker picked = s.select(candidates);
            hits[candidates.indexOf(picked)]++;
        }
        assertThat(hits[0]).isEqualTo(4);
        assertThat(hits[1]).isEqualTo(4);
        assertThat(hits[2]).isEqualTo(4);
    }

    @Test
    @DisplayName("RoundRobinSelector: size 变了 (新增 worker), modulo 算出来仍合法")
    void roundRobinHandlesSizeChange() {
        RoundRobinSelector s = new RoundRobinSelector();

        // 3 个 worker 跑 2 次: w-1, w-2
        s.select(candidates);
        s.select(candidates);

        // size 变 4 (新增 w-4)
        candidates.add(newWorker(4L, "http://w-4:8888"));

        // 下次应该选 index=2 (w-3), 因为 (1+1)%4=2
        Worker picked = s.select(candidates);
        assertThat(picked.getWorkerUrl()).isEqualTo("http://w-3:8888");
    }

    @Test
    @DisplayName("RoundRobinSelector: resetCounter 清空后, 重新从 w-1 开始")
    void roundRobinReset() {
        RoundRobinSelector s = new RoundRobinSelector();
        s.select(candidates);
        s.select(candidates);
        s.select(candidates);

        s.resetCounter(1L);
        Worker picked = s.select(candidates);
        assertThat(picked.getWorkerUrl()).isEqualTo("http://w-1:8888");
    }

    @Test
    @DisplayName("3 个 selector 空列表都抛 BusinessException(NOT_FOUND)")
    void emptyCandidatesThrows() {
        assertThatThrownBy(() -> new FirstAvailableSelector().select(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可用的 worker");
        assertThatThrownBy(() -> new RandomSelector().select(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可用的 worker");
        assertThatThrownBy(() -> new RoundRobinSelector().select(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可用的 worker");
    }

    @Test
    @DisplayName("size=1 时所有 selector 直接返回第 1 个 (RR 也走快路径)")
    void singleCandidateShortCircuit() {
        Worker single = newWorker(99L, "http://solo:8888");
        assertThat(new FirstAvailableSelector().select(List.of(single)).getId()).isEqualTo(99L);
        assertThat(new RandomSelector().select(List.of(single)).getId()).isEqualTo(99L);
        assertThat(new RoundRobinSelector().select(List.of(single)).getId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("SelectorStrategy.index 把 list 转成 strategy → selector map")
    void strategyIndex() {
        List<WorkerSelector> selectors = List.of(
                new FirstAvailableSelector(),
                new RandomSelector(),
                new RoundRobinSelector()
        );
        var index = SelectorStrategy.index(selectors);
        assertThat(index).hasSize(3);
        assertThat(index.get(SelectorStrategy.FIRST_AVAILABLE).name()).isEqualTo("FIRST_AVAILABLE");
        assertThat(index.get(SelectorStrategy.RANDOM).name()).isEqualTo("RANDOM");
        assertThat(index.get(SelectorStrategy.ROUND_ROBIN).name()).isEqualTo("ROUND_ROBIN");
    }

    // ==================== helper ====================

    private Worker newWorker(Long id, String url) {
        Worker w = new Worker();
        w.setId(id);
        w.setEnvId(1L);
        w.setWorkerUrl(url);
        w.setWorkerTokenHash("a".repeat(64));
        w.setStatus("online");
        w.setLastHeartbeatAt(LocalDateTime.now());
        w.setVersion("dev");
        return w;
    }
}

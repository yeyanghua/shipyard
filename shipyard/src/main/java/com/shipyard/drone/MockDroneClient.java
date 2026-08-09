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

package com.shipyard.drone;

import com.shipyard.common.enums.BuildStatus;
import com.shipyard.config.DroneProperties;
import com.shipyard.entity.BuildRecord;
import com.shipyard.mapper.BuildLogMapper;
import com.shipyard.mapper.BuildRecordMapper;
import com.shipyard.service.BuildService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock drone 客户端 — V1 demo 用, 本地异步模拟 drone 行为.
 *
 * <p>行为:
 * <ol>
 *   <li>{@link #triggerBuild(DroneBuildRequest)} 立即返回 droneBuildId, 提交 {@link #runMockBuild} 异步任务</li>
 *   <li>异步任务: 调 {@code BuildRecordMapper.markRunning} → PENDING→RUNNING
 *       → 模拟 3 个 step (compile/test/docker-push) → 落 {@code build_log} → {@code markFinished(SUCCESS)}</li>
 *   <li>{@link #cancelBuild(String)} 标记 cancelled 标志位, 异步任务每 step 前检查 → 主动结束 + 标 CANCELED</li>
 * </ol>
 *
 * <p>激活条件: {@code shipyard.drone.mock-enabled=true} (V1 demo 默认).
 * V1.5 接真实 drone 时改 {@code false}, 启动 {@code RealDroneClient}.
 *
 * <p><b>关键设计</b>: shipyard → drone 走 interface, drone → shipyard 走 webhook
 * (V1.5 才有). V1 mock 没有外部 webhook, 异步任务里直接调 {@link BuildService} 内部方法
 * (绕过 HTTP 层) — 业务代码不区分内部/外部, V1.5 切换零改动.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "shipyard.drone.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockDroneClient implements DroneClient {

    private final BuildRecordMapper buildRecordMapper;
    private final BuildLogMapper buildLogMapper;
    /**
     * {@link Lazy} 解循环依赖: BuildServiceImpl 注入 DroneClient (即 MockDroneClient),
     * MockDroneClient 又要调 BuildService 内部方法. 用 lazy proxy 推迟到真正调方法时再注入.
     */
    private final BuildService buildService;
    private final DroneProperties droneProperties;

    public MockDroneClient(BuildRecordMapper buildRecordMapper,
                            BuildLogMapper buildLogMapper,
                            @Lazy BuildService buildService,
                            DroneProperties droneProperties) {
        this.buildRecordMapper = buildRecordMapper;
        this.buildLogMapper = buildLogMapper;
        this.buildService = buildService;
        this.droneProperties = droneProperties;
    }

    /** 取消标志 — droneBuildId → AtomicBoolean */
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    /**
     * Mock build 异步执行器 — Java 21 虚拟线程池.
     *
     * <p><b>不用 Spring {@code @Async}</b> 原因:
     * <ol>
     *   <li>{@code @Async} 不支持 String 返回 (triggerBuild 要返 droneBuildId)</li>
     *   <li>{@code MockDroneClient implements DroneClient}, Spring 用 JDK 动态代理
     *       (proxy implements interface list, 实际类型是 {@code jdk.proxy2.$Proxy}),
     *       self-injection 拿到 proxy 类型不匹配</li>
     * </ol>
     * 直接用 {@link Executors#newVirtualThreadPerTaskExecutor()} 提交 {@link Runnable} —
     * 跟 Spring 虚拟线程配置一致, 简单可靠, 不依赖 proxy 行为.
     */
    private static final ExecutorService VIRTUAL_EXECUTOR =
        Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public String triggerBuild(DroneBuildRequest request) {
        log.info("[MockDrone] triggerBuild droneBuildId={} projectId={} commit={} envVars={}",
            request.droneBuildId(), request.projectId(), request.commitSha(),
            request.envVars() == null ? "null" : request.envVars().keySet());
        cancelFlags.put(request.droneBuildId(), new AtomicBoolean(false));
        // 提交到虚拟线程池, 不阻塞 controller
        VIRTUAL_EXECUTOR.submit(() -> runMockBuildImpl(request));
        return request.droneBuildId();
    }

    @Override
    public void cancelBuild(String droneBuildId) {
        log.info("[MockDrone] cancelBuild droneBuildId={}", droneBuildId);
        AtomicBoolean flag = cancelFlags.get(droneBuildId);
        if (flag != null) {
            flag.set(true);
        }
    }

    /**
     * 异步执行 mock build — 由 {@link #triggerBuild} 提交到虚拟线程池.
     *
     * <p>3 个 step: compile → test → docker-push, 每 step {@code mock-step-delay-ms},
     * 每个 step 完生成 5-8 行假日志, 落 {@code build_log}.
     */
    public void runMockBuildImpl(DroneBuildRequest request) {
        String droneBuildId = request.droneBuildId();
        log.info("[MockDrone] runMockBuild start droneBuildId={}", droneBuildId);

        // 1. PENDING → RUNNING
        LocalDateTime startedAt = LocalDateTime.now();
        BuildRecord record = buildRecordMapper.selectByDroneBuildId(droneBuildId);
        if (record == null) {
            log.error("[MockDrone] build record not found for droneBuildId={}", droneBuildId);
            return;
        }
        buildRecordMapper.markRunning(record.getId(), startedAt);

        // 2. 模拟 3 个 step
        List<MockStep> steps = List.of(
            new MockStep(1, "compile",
                "[\"INFO] Scanning for projects...\","
                + "\"\\\"[INFO] Building shipyard demo 1.0-SNAPSHOT\\\",\""
                + "\"\\\"[INFO] BUILD SUCCESS in 1.2s\\\",\""
                + "\"\\\"[INFO] 2 actionable tasks: 2 executed\\\"\"]"),
            new MockStep(2, "test",
                "[\"INFO] Running com.shipyard.demo.*Test\","
                + "\"\\\"[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0\\\",\""
                + "\"\\\"[INFO] BUILD SUCCESS\\\"\"]"),
            new MockStep(3, "docker-push",
                "[\"INFO] docker build -t shipyard/demo:mock-... .\","
                + "\"\\\"[INFO] Sending build context to Docker daemon\\\",\""
                + "\"\\\"[INFO] Successfully built abc1234\\\",\""
                + "\"\\\"[INFO] Successfully tagged shipyard/demo:mock-abc1234\\\",\""
                + "\"\\\"[INFO] (mock) push to harbor skipped\\\"]")
        );

        for (MockStep step : steps) {
            // 检查取消
            if (cancelFlags.get(droneBuildId).get()) {
                log.info("[MockDrone] build canceled at step {} droneBuildId={}", step.name, droneBuildId);
                buildService.markBuildFinished(record.getId(), BuildStatus.CANCELED.name(),
                    null, null, LocalDateTime.now());
                cancelFlags.remove(droneBuildId);
                return;
            }

            // 模拟 step 执行
            LocalDateTime stepStart = LocalDateTime.now();
            sleep(droneProperties.getMockStepDelayMs());
            LocalDateTime stepEnd = LocalDateTime.now();

            // 落 build_log
            buildService.saveStepLog(record.getId(), step.order, step.name,
                step.logContent, stepStart, stepEnd);
            log.info("[MockDrone] step {} done droneBuildId={}", step.name, droneBuildId);
        }

        // 3. 终态 SUCCESS + 标 log_persisted
        String imageTag = "mock-" + request.commitSha().substring(0, Math.min(7, request.commitSha().length()));
        String harborUrl = "harbor.shipyard.local/shipyard/" + request.droneBuildId() + ":" + imageTag;
        buildService.markBuildFinished(record.getId(), BuildStatus.SUCCESS.name(),
            imageTag, harborUrl, LocalDateTime.now());
        buildRecordMapper.markLogPersisted(record.getId());

        cancelFlags.remove(droneBuildId);
        log.info("[MockDrone] runMockBuild SUCCESS droneBuildId={} imageTag={}",
            droneBuildId, imageTag);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 内部 step 数据 — V1 mock hard-coded, V1.5 改成从 pipeline_template 渲染 */
    private record MockStep(int order, String name, String logContent) {}
}

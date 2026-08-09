package com.shipyard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * shipyard 平台后端入口.
 *
 * <p>Spring Boot 3.2 + Java 21 LTS + 虚拟线程 (Project Loom).
 *
 * <p>V1 范围:横向 demo 优先 — 一个 Java 应用端到端可演示.具体见
 * {@code docs/superpowers/specs/2026-08-08-platform-design.md}.
 *
 * <p>{@link EnableAsync} — 启动 {@code @Async} 异步方法 (M5 2 mock drone 异步跑 build 用).
 * 默认 executor 是 Spring 6.1+ 的 {@code SimpleAsyncTaskExecutor} + virtualThreads (由
 * {@code VirtualThreadConfig.virtualThreadTaskExecutor} 显式提供).
 */
@SpringBootApplication
@MapperScan("com.shipyard.**.mapper")
@EnableAsync
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

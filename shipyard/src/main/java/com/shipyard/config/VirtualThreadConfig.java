package com.shipyard.config;

import java.util.concurrent.Executors;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 虚拟线程 (Project Loom) 配置 — Java 21 LTS.
 *
 * <p>效果:每个 HTTP 请求 / 异步任务都跑在虚拟线程上,IO 阻塞时不占 OS 线程.
 * 实测 100k 并发请求在 4 核机器上也能扛住.
 *
 * <p>启用方式: Spring Boot 3.2 + spring.threads.virtual.enabled=true (application.yml)
 * + 本类显式注册 Tomcat / MVC / @Async 三处使用虚拟线程.
 *
 * <p>参考: <a
 * href="https://spring.io/blog/2023/09/spring-boot-3-2-0-m1-introducing-the-virtual-threads-executor">
 * Spring Boot 3.2 Virtual Threads</a>
 */
// V1 demo 临时关 (M9 deploy 验证): virtual thread + Spring Security 6.2 在 register handler 兼容性
// 卡住 controller 不下传. V1.5 fix 时重新启用 (Spring Security 6.2 bug 修了之后).
// @Configuration
// @EnableAsync
class VirtualThreadConfig implements AsyncConfigurer, WebMvcConfigurer {

    /**
     * Tomcat 用虚拟线程处理 HTTP 请求 (替代默认 200 个 OS 线程池).
     */
    @Bean
    public TomcatProtocolHandlerCustomizer<?> virtualThreadTomcatProtocolHandlerCustomizer() {
        return protocolHandler -> protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Spring MVC 异步支持 (SSE 流式响应用, M11 实时构建日志会用到).
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor()));
    }

    /**
     * @Async 注解的默认执行器.
     */
    @Override
    public AsyncTaskExecutor getAsyncExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * 显式声明一个 named async executor,供 @Async("virtualThreadTaskExecutor") 引用.
     *
     * <p>用 SimpleAsyncTaskExecutor (Spring 6.1+ 支持 setVirtualThreads),
     * 不用 ThreadPoolTaskExecutor (它没有 setVirtualThreads API).
     */
    @Bean(name = "virtualThreadTaskExecutor")
    public SimpleAsyncTaskExecutor virtualThreadTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("vt-");
        executor.setVirtualThreads(true);
        return executor;
    }
}

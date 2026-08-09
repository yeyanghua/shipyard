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

package com.shipyard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * shipyard platform - master backend 入口.
 *
 * <p>这是 shipyard 平台后端的 Spring Boot 启动类. 整个平台的设计是:
 * <ul>
 *   <li>master = 这个 Spring Boot 应用,统一门面</li>
 *   <li>drone CI = 后端构建引擎(master 调它)</li>
 *   <li>Harbor = 后端镜像仓库(drone push, worker pull)</li>
 *   <li>worker = 每个环境的部署代理(Go 写的,部署在 k8s 集群里)</li>
 * </ul>
 *
 * <p><b>技术栈关键点</b>:
 * <ul>
 *   <li>Java 21 LTS + Spring Boot 3.2.5</li>
 *   <li>虚拟线程(spring.threads.virtual.enabled=true 见 application.yml)</li>
 *   <li>MyBatis-Plus ORM(Flyway 跑迁移)</li>
 *   <li>Spring Security + JWT(Sa-Token 没选,V1 走白名单模式,见 SecurityConfig)</li>
 *   <li>Redis 做缓存 + 分布式锁</li>
 * </ul>
 *
 * <p><b>启动顺序</b>:
 * <pre>
 *   mvn spring-boot:run
 *   # 或
 *   make master-dev
 * </pre>
 * 启动后:
 * <ul>
 *   <li>Flyway 自动跑 db/migration/V1__init.sql (13 张表)</li>
 *   <li>Tomcat 用虚拟线程池</li>
 *   <li>/actuator/health 返回 UP 就算成功</li>
 * </ul>
 *
 * @see com.shipyard.config.SecurityConfig
 * @see com.shipyard.crypto.Encrypter
 * @see <a href="https://github.com/yeyanghua/shipyard">shipyard GitHub</a>
 */
@SpringBootApplication
@MapperScan("com.shipyard.**.mapper")
@EnableAsync
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

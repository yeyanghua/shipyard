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

package com.shipyard.config;

import com.shipyard.entity.DockerfileTemplate;
import com.shipyard.mapper.DockerfileTemplateMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 启动时幂等插入 5 套内置 Dockerfile 模板.
 *
 * <p>幂等策略: 按 {@code name} 检查, 已存在就跳过, 避免重复启动时重复插.
 *
 * <p>5 套模板 (在 {@code classpath:dockerfile-templates/}):
 * <ul>
 *   <li>java_maven_jdk21 — Temurin 21 JRE + Maven 多阶段</li>
 *   <li>java_gradle_jdk21 — Temurin 21 JRE + Gradle 多阶段</li>
 *   <li>node_pnpm_20 — Node 20 + pnpm 多阶段</li>
 *   <li>python_poetry_312 — Python 3.12 + Poetry 多阶段</li>
 *   <li>generic_alpine — 兜底, 适用 projectType=other</li>
 * </ul>
 *
 * <p>V1.5 升级: 支持用户自定义模板 (is_builtin=0, V1 全 1).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerfileTemplateInitializer implements ApplicationRunner {

    private final DockerfileTemplateMapper templateMapper;

    private record BuiltinTemplate(
            String name,
            String displayName,
            String language,
            String buildTool,
            String resourcePath,
            String variableSchema) {}

    private static final List<BuiltinTemplate> BUILTIN = List.of(
            new BuiltinTemplate(
                    "java_maven_jdk21",
                    "Java 21 + Maven (多阶段)",
                    "java",
                    "maven",
                    "dockerfile-templates/java_maven_jdk21.template",
                    "[{\"key\":\"jarName\",\"type\":\"string\",\"default\":\"app.jar\",\"description\":\"构建产物的 jar 文件名\",\"required\":true},"
                            + "{\"key\":\"mainClassArgs\",\"type\":\"string\",\"default\":\"\",\"description\":\"main 方法参数 (例: --server.port=8080)\",\"required\":false},"
                            + "{\"key\":\"port\",\"type\":\"int\",\"default\":\"8080\",\"description\":\"应用监听端口\",\"required\":true}]"),
            new BuiltinTemplate(
                    "java_gradle_jdk21",
                    "Java 21 + Gradle (多阶段)",
                    "java",
                    "gradle",
                    "dockerfile-templates/java_gradle_jdk21.template",
                    "[{\"key\":\"jarName\",\"type\":\"string\",\"default\":\"app.jar\",\"description\":\"构建产物的 jar 文件名\",\"required\":true},"
                            + "{\"key\":\"mainClassArgs\",\"type\":\"string\",\"default\":\"\",\"description\":\"main 方法参数\",\"required\":false},"
                            + "{\"key\":\"port\",\"type\":\"int\",\"default\":\"8080\",\"description\":\"应用监听端口\",\"required\":true}]"),
            new BuiltinTemplate(
                    "node_pnpm_20",
                    "Node 20 + pnpm (多阶段)",
                    "node",
                    "pnpm",
                    "dockerfile-templates/node_pnpm_20.template",
                    "[{\"key\":\"distDir\",\"type\":\"string\",\"default\":\"dist\",\"description\":\"前端 build 产物目录 (Vue/React dist, Next.js .next)\",\"required\":true},"
                            + "{\"key\":\"port\",\"type\":\"int\",\"default\":\"3000\",\"description\":\"应用监听端口\",\"required\":true}]"),
            new BuiltinTemplate(
                    "python_poetry_312",
                    "Python 3.12 + Poetry (多阶段)",
                    "python",
                    "poetry",
                    "dockerfile-templates/python_poetry_312.template",
                    "[{\"key\":\"port\",\"type\":\"int\",\"default\":\"8000\",\"description\":\"应用监听端口 (FastAPI/Django/Flask)\",\"required\":true}]"),
            new BuiltinTemplate(
                    "generic_alpine",
                    "Generic Alpine (兜底)",
                    "other",
                    "other",
                    "dockerfile-templates/generic_alpine.template",
                    "[{\"key\":\"port\",\"type\":\"int\",\"default\":\"8080\",\"description\":\"应用监听端口\",\"required\":false}]"));

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int inserted = 0;
        int skipped = 0;
        for (BuiltinTemplate bt : BUILTIN) {
            if (templateMapper.selectByName(bt.name()) != null) {
                skipped++;
                continue;
            }
            Resource resource = new ClassPathResource(bt.resourcePath());
            if (!resource.exists()) {
                log.warn("[DockerfileTemplateInitializer] 资源文件不存在: {}, 跳过", bt.resourcePath());
                continue;
            }
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            DockerfileTemplate t = new DockerfileTemplate();
            t.setName(bt.name());
            t.setDisplayName(bt.displayName());
            t.setLanguage(bt.language());
            t.setBuildTool(bt.buildTool());
            t.setTemplateContent(content);
            t.setVariableSchema(bt.variableSchema());
            t.setVersion(1);
            t.setIsBuiltin(1);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            templateMapper.insert(t);
            inserted++;
            log.info("[DockerfileTemplateInitializer] 插入内置模板: {}", bt.name());
        }
        log.info("[DockerfileTemplateInitializer] Dockerfile 模板初始化完成: 新增 {} 套, 跳过 {} 套", inserted, skipped);
    }
}

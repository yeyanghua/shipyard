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

package com.shipyard.service;

import com.shipyard.common.exception.BusinessException;
import com.shipyard.common.exception.ErrorCode;
import com.shipyard.entity.Env;
import com.shipyard.entity.PipelineTemplate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 部署 yaml 模板渲染器 — 简单模式唯一入口 (V1).
 *
 * <p>输入:
 * <ul>
 *   <li>{@link Env} (拿 envName 填 namespace_pattern 里的 {env_name})</li>
 *   <li>{@link PipelineTemplate} (拿 containerPort / replicas / namespacePattern)</li>
 *   <li>{@code resourceName} — K8s 资源名 (格式 {@code <project>-<env>}, 例 {@code myapp-dev})</li>
 *   <li>{@code image} — 完整镜像 tag (例 {@code nginx:1.27.0})</li>
 *   <li>{@code envVars} — 注入到 Pod 的 env (K8s env 数组, V1 简版直接 plain env 不走 configMap)</li>
 * </ul>
 *
 * <p>输出: 多 resource K8s yaml 字符串 (Deployment + Service, 用 {@code ---} 分隔)
 *
 * <p>V1 渲染策略:
 * <ul>
 *   <li>简单字符串拼接 + Java 19+ text blocks (3 引号) — 不引外部模板引擎 (freemarker / velocity)</li>
 *   <li>envVars 按 name 字母序排 — 保证 yaml 输出稳定, 同一输入永远出同一 sha256</li>
 *   <li>namespace_pattern 替换 {@code {env_name}} → env.name (不引 handlebars 之类)</li>
 * </ul>
 *
 * <p>不引模板引擎的理由 (V1.5+ 再考虑):
 * <ol>
 *   <li>零依赖, shipyard 后端启动快</li>
 *   <li>用户不能改模板 (V1 只"简单模式"填字段, 高级模式 V1.5), 模板 shipyard 代码管</li>
 *   <li>V1.5 高级模式让用户改 yaml 时, shipyard 也不解析 — 直接拿用户字符串</li>
 * </ol>
 */
@Component
public class DeployTemplateRenderer {

    /**
     * 渲染 K8s Deployment + Service 二合一 yaml (--- 分隔).
     *
     * @param env env 元数据, 主要拿 env.name 填 namespace
     * @param template pipeline_template 拿 containerPort / replicas / namespacePattern
     * @param resourceName 资源名 (例 myapp-dev, K8s 资源名规则: 小写字母数字-, 最多 63)
     * @param image 完整镜像 tag
     * @param envVars 注入到 Pod 容器的 env (env key 必须符合 K8s env name 规则)
     * @return 多 resource K8s yaml 字符串
     * @throws BusinessException pipeline_template.containerPort 必填字段为 NULL
     */
    public String render(
            Env env, PipelineTemplate template, String resourceName, String image, Map<String, String> envVars) {

        // 1. 校验
        if (template.getContainerPort() == null) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "pipeline_template.containerPort 不能为空 (projectId=" + template.getProjectId() + " version="
                            + template.getVersion() + "), 请先在 pipeline 配置主端口");
        }
        if (resourceName == null || resourceName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "resourceName 不能为空");
        }
        if (image == null || image.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "image 不能为空");
        }

        // 2. 渲染 namespace
        String namespace = renderNamespace(template.getNamespacePattern(), env.getName());

        // 3. 渲染 env 数组 (字母序排, 稳定 sha256)
        String envBlock = renderEnvBlock(envVars);

        // 4. 渲染整段 yaml (Deployment + Service)
        return String.format(
                """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: %s
                  namespace: %s
                  labels:
                    app: %s
                    managed-by: shipyard
                spec:
                  replicas: %d
                  selector:
                    matchLabels:
                      app: %s
                  template:
                    metadata:
                      labels:
                        app: %s
                    spec:
                      containers:
                        - name: app
                          image: %s
                          imagePullPolicy: IfNotPresent
                          ports:
                            - containerPort: %d
                          env:
                %s
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  name: %s
                  namespace: %s
                  labels:
                    app: %s
                    managed-by: shipyard
                spec:
                  selector:
                    app: %s
                  ports:
                    - port: 80
                      targetPort: %d
                      protocol: TCP
                      name: http
                  type: ClusterIP
                """,
                resourceName,
                namespace,
                resourceName,
                template.getReplicas(),
                resourceName,
                resourceName,
                image,
                template.getContainerPort(),
                envBlock,
                resourceName,
                namespace,
                resourceName,
                resourceName,
                template.getContainerPort());
    }

    /**
     * 把 {@code shipyard-{env_name}} 替换成 {@code shipyard-dev} 这种.
     *
     * <p>仅支持单个 placeholder {env_name}, 拼错模板 / 模板包含其他 placeholder 时不替换 (原样返回).
     * V1.5 考虑用 handlebars, V1 简版硬编码.
     */
    public String renderNamespace(String pattern, String envName) {
        if (pattern == null || pattern.isBlank()) {
            return "shipyard-" + envName; // fallback 到默认
        }
        return pattern.replace("{env_name}", envName);
    }

    /**
     * 渲染 K8s env 数组 — 字母序排保证输出稳定.
     *
     * <p>输入 envVars = null 或 empty → 返 "  []" 占位.
     * 输入有值 → 每行 {@code            - name: XXX\n              value: YYY}.
     */
    String renderEnvBlock(Map<String, String> envVars) {
        if (envVars == null || envVars.isEmpty()) {
            return "            []";
        }
        // 字母序排 (LinkedHashMap 保留 insertion order, 这里排完再 put)
        Map<String, String> sorted = new LinkedHashMap<>();
        envVars.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sorted.put(e.getKey(), e.getValue() == null ? "" : e.getValue()));

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            sb.append("            - name: ")
                    .append(e.getKey())
                    .append("\n")
                    .append("              value: \"")
                    .append(escapeYamlValue(e.getValue()))
                    .append("\"\n");
        }
        return sb.toString();
    }

    /**
     * yaml 字符串值转义 — 简单版本, 只处理双引号.
     *
     * <p>V1 简版 (env vars 业务值通常没有特殊字符), V1.5 考虑:
     * <ul>
     *   <li>单引号 / 反斜杠 / 换行 / tab / 控制字符</li>
     *   <li>用 SnakeYAML 库的转义函数</li>
     * </ul>
     */
    String escapeYamlValue(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

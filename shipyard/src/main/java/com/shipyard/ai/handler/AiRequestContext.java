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

package com.shipyard.ai.handler;

import com.shipyard.entity.Project;
import com.shipyard.entity.ProjectEnv;
import java.util.Map;

/**
 * AI 调用上下文 — 业务侧传进来的"全部信息", handler 自己挑用.
 *
 * <p>设计原则: <b>不绑死字段</b> — 3 个 capability 用到的 context 字段不一样, 放一个
 * 大 record 里, handler 取自己关心的. 业务字段 type-safe, 扩展字段 (例 build log 全文)
 * 走 {@code extras} 兜底.
 *
 * <p>字段分 3 档:
 * <ul>
 *   <li><b>core</b> — 几乎每个 capability 都会用: userId / projectId / project</li>
 *   <li><b>env</b> — 环境相关 (例: build log / env id), 不一定每个 capability 用</li>
 *   <li><b>extras</b> — capability 特有的任意 Map, 例 diagnosis 的 buildLog, decision 的 buildHistory</li>
 * </ul>
 *
 * <p>用 builder 拼装:
 * <pre>{@code
 * AiRequestContext ctx = AiRequestContext.builder()
 *     .userId("alice")
 *     .project(project)
 *     .env(env)
 *     .put("buildLog", "...log text...")
 *     .build();
 * }</pre>
 */
public record AiRequestContext(
        String userId, Long projectId, Project project, ProjectEnv env, Map<String, Object> extras) {

    /**
     * Builder — 业务代码不用每次都填 5 个字段.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从 extras 拿字段 (类型转换异常抛 {@code IllegalStateException}).
     *
     * <p>handler 用法: {@code String log = ctx.require("buildLog", String.class);}
     */
    @SuppressWarnings("unchecked")
    public <T> T require(String key, Class<T> type) {
        Object v = extras == null ? null : extras.get(key);
        if (v == null) {
            throw new IllegalStateException("extras 缺少必填字段: " + key);
        }
        if (!type.isInstance(v)) {
            throw new IllegalStateException("extras 字段 " + key + " 类型错误, 期望 " + type.getSimpleName() + " 实际 "
                    + v.getClass().getSimpleName());
        }
        return (T) v;
    }

    /**
     * 从 extras 拿字段, 缺省返 null (不抛).
     */
    @SuppressWarnings("unchecked")
    public <T> T optional(String key, Class<T> type) {
        Object v = extras == null ? null : extras.get(key);
        if (v == null) {
            return null;
        }
        if (!type.isInstance(v)) {
            throw new IllegalStateException("extras 字段 " + key + " 类型错误, 期望 " + type.getSimpleName() + " 实际 "
                    + v.getClass().getSimpleName());
        }
        return (T) v;
    }

    public static class Builder {
        private String userId;
        private Long projectId;
        private Project project;
        private ProjectEnv env;
        private final Map<String, Object> extras = new java.util.HashMap<>();

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder projectId(Long projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder project(Project project) {
            this.project = project;
            this.projectId = (project != null) ? project.getId() : this.projectId;
            return this;
        }

        public Builder env(ProjectEnv env) {
            this.env = env;
            return this;
        }

        public Builder put(String key, Object value) {
            this.extras.put(key, value);
            return this;
        }

        public AiRequestContext build() {
            return new AiRequestContext(userId, projectId, project, env, Map.copyOf(extras));
        }
    }
}

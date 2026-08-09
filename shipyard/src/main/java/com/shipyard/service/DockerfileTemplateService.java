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

import com.shipyard.entity.DockerfileTemplate;

import java.util.List;
import java.util.Map;

/**
 * Dockerfile 模板服务 — 列模板 + 按 name 查 + 渲染.
 *
 * <p>渲染: 简单 {@code ${var}} 字符串替换 (M1.5 可换 Mustache/Handlebars 之类).
 * <p>V1 内置 5 套模板, 启动时 {@link com.shipyard.config.DockerfileTemplateInitializer} 幂等插入.
 */
public interface DockerfileTemplateService {

    /** 列所有可用模板 (按 language, build_tool 排序) */
    List<DockerfileTemplate> listAll();

    /** 按 name 查 (name 是唯一 key) */
    DockerfileTemplate getByName(String name);

    /** 按 id 查 */
    DockerfileTemplate get(Long id);

    /**
     * 渲染模板: 把 {@code ${key}} 替换成 vars 里对应值.
     *
     * <p>规则:
     * <ul>
     *   <li>vars 里没提供的 key, 替换成空字符串 (不报错)</li>
     *   <li>空值变量保留空 (不写默认值, 避免静默错误)</li>
     * </ul>
     *
     * @param template 已查好的 template entity
     * @param vars 变量值 (key=变量名, value=填入值)
     * @return 渲染后的 Dockerfile 内容
     */
    String render(DockerfileTemplate template, Map<String, String> vars);
}

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

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局序列化配置 — 把 {@code Long} 序列化为 String, 避免 JS 精度丢失.
 *
 * <h2>问题背景</h2>
 * <p>MyBatis-Plus 雪花算法生成的 ID 是 {@code Long} (19 位, 例 {@code 2086275475138842626}).
 * JS 的 {@code Number} 最大安全整数是 {@code Number.MAX_SAFE_INTEGER = 2^53 - 1} (16 位),
 * 19 位数字超过这个范围, 末几位被 round 掉, 浏览器拿到 "2086275475138842600" (差 26).
 * <br>→ 前端用这个截断的 ID 调 API (例 {@code GET /api/envs/{id}}), 后端查不到 → "资源不存在".
 *
 * <h2>修法</h2>
 * <p>所有 {@code Long} / {@code long} 字段在 JSON 响应里都序列化为 String.
 * 前端用 {@code id: string} 接收, URL 拼接不受影响 (URL 里 string 合法).
 *
 * <p>影响范围: 所有 entity (Project/Env/BuildRecord/BuildLog/EnvVariable/etc.) 的 Long 字段
 * + DTO (PageResponse.records[].id) 都变 String.
 *
 * <p><b>注</b>: 不影响数据库 (DB 仍存 BIGINT), 不影响 Java 代码 (Long 仍是 Long),
 * 只影响 JSON 序列化层. 这是 shipyard V1 跟前端联调的关键修复.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longAsStringCustomizer() {
        return builder -> {
            // Long 类型 (大写 L) + long 基本类型 — 三种都覆盖
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
            builder.serializerByType(long.class, ToStringSerializer.instance);
        };
    }
}

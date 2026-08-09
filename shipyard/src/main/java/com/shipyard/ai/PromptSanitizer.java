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

package com.shipyard.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 脱敏工具 — 入 {@code ai_interaction.input_prompt} 之前把 secret 字段 value 替换成 {@code ***}.
 *
 * <p>为啥要脱敏: 流水表是不删除的, secret 一旦落库就等于泄露, 哪怕 DB 加密备份也会被恢复.
 * 脱敏的 scope:
 * <ul>
 *   <li>环境变量值 (例 {@code HARBOR_PASSWORD=xxx})</li>
 *   <li>JSON / YAML 字段 value (例 {@code "password": "xxx"} / {@code apiKey: xxx})</li>
 * </ul>
 *
 * <p>V1 简化: 只覆盖常见关键字 + value 模式. V1.5 可以加更严格的 (例 base64 检测, JWT 检测).
 *
 * <p>关键字白名单: password / secret / token / api_key / apiKey / apikey / private_key / accessKey
 *
 * <p>匹配模式: {@code (keyword)\s*[:=]\s*["']?([^"'\s,;}{]+)["']?}  -> "$1: ***"}
 */
public final class PromptSanitizer {

    private static final Pattern SENSITIVE = Pattern.compile(
            // group(1) = prefix + keyword (整体保留, 例 "HARBOR_PASSWORD" 完整保留)
            // 前面允许任意 \w (兼容 HARBOR_PASSWORD / TONGYI_API_KEY 等带前缀的形式)
            // keyword 后面用 \b 避免 passwordLength 这种"假阳性"
            // JSON 形式 {"password":"value"} 中 ["'] 在 [:=] 前面, 允许
            "(?i)(\\w*(?:password|secret|token|api_?key|access_?key|private_?key|auth))\\b"
                    + "\\s*[\"']?\\s*[:=]\\s*[\"']?([^\"',;\\s}{]+)[\"']?");

    private static final String REDACTED = "***";

    private PromptSanitizer() {}

    /**
     * 脱敏字符串中的 secret 字段. null / 空字符串原样返回.
     *
     * @param input 原始文本
     * @return 脱敏后文本 (如果没有匹配, 原样返回)
     */
    public static String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher m = SENSITIVE.matcher(input);
        if (!m.find()) {
            return input;
        }
        // reset + replace 循环 (Matcher.replaceAll 用一次扫描, 这里手动 reset 安全)
        m.reset();
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            // group(1) = prefix + keyword 整体 (如 "HARBOR_PASSWORD" / "password" / "apiKey")
            // group(2) = value (已脱敏, 不再使用)
            String fullKeyword = m.group(1);
            m.appendReplacement(sb, Matcher.quoteReplacement(fullKeyword + ": " + REDACTED));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

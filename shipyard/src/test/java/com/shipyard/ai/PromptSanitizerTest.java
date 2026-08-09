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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PromptSanitizer 单元测试 — 覆盖常见 secret 字段 + 边界 case.
 */
class PromptSanitizerTest {

    @Test
    @DisplayName("脱敏: env var 形式 (HARBOR_PASSWORD=xxx)")
    void sanitize_envVarForm() {
        String input = "Deploy with HARBOR_PASSWORD=abc123def to harbor";
        String result = PromptSanitizer.sanitize(input);
        assertThat(result).contains("HARBOR_PASSWORD: ***");
        assertThat(result).doesNotContain("abc123def");
    }

    @Test
    @DisplayName("脱敏: JSON 形式 (\"password\": \"xxx\")")
    void sanitize_jsonForm() {
        String input = "{\"username\":\"alice\",\"password\":\"super-secret-pwd\"}";
        String result = PromptSanitizer.sanitize(input);
        assertThat(result).contains("alice");
        assertThat(result).doesNotContain("super-secret-pwd");
        assertThat(result).contains("password: ***");
    }

    @Test
    @DisplayName("脱敏: YAML 形式 (apiKey: xxx)")
    void sanitize_yamlForm() {
        String input = "config:\n  apiKey: my-tongyi-key-12345\n  port: 8080";
        String result = PromptSanitizer.sanitize(input);
        assertThat(result).contains("apiKey: ***");
        assertThat(result).contains("port: 8080"); // 非敏感字段不动
        assertThat(result).doesNotContain("my-tongyi-key-12345");
    }

    @Test
    @DisplayName("脱敏: 大小写不敏感 (PASSWORD / Password / password 都匹配)")
    void sanitize_caseInsensitive() {
        assertThat(PromptSanitizer.sanitize("PASSWORD=foo")).contains("***").doesNotContain("foo");
        assertThat(PromptSanitizer.sanitize("Password=bar")).contains("***").doesNotContain("bar");
        assertThat(PromptSanitizer.sanitize("password=baz")).contains("***").doesNotContain("baz");
    }

    @Test
    @DisplayName("脱敏: 多个 secret 字段都替换")
    void sanitize_multipleSecrets() {
        String input = "token=tk1 password=pwd2 apiKey=k3 normal_field=value";
        String result = PromptSanitizer.sanitize(input);
        assertThat(result).doesNotContain("tk1");
        assertThat(result).doesNotContain("pwd2");
        assertThat(result).doesNotContain("k3");
        assertThat(result).contains("normal_field=value"); // 不动
    }

    @Test
    @DisplayName("不动: 没有 secret 字段的文本原样返")
    void sanitize_noSecret_unchanged() {
        String input = "项目元数据: name=demo, port=8080, javaVersion=21";
        assertThat(PromptSanitizer.sanitize(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("边界: null 和空字符串原样返")
    void sanitize_nullOrEmpty() {
        assertThat(PromptSanitizer.sanitize(null)).isNull();
        assertThat(PromptSanitizer.sanitize("")).isEqualTo("");
    }

    @Test
    @DisplayName("不动: 变量名中含 password 但后面非 secret value (passwordLength=5)")
    void sanitize_keywordInVariableName_unchanged() {
        // "passwordLength" 是变量名, 后跟 =5 (整数 5 不是 secret)
        // 我们的 regex \b 边界检查: keyword 后必须是 word boundary, "passwordLength" 中
        // password 后面是 "L" 不是 boundary, 所以不匹配 — 这正是我们想要的
        String input = "config passwordLength=5";
        assertThat(PromptSanitizer.sanitize(input)).isEqualTo(input);
    }
}

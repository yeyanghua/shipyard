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

package com.shipyard.crypto;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AesEncrypter 单元测试 + Property-based 测试.
 *
 * <p><b>两层覆盖</b>:
 * <ul>
 *   <li><b>普通单元测试</b>(@Test): 验证关键路径(空字符串、null、密文长度、IV 随机性)</li>
 *   <li><b>PBT</b>(@Property + jqwik): 任意输入都能加解密回来,这是加密器的"基本法"</li>
 * </ul>
 *
 * <p><b>PBT 为什么必要</b>(spec §8.4 选址):
 * 普通单元测试只测几个代表性输入,容易漏掉边界 case(空字符串、特殊字符、Unicode、超长)。
 * jqwik 自动生成 1000+ 随机输入,任何让"加解密不往返"的情况都跑不掉。
 */
@DisplayName("AesEncrypter 加密器测试")
class AesEncrypterTest {

    /**
     * 测试用 master key - 32 字节(64 hex 字符)。
     * 跟 application.yml 默认值一样,这样 Spring 启动时 key 校验能过。
     */
    private static final String TEST_KEY_HEX = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private AesEncrypter encrypter;

    @BeforeEach
    void setUp() {
        initEncrypter();
    }

    /**
     * jqwik 的 PBT lifecycle 跟 JUnit @BeforeEach 不完全同步,
     * 要加 @BeforeProperty 单独给 PBT 方法跑前初始化。
     * (否则 PBT 跑时 encrypter 字段是 null)
     */
    @BeforeProperty
    void beforeProperty() {
        initEncrypter();
    }

    private void initEncrypter() {
        // 用反射或直接 new 注入测试值,绕开 Spring 容器
        encrypter = new AesEncrypter();
        setField(encrypter, "masterKeyHex", TEST_KEY_HEX);
        setField(encrypter, "algorithm", "AES/GCM/NoPadding");
        setField(encrypter, "ivLength", 12);
        setField(encrypter, "tagLength", 128);
        setField(encrypter, "aad", "shipyard-platform");
        encrypter.init();
    }

    /**
     * ============================================================
     * 单元测试
     * ============================================================
     */

    @Test
    @DisplayName("加密 → 解密 往返应该相等")
    void encrypt_then_decrypt_should_return_original() {
        String plaintext = "DATABASE_URL=jdbc:mysql://localhost:3306/shipyard";
        String ciphertext = encrypter.encrypt(plaintext);
        String decrypted = encrypter.decrypt(ciphertext);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("同一明文两次加密,密文应该不同(IV 随机)")
    void encrypt_should_produce_different_ciphertext_each_time() {
        String plaintext = "secret-value-123";

        String ct1 = encrypter.encrypt(plaintext);
        String ct2 = encrypter.encrypt(plaintext);

        assertThat(ct1).isNotEqualTo(ct2);
        // 但两个密文解密后都应该是原文
        assertThat(encrypter.decrypt(ct1)).isEqualTo(plaintext);
        assertThat(encrypter.decrypt(ct2)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("空字符串加解密")
    void empty_string_should_round_trip() {
        String ciphertext = encrypter.encrypt("");
        assertThat(encrypter.decrypt(ciphertext)).isEmpty();
    }

    @Test
    @DisplayName("中文字符串加解密")
    void chinese_string_should_round_trip() {
        String plaintext = "用户名: 仔哥,密码: secret_密码_123";
        String ciphertext = encrypter.encrypt(plaintext);
        assertThat(encrypter.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("超长字符串(1MB)加解密")
    void very_long_string_should_round_trip() {
        String plaintext = "a".repeat(1024 * 1024);  // 1MB
        String ciphertext = encrypter.encrypt(plaintext);
        assertThat(encrypter.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("null plaintext 应该抛 IllegalArgumentException")
    void null_plaintext_should_throw() {
        assertThatThrownBy(() -> encrypter.encrypt(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("plaintext 不能为 null");
    }

    @Test
    @DisplayName("null ciphertext 应该抛 IllegalArgumentException")
    void null_ciphertext_should_throw() {
        assertThatThrownBy(() -> encrypter.decrypt(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ciphertext 不能为 null");
    }

    @Test
    @DisplayName("损坏的密文应该抛 CryptoException(不是数据丢失)")
    void corrupted_ciphertext_should_throw_crypto_exception() {
        // 拿一个合法密文,然后改一个字节
        String valid = encrypter.encrypt("test");
        // 把第一个字符换成 X(改变 base64 编码的某个字节)
        char first = valid.charAt(0);
        char corrupted = (first == 'A') ? 'B' : 'A';
        String bad = corrupted + valid.substring(1);

        assertThatThrownBy(() -> encrypter.decrypt(bad))
            .isInstanceOf(Encrypter.CryptoException.class);
    }

    @Test
    @DisplayName("key 长度不对应该在 init 时就抛(启动 fail-fast)")
    void wrong_key_length_should_fail_at_init() {
        AesEncrypter bad = new AesEncrypter();
        // 用 32 字节 hex 字符(偶数且合法)但实际只有 16 字节 - 触发"必须是 32 字节"检查
        setField(bad, "masterKeyHex", "aa".repeat(16));  // 32 hex chars = 16 bytes, 不到 32
        setField(bad, "ivLength", 12);
        setField(bad, "tagLength", 128);
        setField(bad, "aad", "shipyard-platform");

        assertThatThrownBy(bad::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("32 字节");
    }

    @Test
    @DisplayName("AAD 不匹配的密文应该解密失败(GCM 认证)")
    void ciphertext_with_wrong_aad_should_fail() {
        // 用 master key A 加密
        AesEncrypter encrypterA = encrypter;
        String ct = encrypterA.encrypt("secret");

        // 用不同 AAD 的 decrypter(其他配置一样)
        AesEncrypter encrypterB = new AesEncrypter();
        setField(encrypterB, "masterKeyHex", TEST_KEY_HEX);
        setField(encrypterB, "ivLength", 12);
        setField(encrypterB, "tagLength", 128);
        setField(encrypterB, "aad", "different-aad");
        encrypterB.init();

        assertThatThrownBy(() -> encrypterB.decrypt(ct))
            .isInstanceOf(Encrypter.CryptoException.class);
    }

    /**
     * ============================================================
     * Property-based 测试 (jqwik)
     * ============================================================
     */

    /**
     * <b>PBT 核心属性</b>: 任意非空字符串,encrypt → decrypt 必须等于原文。
     *
     * <p>jqwik 会自动生成 1000+ 随机字符串(包括空、ASCII、Unicode、中文、超长、特殊字符),
     * 任何让"加解密不往返"的 case 都会被自动捕获。
     *
     * <p>注意: PBT 方法不能用 @DisplayName(jqwik 不支持 + JUnit 注解冲突)
     */
    @Property
    void any_string_should_round_trip(@ForAll("anyPlaintext") String plaintext) {
        String ciphertext = encrypter.encrypt(plaintext);
        String decrypted = encrypter.decrypt(ciphertext);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    /**
     * PBT 提供器: 生成各种边界输入。
     * 包括:
     *   - 空字符串
     *   - ASCII 短串/长串
     *   - Unicode(中文/日文/韩文/emoji)
     *   - 特殊字符(\n \r \t \0 等控制字符)
     *   - SQL 注入尝试
     *   - 大字符串(接近 1MB)
     */
    @Provide
    Arbitrary<String> anyPlaintext() {
        return Arbitraries.oneOf(
            // 空字符串
            Arbitraries.just(""),
            // 普通 ASCII
            Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(100),
            // 中文
            Arbitraries.strings().ofMinLength(1).ofMaxLength(50)
                .map(s -> s + "中文测试" + s),
            // 包含特殊字符(模拟环境变量值)
            Arbitraries.strings().ascii().ofMinLength(1).ofMaxLength(50)
                .map(s -> "KEY=" + s + "&VALUE=secret!@#$%^&*()"),
            // 超长字符串(1KB - 1MB)
            Arbitraries.strings().ascii().ofMinLength(1024).ofMaxLength(1024 * 1024),
            // 控制字符
            Arbitraries.strings().ofMinLength(1).ofMaxLength(20)
                .map(s -> "\n\r\t\0\b\f" + s)
        );
    }

    /**
     * ============================================================
     * 工具方法
     * ============================================================
     */

    /**
     * 反射设置 private 字段(测试需要注入配置值,绕开 @Value)。
     */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("反射设置字段失败: " + fieldName, e);
        }
    }
}

package com.shipyard.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AesEncrypter 单元测试.
 *
 * <p>覆盖: 加解密往返、相同明文不同密文 (随机 IV)、篡改检测、边界输入、错误密钥、null/空值.
 *
 * <p>Property-based 思路: encrypt → decrypt 是恒等函数 (任意字符串 s, decrypt(encrypt(s)) == s).
 * 真实 property test 用 jqwik 跑,这里用循环验证.
 */
@DisplayName("AesEncrypter 加解密往返")
class AesEncrypterTest {

    private static final String VALID_KEY_BASE64 =
            Base64.getEncoder().encodeToString(new byte[32]); // 32 字节全 0,合法 AES-256 key

    private AesEncrypter encrypter;

    @BeforeEach
    void setUp() {
        encrypter = new AesEncrypter(VALID_KEY_BASE64);
    }

    @Test
    @DisplayName("往返: 简单字符串 encrypt → decrypt 还原")
    void roundTrip_simpleString() {
        String plaintext = "hello shipyard";
        String ciphertext = encrypter.encrypt(plaintext);
        String decrypted = encrypter.decrypt(ciphertext);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("往返: 中文/特殊字符/JSON/长字符串")
    void roundTrip_unicodeAndJson() {
        String[] inputs = {
                "中文测试 🚀",                                  // Unicode + emoji
                "{\"token\":\"glpat-xxxx\",\"host\":\"https://gitlab.com\"}",  // JSON
                "a".repeat(1000),                                // 1000 字符
                "line1\nline2\nline3\twith\ttab",                // 控制字符
                "!@#$%^&*()_+-={}[]|:;\"'<>,.?/`~"              // 特殊符号
        };

        for (String input : inputs) {
            String ciphertext = encrypter.encrypt(input);
            String decrypted = encrypter.decrypt(ciphertext);
            assertThat(decrypted)
                    .as("Round-trip failed for: %s", input)
                    .isEqualTo(input);
        }
    }

    @Test
    @DisplayName("相同明文加密结果不同 (随机 IV 生效)")
    void encrypt_producesDifferentCiphertextForSamePlaintext() {
        String plaintext = "same input";

        Set<String> ciphertexts = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ciphertexts.add(encrypter.encrypt(plaintext));
        }

        // 100 次加密应该全部不同 (碰撞概率 2^-96, 视为不可能)
        assertThat(ciphertexts)
                .as("AES-GCM 每次应该用随机 IV,100 次密文应全部不同")
                .hasSize(100);
    }

    @Test
    @DisplayName("密文被篡改 → 解密抛 CryptoException")
    void decrypt_tamperedCiphertext_throwsException() {
        String plaintext = "important token";
        String ciphertext = encrypter.encrypt(plaintext);

        // 篡改密文中间一字节
        byte[] bytes = Base64.getDecoder().decode(ciphertext);
        bytes[bytes.length / 2] ^= 0x01; // flip lowest bit
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> encrypter.decrypt(tampered))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("解密失败");
    }

    @Test
    @DisplayName("密文被截断 → 解密抛 CryptoException")
    void decrypt_truncatedCiphertext_throwsException() {
        String ciphertext = encrypter.encrypt("test");

        // 截断 (留 IV 但去掉部分密文+tag)
        String truncated = ciphertext.substring(0, ciphertext.length() - 4);

        assertThatThrownBy(() -> encrypter.decrypt(truncated))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("错误密钥 → 解密抛 CryptoException")
    void decrypt_wrongKey_throwsException() {
        String ciphertext = encrypter.encrypt("secret");

        // 用另一个 key 建解密器
        String otherKeyBase64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        // 注意: 这个 key 长度不合法,构造就该抛.改用合法长度但值不同的 key
        byte[] otherKeyBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            otherKeyBytes[i] = (byte) i;
        }
        String otherKey = Base64.getEncoder().encodeToString(otherKeyBytes);
        AesEncrypter otherEncrypter = new AesEncrypter(otherKey);

        assertThatThrownBy(() -> otherEncrypter.decrypt(ciphertext))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("null / 空字符串边界")
    void edgeCases_nullAndEmpty() {
        assertThatThrownBy(() -> encrypter.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> encrypter.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class);

        // 空字符串合法
        String ciphertext = encrypter.encrypt("");
        assertThat(encrypter.decrypt(ciphertext)).isEmpty();
    }

    @Test
    @DisplayName("无效 Base64 → 解密抛 CryptoException")
    void decrypt_invalidBase64_throwsException() {
        assertThatThrownBy(() -> encrypter.decrypt("!!!not-base64!!!"))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    @DisplayName("密钥长度错误 → 构造抛 IllegalArgumentException")
    void constructor_invalidKeyLength_throwsException() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]); // 16 字节 (AES-128)
        assertThatThrownBy(() -> new AesEncrypter(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");
    }
}

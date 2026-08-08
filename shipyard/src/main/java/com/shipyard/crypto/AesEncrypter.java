package com.shipyard.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * AES-256-GCM 加密器.
 *
 * <p>模式: AES/GCM/NoPadding (GCM = Galois/Counter Mode,带认证标签,防篡改).
 * <ul>
 *   <li>密钥长度: 256 bit (32 字节)
 *   <li>IV 长度: 96 bit (12 字节, GCM 推荐)
 *   <li>认证标签: 128 bit (16 字节,GCM 默认)
 *   <li>输出格式: [12 bytes IV][N bytes 密文+tag] → Base64
 * </ul>
 *
 * <p>安全性: 每次加密用随机 IV (SecureRandom), 相同明文每次密文不同,但解密后是原值.
 *
 * <p>V1 密钥来源: application.yml {@code shipyard.crypto.key} (Base64 编码的 32 字节).
 * V1.5 接 KMS 后改成从环境变量 / Vault 拉.
 *
 * <p>线程安全: stateless, {@link SecureRandom} 实例共享.
 */
@Component
public class AesEncrypter implements Encrypter {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32; // AES-256

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param keyBase64 Base64 编码的 32 字节密钥.从 {@code shipyard.crypto.key} 注入.
     */
    public AesEncrypter(@Value("${shipyard.crypto.key}") String keyBase64) {
        Objects.requireNonNull(keyBase64, "shipyard.crypto.key 不能为空");
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "shipyard.crypto.key 必须是 " + KEY_LENGTH_BYTES + " 字节 (AES-256), 实际是 " + keyBytes.length + " 字节");
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext 不能为 null");
        }
        try {
            // 1. 生成随机 IV
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            // 2. 初始化 Cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // 3. 加密
            byte[] ciphertextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 4. 拼接 IV + 密文+tag → Base64
            ByteBuffer buffer = ByteBuffer.allocate(IV_LENGTH_BYTES + ciphertextWithTag.length);
            buffer.put(iv);
            buffer.put(ciphertextWithTag);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new CryptoException("加密失败", e);
        }
    }

    @Override
    public String decrypt(String ciphertextBase64) {
        if (ciphertextBase64 == null) {
            throw new IllegalArgumentException("ciphertextBase64 不能为 null");
        }
        try {
            // 1. Base64 解码 → 拆 IV + 密文+tag
            byte[] all = Base64.getDecoder().decode(ciphertextBase64);
            if (all.length < IV_LENGTH_BYTES + 16) { // tag 至少 16 字节
                throw new CryptoException("密文长度不合法 (太短, 无 IV 或 tag)");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] ciphertextWithTag = new byte[all.length - IV_LENGTH_BYTES];
            System.arraycopy(all, IV_LENGTH_BYTES, ciphertextWithTag, 0, ciphertextWithTag.length);

            // 2. 解密 + 验签
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            byte[] plaintext = cipher.doFinal(ciphertextWithTag);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (CryptoException e) {
            throw e; // 透传
        } catch (Exception e) {
            throw new CryptoException("解密失败 (可能密文被篡改 / 密钥错误 / 编码损坏)", e);
        }
    }
}

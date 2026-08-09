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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密器 - V1 默认实现.
 *
 * <p><b>为什么选 AES/GCM/NoPadding</b>:
 * <ul>
 *   <li>AES-256: NIST 标准,JDK 自带,性能 OK</li>
 *   <li>GCM 模式: <b>AEAD(认证加密)</b>,同时保证机密性和完整性,比 CBC 安全
 *   (CBC 没认证,攻击者能篡改密文而不被发现)</li>
 *   <li>GCM 也是 TLS 1.3 默认,工业标准</li>
 *   <li>NoPadding: GCM 不需要 padding,简化逻辑</li>
 * </ul>
 *
 * <p><b>密文格式</b>(Base64 编码):
 * <pre>
 *   +---------+----------------+-----------------+
 *   | IV(12B) | 密文(n 字节)   | Tag(16B)         |
 *   +---------+----------------+-----------------+
 * </pre>
 * IV 每次加密随机生成(SecureRandom),所以同一明文 + 同一 key 加密结果不同.
 *
 * <p><b>V1.5 升级到 KMS 的步骤</b>(不改业务代码):
 * <ol>
 *   <li>新建 {@code KmsEncrypter implements Encrypter}</li>
 *   <li>把 {@code @Component} 换成 {@code @ConditionalOnProperty(name="shipyard.crypto.type", havingValue="kms")}</li>
 *   <li>从 Vault/AWS KMS/阿里云 KMS 拿数据加密密钥(DEK),再用 DEK 加密业务数据</li>
 *   <li>key 轮转、审计、权限管理全交给 KMS</li>
 * </ol>
 *
 * @see Encrypter
 */
@Slf4j
@Component
public class AesEncrypter implements Encrypter {

    /**
     * AES-256 master key,32 字节(256 bit).
     * V1 从 application.yml 读,V1.5 走 KMS.
     */
    @Value("${shipyard.crypto.master-key}")
    private String masterKeyHex;

    /**
     * 算法.默认 AES/GCM/NoPadding(JDK 9+ 都支持).
     */
    @Value("${shipyard.crypto.algorithm:AES/GCM/NoPadding}")
    private String algorithm;

    /**
     * IV 长度.GCM 推荐 12 字节(96 bit),Java 默认也是 12.
     */
    @Value("${shipyard.crypto.iv-length:12}")
    private int ivLength;

    /**
     * GCM 认证标签长度.128 bit 是 GCM 标准最大,Java 默认.
     */
    @Value("${shipyard.crypto.tag-length:128}")
    private int tagLength;

    /**
     * Additional Authenticated Data (AAD).
     * GCM 模式下绑定的元数据,密文 + AAD 一致才能解密成功.
     * 用 "shipyard-platform" 当 AAD,防止密文被搬到其他系统解密.
     */
    @Value("${shipyard.crypto.aad:shipyard-platform}")
    private String aad;

    /**
     * SecureRandom 是线程安全的,JDK 推荐共享一个实例.
     * 用 {@link SecureRandom#nextBytes(byte[])} 生成 IV.
     */
    private final SecureRandom secureRandom = new SecureRandom();

    private SecretKey secretKey;

    /**
     * 启动时校验 key 长度,失败直接抛异常(应用起不来).
     *
     * <p>为什么不在 encrypt/decrypt 时校验:启动时校验是 fail-fast,运行时校验是
     * "半个请求成功了然后报错"——后者用户体验差,排错也难.
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = hexToBytes(masterKeyHex);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                "shipyard.crypto.master-key 必须是 32 字节 (256 bit) 的 hex string. " +
                "当前长度: " + keyBytes.length + " 字节. " +
                "可以用 `openssl rand -hex 32` 生成新 key."
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("AesEncrypter initialized: algorithm={}, ivLength={}, tagLength={}, aad={}",
            algorithm, ivLength, tagLength, aad);
    }

    /**
     * 加密:生成随机 IV → AES/GCM 加密 → 拼接 IV + 密文 + Tag → Base64 编码.
     */
    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext 不能为 null");
        }
        try {
            // 1. 生成随机 IV
            byte[] iv = new byte[ivLength];
            secureRandom.nextBytes(iv);

            // 2. 初始化 Cipher
            Cipher cipher = Cipher.getInstance(algorithm);
            GCMParameterSpec spec = new GCMParameterSpec(tagLength, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            // 3. 设置 AAD(GCM 把 AAD 也认证)
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));

            // 4. 加密
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 5. 拼接 IV + ciphertext(GCM 模式下 ciphertext 包含 tag)
            byte[] result = new byte[ivLength + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, ivLength);
            System.arraycopy(ciphertext, 0, result, ivLength, ciphertext.length);

            // 6. Base64 编码
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new CryptoException("AES/GCM encrypt 失败", e);
        }
    }

    /**
     * 解密:Base64 解码 → 分离 IV + 密文 → AES/GCM 解密.
     */
    @Override
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            throw new IllegalArgumentException("ciphertext 不能为 null");
        }
        try {
            // 1. Base64 解码
            byte[] data = Base64.getDecoder().decode(ciphertext);

            // 2. 校验长度(至少要 IV + 16 字节 tag)
            if (data.length < ivLength + 16) {
                throw new IllegalArgumentException(
                    "密文长度不对: " + data.length + " 字节,至少需要 " + (ivLength + 16) + " 字节"
                );
            }

            // 3. 分离 IV + 密文
            byte[] iv = new byte[ivLength];
            byte[] ct = new byte[data.length - ivLength];
            System.arraycopy(data, 0, iv, 0, ivLength);
            System.arraycopy(data, ivLength, ct, 0, ct.length);

            // 4. 初始化 Cipher
            Cipher cipher = Cipher.getInstance(algorithm);
            GCMParameterSpec spec = new GCMParameterSpec(tagLength, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            // 5. 设置 AAD(必须跟 encrypt 时一样)
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));

            // 6. 解密(GCM 模式下自动验证 tag,验证失败抛 AEADBadTagException)
            byte[] plaintext = cipher.doFinal(ct);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException("AES/GCM decrypt 失败 (密文损坏 / key 不对 / AAD 不匹配)", e);
        }
    }

    /**
     * hex string → byte[].
     * 例: "0123abcd" → [0x01, 0x23, 0xab, 0xcd]
     */
    private static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return new byte[0];
        }
        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("hex string 长度必须是偶数");
        }
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("非 hex 字符在位置 " + i);
            }
            result[i / 2] = (byte) ((hi << 4) | lo);
        }
        return result;
    }
}

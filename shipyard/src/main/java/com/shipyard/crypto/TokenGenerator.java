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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Token 生成器 — M9.5 worker 鉴权 token.
 *
 * <p>生成规则:
 * <ul>
 *   <li>32 字节 (256 bit) 加密安全随机数 → URL-safe Base64 编码 → 44 字符</li>
 *   <li>Shipyard 端存 SHA-256 哈希 (Hex 64 字符), 不存明文</li>
 *   <li>明文只展示一次 (用户复制到 k8s manifest 的 WORKER_TOKEN env var)</li>
 * </ul>
 *
 * <p>为什么不存明文:
 * <ul>
 *   <li>DB 泄漏不会泄漏明文 token (DBA / 备份泄漏也安全)</li>
 *   <li>token 重新生成时, 旧 token 立即失效 (revoke 简单)</li>
 *   <li>跟 M5 drone webhook HMAC 验签同款 (HmacVerifier 已有 SHA-256 逻辑)</li>
 * </ul>
 *
 * <p>32 字节熵 ≈ 2^256, 暴力破解不可能.
 */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;

    private TokenGenerator() {}

    /**
     * 生成新的明文 token.
     *
     * @return 32 字节随机数的 URL-safe Base64 编码 (44 字符, 无 padding)
     */
    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 计算 token 的 SHA-256 哈希 (Hex 64 字符, 大写).
     *
     * @param token 明文 token
     * @return Hex 编码的 SHA-256 哈希
     */
    public static String hash(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("token cannot be null or empty");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 在 JDK 8+ 必有, 不可能到这里
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 验证明文 token 跟存库的哈希是否匹配.
     *
     * <p>用 constant-time comparison 防 timing attack.
     *
     * @param token      明文 token
     * @param storedHash 存库的 SHA-256 哈希 (Hex 64 字符)
     * @return true = 匹配
     */
    public static boolean verify(String token, String storedHash) {
        if (token == null || storedHash == null) return false;
        String computed = hash(token);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                storedHash.toUpperCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;

/**
 * HMAC-SHA256 验签器 — shipyard webhook 接收端用.
 *
 * <p>跟 {@link HmacSigner} 配对, 防 3 类攻击:
 * <ol>
 *   <li><b>伪造</b>: 攻击者构造 payload 但不知道 secret, 验签失败</li>
 *   <li><b>篡改</b>: 中间人改 body 但改不了签名 (HMAC 的 collision resistance)</li>
 *   <li><b>重放</b>: V1 demo 不防 (drone 同一 build 状态推多次 shipyard 都接受),
 *       V1.5 接入真实 drone 时加 timestamp + nonce + 短 TTL 防重放</li>
 * </ol>
 *
 * <p>实现细节:
 * <ul>
 *   <li>用 {@link MessageDigest#isEqual(byte[], byte[])} 比较 (constant-time, 防 timing attack)</li>
 *   <li>大小写不敏感 — drone 官方 hex 字符串统一转小写后再比</li>
 *   <li>secret 长度 ≥ 32 字符 (256 bit) — 防 weak key</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class HmacVerifier {

    private final String secret;

    /**
     * 验签.
     *
     * @param body   webhook 原始 body (跟 drone 签名时用的 input 完全一致)
     * @param signature drone 端传来的签名 (hex 字符串, {@code X-Drone-Signature} 头)
     * @return true=验签通过, false=验签失败
     */
    public boolean verify(String body, String signature) {
        if (body == null || signature == null || secret == null || secret.isEmpty()) {
            log.debug("[HmacVerifier] verify skipped (null/empty body/sig/secret)");
            return false;
        }
        if (secret.length() < 32) {
            log.warn("[HmacVerifier] secret too short ({} chars), security risk", secret.length());
        }
        try {
            HmacSigner signer = new HmacSigner(secret);
            String expected = signer.sign(body);
            // 大小写不敏感 + constant-time 比较
            return MessageDigest.isEqual(
                expected.getBytes(),
                signature.toLowerCase().getBytes()
            );
        } catch (Exception e) {
            log.error("[HmacVerifier] verify failed: {}", e.getMessage());
            return false;
        }
    }
}

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
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;

/**
 * HMAC-SHA256 签名器 — drone 端用, 给 webhook body 签名.
 *
 * <p>跟 {@link HmacVerifier} 配对使用, shipyard webhook 接收端用同一 secret 重算, 一致才放行.
 *
 * <p>设计:
 * <ul>
 *   <li>算法: {@code HmacSHA256} (跟 drone 默认一致)</li>
 *   <li>输出: 十六进制字符串 (drone 官方也是这种格式, 跟 {@code openssl dgst -sha256 -hmac ...} 一致)</li>
 *   <li>防错: secret 为 null/空时抛 {@link CryptoException}</li>
 * </ul>
 */
@RequiredArgsConstructor
public class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    /**
     * 对 input 字符串计算 HMAC-SHA256 签名, 返回十六进制小写字符串.
     *
     * @param input 要签名的原始字符串 (通常是 webhook body)
     * @return 十六进制小写 (例 {@code "9b2e...3a"})
     * @throws CryptoException secret 非法或 JVM 算法不可用
     */
    public String sign(String input) {
        if (secret == null || secret.isEmpty()) {
            throw new CryptoException("HMAC secret is null or empty");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] raw = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new CryptoException("Failed to compute HMAC: " + e.getMessage(), e);
        }
    }
}

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HmacVerifier 单元测试 — 覆盖伪造 / 篡改 / 边界 case.
 *
 * <p>对应 spec §9.2 "drone webhook HMAC 验签" 测试要求.
 */
class HmacVerifierTest {

    private static final String SECRET = "test-secret-key-at-least-32-chars-long-for-256bit";
    private static final String BODY = "{\"event\":\"build_finished\",\"drone_build_id\":\"drone-123\"}";
    private static final String VALID_SIG = new HmacSigner(SECRET).sign(BODY);

    private final HmacVerifier verifier = new HmacVerifier(SECRET);

    @Test
    @DisplayName("正常签名 - 验签通过")
    void verify_validSignature_returnsTrue() {
        assertThat(verifier.verify(BODY, VALID_SIG)).isTrue();
    }

    @Test
    @DisplayName("大小写不敏感 - 大写 hex 也能验签通过")
    void verify_uppercaseSignature_returnsTrue() {
        assertThat(verifier.verify(BODY, VALID_SIG.toUpperCase())).isTrue();
    }

    @Test
    @DisplayName("伪造签名 - 错一个字符就拒")
    void verify_forgedSignature_returnsFalse() {
        // 末位 +1 (某 hex 字符变成下一个, 但仍是合法 hex)
        char lastChar = VALID_SIG.charAt(VALID_SIG.length() - 1);
        char flipped = lastChar == '9' ? '0' : (char) (lastChar + 1);
        String forged = VALID_SIG.substring(0, VALID_SIG.length() - 1) + flipped;
        assertThat(forged).isNotEqualTo(VALID_SIG);
        assertThat(verifier.verify(BODY, forged)).isFalse();
    }

    @Test
    @DisplayName("完全伪造的 hex 串")
    void verify_garbageHex_returnsFalse() {
        assertThat(verifier.verify(BODY, "deadbeef".repeat(8))).isFalse();
    }

    @Test
    @DisplayName("body 被篡改 - 签名不匹配")
    void verify_tamperedBody_returnsFalse() {
        String tampered = BODY.replace("build_finished", "build_fake");
        assertThat(verifier.verify(tampered, VALID_SIG)).isFalse();
    }

    @Test
    @DisplayName("body 多一个空格 - 签名不匹配 (HMAC 严格按字节)")
    void verify_trailingSpace_returnsFalse() {
        assertThat(verifier.verify(BODY + " ", VALID_SIG)).isFalse();
    }

    @Test
    @DisplayName("不同 secret 签的 sig - 验签失败")
    void verify_wrongSecret_returnsFalse() {
        HmacVerifier wrongVerifier = new HmacVerifier("other-secret-also-at-least-32-chars-long-256bit");
        assertThat(wrongVerifier.verify(BODY, VALID_SIG)).isFalse();
    }

    @Test
    @DisplayName("null body - 验签失败 (不抛异常)")
    void verify_nullBody_returnsFalse() {
        assertThat(verifier.verify(null, VALID_SIG)).isFalse();
    }

    @Test
    @DisplayName("null sig - 验签失败 (不抛异常)")
    void verify_nullSig_returnsFalse() {
        assertThat(verifier.verify(BODY, null)).isFalse();
    }

    @Test
    @DisplayName("null secret - 验签失败 (不抛异常, 防运行时 NPE)")
    void verify_nullSecret_returnsFalse() {
        HmacVerifier emptyVerifier = new HmacVerifier(null);
        assertThat(emptyVerifier.verify(BODY, VALID_SIG)).isFalse();
    }

    @Test
    @DisplayName("空 secret - 验签失败")
    void verify_emptySecret_returnsFalse() {
        HmacVerifier emptyVerifier = new HmacVerifier("");
        assertThat(emptyVerifier.verify(BODY, VALID_SIG)).isFalse();
    }

    @Test
    @DisplayName("空 body - 验签失败 (不是 true)")
    void verify_emptyBody_returnsFalse() {
        // 空 body 的 HMAC 是合法的 hex, 但 verifier 应该拒, 不让空 payload 通过
        // (避免 drone 端 bug 推空 body 触发业务逻辑)
        assertThat(verifier.verify("", VALID_SIG)).isFalse();
    }

    @Test
    @DisplayName("长短 sig 边界 - 非 hex 字符的 sig 被拒")
    void verify_nonHexSignature_returnsFalse() {
        // HMAC-SHA256 输出 64 hex 字符, 这里 32 个非 hex (g, z 等)
        assertThat(verifier.verify(BODY, "gzgzgz".repeat(10) + "gz")).isFalse();
    }

    @Test
    @DisplayName("固定 drone 测试向量 - 跟 drone 官方格式一致")
    void verify_droneLikeVector_succeeds() {
        // 模拟 drone webhook body: {"hook_id":1234567,...}
        String droneBody = "{\"hook_id\":1234567,\"event\":\"build\",\"action\":\"finished\"}";
        String droneSecret = "drone-shared-secret-32-chars-minimum-256";
        HmacSigner drone = new HmacSigner(droneSecret);
        HmacVerifier shipyard = new HmacVerifier(droneSecret);
        String sig = drone.sign(droneBody);
        assertThat(shipyard.verify(droneBody, sig)).isTrue();
    }
}

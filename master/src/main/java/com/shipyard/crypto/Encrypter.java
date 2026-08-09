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

/**
 * 加密器接口 - shipyard 所有敏感字段(环境变量、repo token、worker token)走这个.
 *
 * <p><b>设计目的: envelope encryption(信封加密)</b><br>
 * V1 阶段用 {@link AesEncrypter} 实现,key 启动时从 application.yml 读.<br>
 * V1.5 阶段,只要新增一个 {@code KmsEncrypter} 实现这个接口 + 在配置里切换,业务代码一行不用改.
 * 这是经典的 <i>Strategy pattern + Dependency inversion</i>.
 *
 * <p><b>约束</b>:
 * <ul>
 *   <li>实现必须是线程安全的(master 是多线程 + 虚拟线程)</li>
 *   <li>实现必须支持认证加密(AEAD),不能单独用 ECB/CBC</li>
 *   <li>同一 plaintext + 同一 key 每次 encrypt 结果必须不同(IV 随机)</li>
 *   <li>密文必须能自包含(IV 放密文前缀,decrypt 时能解出来)</li>
 * </ul>
 *
 * <p><b>使用示例</b>:
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class EnvVariableService {
 *     private final Encrypter encrypter;
 *
 *     public void saveSecret(String value) {
 *         String encrypted = encrypter.encrypt(value);
 *         // 存数据库 var_value_enc 列
 *     }
 * }
 * }</pre>
 *
 * @see AesEncrypter
 */
public interface Encrypter {

    /**
     * 加密明文,返回 Base64 编码的密文(IV + ciphertext + tag 拼一起).
     *
     * @param plaintext 明文(不能为 null)
     * @return Base64 编码的密文,自包含 IV,可以直接存数据库
     * @throws IllegalArgumentException 如果 plaintext 为 null
     * @throws CryptoException 如果加密失败(比如 key 长度不对)
     */
    String encrypt(String plaintext);

    /**
     * 解密密文,返回原文.
     *
     * @param ciphertext Base64 编码的密文(由 {@link #encrypt(String)} 生成)
     * @return 原文
     * @throws IllegalArgumentException 如果 ciphertext 为 null
     * @throws CryptoException 如果解密失败(密文损坏/key 错/认证失败)
     */
    String decrypt(String ciphertext);

    /**
     * 加密异常 - 封装底层加密失败的 checked exception.
     *
     * <p>为什么用 RuntimeException: 业务代码不应该 try-catch 这个,加密失败说明系统
     * 配置/状态有问题,应该让调用者知道并告警,而不是吞掉.
     */
    class CryptoException extends RuntimeException {
        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

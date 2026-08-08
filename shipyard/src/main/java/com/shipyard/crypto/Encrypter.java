package com.shipyard.crypto;

/**
 * 加密器接口.
 *
 * <p>V1 实现: {@link AesEncrypter} (AES-256-GCM).
 * V1.5 设计: 接 KMS 时换实现,业务代码不动 — 这就是 envelope encryption 设计的初衷.
 *
 * <p>所有加密值 (repo_token / var_value / worker_token / webhook_url) 统一用这个接口.
 *
 * <p>线程安全: 实现必须是 stateless,支持并发.
 */
public interface Encrypter {

    /**
     * 加密明文 → 密文 (Base64 编码, 直接入库).
     *
     * @param plaintext 明文字符串
     * @return Base64 编码的密文 (包含 IV + 密文 + 认证标签)
     * @throws CryptoException 加密失败
     */
    String encrypt(String plaintext);

    /**
     * 解密密文 → 明文.
     *
     * @param ciphertextBase64 Base64 编码的密文
     * @return 明文字符串
     * @throws CryptoException 解密失败 (密文损坏 / 篡改 / 密钥错误)
     */
    String decrypt(String ciphertextBase64);
}

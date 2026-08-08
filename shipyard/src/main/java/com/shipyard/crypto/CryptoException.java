package com.shipyard.crypto;

/**
 * 加密/解密异常.
 *
 * <p>使用场景: 加解密失败、密文被篡改、密钥不匹配.
 * 业务层捕获后应记录到 {@code alert_log} (M7 会接入启动时全量校验).
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}

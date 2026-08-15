"""Reproduce V5 error to confirm root cause (AFTER description on missing column)."""
import pymysql

conn = pymysql.connect(host="localhost", port=3306, user="root", password="123456", database="shipyard")
try:
    cur = conn.cursor()
    # V5 Step 3: same ALTER as in V5
    try:
        cur.execute("""
            ALTER TABLE `env`
                ADD COLUMN `worker_url` VARCHAR(512) NULL DEFAULT NULL
                    COMMENT 'worker 服务 URL (V1 阶段: shipyard 直接调 K8s, 字段保留兼容 V3)'
                    AFTER `description`,
                ADD COLUMN `worker_token_enc` TEXT NULL DEFAULT NULL
                    COMMENT 'worker 鉴权 token (AES-256 加密, V1 阶段不用, 字段保留)'
                    AFTER `worker_url`,
                ADD COLUMN `k8s_namespace` VARCHAR(64) NULL DEFAULT NULL
                    COMMENT 'env 对应 k8s namespace (V1 阶段 shipyard 调 K8s API 用, shipyard-tunnel 跳板转发)'
                    AFTER `worker_token_enc`
        """)
        print("Step 3 succeeded")
    except Exception as e:
        print(f"Step 3 FAILED: {e}")
finally:
    conn.close()

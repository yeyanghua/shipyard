-- ============================================================
-- V4: M9.5 redesign worker table
-- ============================================================
-- 目标:
--   1. 1 worker = 1 pod (旧设计是 2 pod 1 row 共享, 导致 UI 实例数跟实际 pod 数对不上)
--   2. Pre-register model: 用户在 shipyard UI 创建 worker (预登记) → 部署 k8s pod → pod register
--      严格匹配 (env_id, pod_name) → 状态从 PLANNED 变 PROVISIONING → ONLINE
--   3. Token 生命周期: shipyard 端生成 32 字节随机 base64, 存 SHA-256 哈希, 明文只展示一次
--   4. env 表不再管 worker 部署细节 (workerUrl / workerToken / k8sNamespace 全删)
--
-- 数据迁移:
--   旧 worker 数据 (8/15 之前) 是脏的 (worker_name 空, 4 行), 这次 V4 直接 DROP TABLE 重建.
--   旧 env 数据保留, 只删 workerUrl / workerToken / k8sNamespace 字段.
-- ============================================================

-- ============================================================
-- Step 1: DROP 旧 worker 表 (数据脏, 重建)
-- ============================================================
DROP TABLE IF EXISTS `worker`;


-- ============================================================
-- Step 2: 重建 worker 表 (M9.5 新设计)
-- ============================================================
CREATE TABLE `worker` (
    `id`                  BIGINT       NOT NULL                              COMMENT '主键 (雪花 ID)',
    `env_id`              BIGINT       NOT NULL                              COMMENT '所属环境 ID (1 env — N worker, 1:N 关系)',
    `name`                VARCHAR(64)  NOT NULL                              COMMENT 'shipyard 内部展示名 (用户创建时填, 同 env 下唯一)',
    `pod_name`            VARCHAR(128) NOT NULL                              COMMENT 'k8s pod metadata.name (严格匹配 register, 同 env 下唯一)',
    `description`         VARCHAR(256) NULL      DEFAULT NULL                COMMENT '备注 / 描述 (用户可选填)',
    `worker_url`          VARCHAR(512) NULL      DEFAULT NULL                COMMENT 'worker 服务 URL (worker 启动后上报, shipyard 调 worker 用这个)',
    `worker_token_hash`   CHAR(64)     NOT NULL                              COMMENT 'worker 鉴权 token SHA-256 哈希 (Hex 64字符, 不存明文)',
    `status`              VARCHAR(16)  NOT NULL  DEFAULT 'PLANNED'           COMMENT '状态: PLANNED / PROVISIONING / ONLINE / OFFLINE / UNHEALTHY',
    `health`              VARCHAR(16)  NULL      DEFAULT NULL                COMMENT 'worker 自检状态: HEALTHY / UNHEALTHY (worker 上报)',
    `health_detail`       VARCHAR(512) NULL      DEFAULT NULL                COMMENT 'worker 自检失败原因 (worker 上报)',
    `last_heartbeat_at`   DATETIME     NULL      DEFAULT NULL                COMMENT '最后心跳时间 (worker 30s 上报一次)',
    `version`             VARCHAR(32)  NULL      DEFAULT NULL                COMMENT 'worker 版本 (从 ldflags 注入)',
    `created_by`          VARCHAR(64)  NOT NULL  DEFAULT 'system'            COMMENT '创建人 (V1 demo 默认 system, V1.5 接用户体系后存 userId/email)',
    `created_at`          DATETIME     NOT NULL  DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `updated_by`          VARCHAR(64)  NOT NULL  DEFAULT 'system'            COMMENT '修改人 (register / heartbeat 等系统行为 updated_by = system:register 等标记)',
    `updated_at`          DATETIME     NOT NULL  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             TINYINT      NOT NULL  DEFAULT 0                    COMMENT '逻辑删除: 0=active, 1=deleted',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_worker_env_pod` (`env_id`, `pod_name`)                    COMMENT '同 env 下 pod_name 唯一 (M9.5: 1 worker = 1 pod, register 严格匹配)',
    UNIQUE KEY `uk_worker_env_name` (`env_id`, `name`)                       COMMENT '同 env 下 name 唯一 (展示名, 防止重复)',
    KEY `idx_worker_status` (`env_id`, `status`)                            COMMENT 'WorkerSelector 按状态选 (env_id 走前缀, status 过滤)',
    KEY `idx_worker_heartbeat` (`last_heartbeat_at`)                        COMMENT 'WorkerHealthScanner 扫超时 row'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='worker 注册表 (M9.5: 1 worker = 1 pod, 预登记 + token 鉴权)';


-- ============================================================
-- Step 3: env 表删 worker 相关字段 (worker 现在自治, env 只管集群元数据)
-- ============================================================
-- 删字段 (M9.5: env 不再管 workerUrl / workerToken / k8sNamespace)
ALTER TABLE `env`
    DROP COLUMN `worker_url`,
    DROP COLUMN `worker_token_enc`,
    DROP COLUMN `k8s_namespace`;


-- ============================================================
-- Step 4: 更新 env 表 COMMENT (字段少了, 业务含义变化)
-- ============================================================
ALTER TABLE `env` COMMENT='环境定义 (M9.5: env 只管集群元数据 name/displayName/clusterType/isProduction, worker 自治)';

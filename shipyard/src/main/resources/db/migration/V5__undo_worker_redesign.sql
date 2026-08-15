-- ============================================================
-- V5: 撤回到 V3 worker 模型 (V1 阶段放弃 Go worker, 改 in-process 模拟)
-- ============================================================
-- 背景:
--   V4 (M9.5 redesign) 引入了 1 worker = 1 pod / pre-register / token 鉴权模式,
--   shipyard 后端要等 Go worker 启动后 register 严格匹配才能 ONLINE.
--
--   V1 阶段放弃:
--     1. 团队技术栈全是 Java, Go worker 维护成本高 (V1 阶段 debug 门槛)
--     2. V1 demo 演示为主, 不需要真 worker 拉集群数据
--
--   撤回到 V3 模式:
--     - worker 表回到 V3 结构 (env_id + worker_url + token_hash 等)
--     - env 表加回 workerUrl / workerTokenEnc / k8sNamespace 字段
--     - shipyard 后端从外部 worker 拉数据 → shipyard 内部 in-process 模拟
--
-- 数据迁移:
--   V4 的 worker 表 (PLANNED 状态, 1 行) 数据丢弃, 重建 V3 结构空表.
--   V4 删的 env 表 3 字段 (worker_url / worker_token_enc / k8s_namespace) 加回.
--
-- V1.5+ 重新评估:
--   团队如果决定真接 Go worker (从 git history 恢复 M9.5 commit d029106), 写 V6 migration
--   再升级. 现在 V5 是 1 个回滚点, git history 完整, 任何时候能 cherry-pick 恢复.
-- ============================================================


-- ============================================================
-- Step 1: DROP V4 worker 表 (数据脏, 重建)
-- ============================================================
DROP TABLE IF EXISTS `worker`;


-- ============================================================
-- Step 2: 重建 worker 表 (V3 结构, 1 env — N worker 模型)
-- ============================================================
-- V1 阶段 in-process 模拟: worker 表只是元数据记录, 不再被真 worker 调 register.
-- shipyard 后端在 UI 创建 env 时直接 create row (status=ONLINE), in-process 模拟 register.
CREATE TABLE `worker` (
  `id`                  BIGINT       NOT NULL                            COMMENT '主键',
  `env_id`              BIGINT       NOT NULL                            COMMENT '所属环境 (一对多: env 1 — N worker)',
  `worker_name`         VARCHAR(128) NOT NULL                            COMMENT 'worker 唯一名 (env 下唯一, register SELECT 主键之一)',
  `worker_url`          VARCHAR(512) NOT NULL                            COMMENT 'worker 服务 URL (V1 阶段 shipyard 自填, in-process 模式)',
  `worker_token_hash`   VARCHAR(128) NOT NULL                            COMMENT 'worker 鉴权 token 哈希 (V1 阶段 in-process 不用, 留字段兼容 V3)',
  `last_heartbeat_at`   DATETIME     NULL                                COMMENT '最后心跳时间 (V1 阶段 shipyard 自己 update 模拟心跳)',
  `status`              VARCHAR(16)  NOT NULL DEFAULT 'offline'          COMMENT '状态: online / offline / unhealthy',
  `version`             VARCHAR(32)  NULL                                COMMENT 'worker 版本',
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '注册时间',
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_worker_env_name` (`env_id`, `worker_name`)             COMMENT '同 env 下 worker name 唯一',
  KEY `idx_worker_env` (`env_id`),
  KEY `idx_worker_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='worker 注册表 (V1 阶段 in-process 模拟, shipyard 后端内部维护, 不依赖真 worker)';


-- ============================================================
-- Step 3: env 表加回 3 个字段 (V1 原始模型: env 自己管 workerUrl / token / namespace)
-- ============================================================
-- V1 阶段 in-process 模拟: env 表自己管 worker 部署细节, shipyard 后端调 4 个集群代理接口时直接读这里.
-- workerUrl: 调 K8s API 的 URL (V1 阶段 shipyard 自己连 K8s, 不用 workerUrl 但字段保留兼容 V3)
-- workerTokenEnc: AES-256 加密的 token (V1 阶段不用, 字段保留兼容 V3)
-- k8sNamespace: env 对应的 k8s namespace (V1 阶段 shipyard 调 K8s API 时直接用, shipyard-tunnel 跳板转发)
--
-- 注意: V1 原始 env 表没有 description 列, 字段顺序是
--   id / name / display_name / cluster_type / is_production / created_at / updated_at / deleted
-- V4 redesign 也没加 description, 所以 3 字段直接加到末尾 (updated_at 之后) 即可.
ALTER TABLE `env`
    ADD COLUMN `worker_url` VARCHAR(512) NULL DEFAULT NULL
        COMMENT 'worker 服务 URL (V1 阶段: shipyard 直接调 K8s, 字段保留兼容 V3)'
        AFTER `updated_at`,
    ADD COLUMN `worker_token_enc` TEXT NULL DEFAULT NULL
        COMMENT 'worker 鉴权 token (AES-256 加密, V1 阶段不用, 字段保留)'
        AFTER `worker_url`,
    ADD COLUMN `k8s_namespace` VARCHAR(64) NULL DEFAULT NULL
        COMMENT 'env 对应 k8s namespace (V1 阶段 shipyard 调 K8s API 用, shipyard-tunnel 跳板转发)'
        AFTER `worker_token_enc`;


-- ============================================================
-- Step 4: 更新 env 表 COMMENT
-- ============================================================
ALTER TABLE `env` COMMENT='环境定义 (V1 阶段 in-process 模拟: env 自管 workerUrl / k8sNamespace, shipyard 调 K8s API 4 接口)';

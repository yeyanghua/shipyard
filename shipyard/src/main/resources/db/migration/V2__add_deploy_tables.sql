-- ============================================================
-- shipyard V2 — deploy_record + deploy_snapshot + worker.role + pipeline_template.deploy 字段
-- ============================================================
-- 关联: docs/M9-detail.md §2 数据模型
-- 迁移规则: append-only, 已应用的 V* 文件不再修改.
-- 这次改:
--   1. 新增 deploy_record (一次部署任务)
--   2. 新增 deploy_snapshot (部署 yaml 快照, 回滚源)
--   3. worker 表加 role 字段 (PRIMARY / STANDBY)
--   4. pipeline_template 表加 container_port / replicas / namespace_pattern 3 字段
-- ============================================================

-- ============================================================
-- 1. deploy_record — 一次部署任务
-- ============================================================
CREATE TABLE `deploy_record` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`        BIGINT       NOT NULL COMMENT '关联 project.id',
  `env_id`            BIGINT       NOT NULL COMMENT '关联 env.id (决定 deploy 到哪个集群)',
  `build_record_id`   BIGINT       NULL     COMMENT '关联 build_record.id (镜像来源, 可空表示手动选 image)',
  `image_tag`         VARCHAR(255) NOT NULL COMMENT '实际部署的镜像 (例 nginx:1.27.0)',
  `namespace`         VARCHAR(64)  NOT NULL COMMENT '实际 ns (shipyard-{env_name})',
  `deploy_yaml_sha256` CHAR(64)    NOT NULL COMMENT '渲染后 yaml sha256, 快查 diff',
  `current_snapshot_id` BIGINT     NULL     COMMENT '当前生效 snapshot, deploy_record 1 — N snapshot',
  `status`            VARCHAR(16)  NOT NULL COMMENT 'PENDING / RUNNING / SUCCESS / FAILED / TIMEOUT / CANCELED',
  `error_message`     TEXT         NULL,
  `started_at`        DATETIME     NULL,
  `finished_at`       DATETIME     NULL,
  `triggered_by`      VARCHAR(64)  NOT NULL DEFAULT 'unknown',
  `trigger_type`      VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / GIT_PUSH (V1.5)',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_project_env` (`project_id`, `env_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一次部署任务';

-- ============================================================
-- 2. deploy_snapshot — 一次部署的 yaml 快照 (回滚源)
-- ============================================================
CREATE TABLE `deploy_snapshot` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `deploy_record_id`  BIGINT       NOT NULL COMMENT '关联 deploy_record.id',
  `env_id`            BIGINT       NOT NULL,
  `project_id`        BIGINT       NOT NULL,
  `deploy_yaml`       LONGTEXT     NOT NULL COMMENT 'shipyard 渲染完的 K8s yaml (用户提交字段 + shipyard 补默认)',
  `deploy_yaml_sha256` CHAR(64)    NOT NULL,
  `created_by`        VARCHAR(64)  NOT NULL,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_deploy_record` (`deploy_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部署 yaml 快照 (回滚源)';

-- ============================================================
-- 3. pipeline_template 表加 deploy 字段
-- (worker.role 字段在 fix-commit "M9 worker 自治 + WorkerSelector 抽象" 移除 —
-- 仔哥 2026-08-11 拍板: worker 是自治服务, 不在 shipyard 里管主备, shipyard 只
-- 被动路由. 决策 6/7/8 全改, WorkerSelector 抽象包代替)
-- ============================================================
ALTER TABLE `pipeline_template`
  ADD COLUMN `container_port` INT NULL
  COMMENT '主容器监听端口 (deploy 用, V1.5 必填)'
  AFTER `template_yaml`;

ALTER TABLE `pipeline_template`
  ADD COLUMN `replicas` INT NOT NULL DEFAULT 1
  COMMENT '副本数 (deploy 用)'
  AFTER `container_port`;

ALTER TABLE `pipeline_template`
  ADD COLUMN `namespace_pattern` VARCHAR(64) NOT NULL DEFAULT 'shipyard-{env_name}'
  COMMENT '目标 ns 模板 ({env_name} 渲染时替换)'
  AFTER `replicas`;

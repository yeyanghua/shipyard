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
-- V1 老的 deploy_record (字段 deploy_status / snapshot_yaml / k8s_deployment_name / log_url)
-- 跟 M9 V2 新设计 (字段 status / deploy_yaml_sha256 / current_snapshot_id / error_message / trigger_type
-- + 独立 deploy_snapshot 表) 不兼容, V2 彻底重写. 老 deploy_record 数据丢失 (V1 demo 数据, M5 测试
-- 数据, 重新创建环境即可, 不影响生产).
DROP TABLE IF EXISTS `deploy_record`;
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
  AFTER `yaml_content`;

ALTER TABLE `pipeline_template`
  ADD COLUMN `replicas` INT NOT NULL DEFAULT 1
  COMMENT '副本数 (deploy 用)'
  AFTER `container_port`;

ALTER TABLE `pipeline_template`
  ADD COLUMN `namespace_pattern` VARCHAR(64) NOT NULL DEFAULT 'shipyard-{env_name}'
  COMMENT '目标 ns 模板 ({env_name} 渲染时替换)'
  AFTER `replicas`;

-- ============================================================
-- 4. worker 表加 health 字段 (commit-4 新增)
-- (worker 自检 + 跟 shipyard @Scheduled 配合, shipyard 是被动路由层.
--  worker 自检: k8s API 连不连得上 / 内存压力 / 磁盘满 没满, 自报 HEALTHY/UNHEALTHY;
--  shipyard WorkerHealthScanner 扫 last_heartbeat_at < now-90s 标 offline.
--  WorkerSelector 候选池只走 status='online' + health='HEALTHY' 的 worker,
--  unhealthy 不派活 — M9 fix-commit 决策 11 'shipyard 不 promote, 只剔除')
-- ============================================================
ALTER TABLE `worker`
  ADD COLUMN `health` VARCHAR(16) NOT NULL DEFAULT 'HEALTHY'
  COMMENT 'HEALTHY (默认, worker 自报健康) / UNHEALTHY (worker 自检失败, 不派活)'
  AFTER `status`;

ALTER TABLE `worker`
  ADD COLUMN `health_detail` VARCHAR(512) NULL
  COMMENT 'worker 自检失败原因 (例 k8s API timeout / disk full), 前端 + log 用'
  AFTER `health`;

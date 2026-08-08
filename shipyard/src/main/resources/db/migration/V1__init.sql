-- ============================================================
-- shipyard V1 — 12 张核心表 (M2 初始化 schema)
-- ============================================================
-- spec: docs/superpowers/specs/2026-08-08-platform-design.md §4.1
-- 迁移规则: append-only, 已应用的 V* 文件不再修改.
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. project — 项目元数据
-- ============================================================
DROP TABLE IF EXISTS `project`;
CREATE TABLE `project` (
  `id`              BIGINT       NOT NULL                            COMMENT '主键 (雪花 ID)',
  `name`            VARCHAR(64)  NOT NULL                            COMMENT '项目唯一名 (英文,小写,数字,中划线)',
  `display_name`    VARCHAR(128) NOT NULL                            COMMENT '显示名',
  `repo_provider`   VARCHAR(16)  NOT NULL                            COMMENT '仓库平台: gitlab / gitee',
  `repo_url`        VARCHAR(512) NOT NULL                            COMMENT '仓库 URL',
  `repo_token_enc`  TEXT         NULL                                COMMENT '仓库访问 token (AES-256 加密)',
  `default_branch`  VARCHAR(64)  NOT NULL DEFAULT 'main'             COMMENT '默认构建分支',
  `project_type`    VARCHAR(32)  NOT NULL                            COMMENT '项目类型: java_maven / java_gradle / node_pnpm / python_poetry / other',
  `project_meta`    JSON         NULL                                COMMENT '项目元数据: 语言版本/主类/jar 名/端口 等',
  `description`     VARCHAR(512) NULL                                COMMENT '项目描述',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除: 0=未删, 1=已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_name` (`name`),
  KEY `idx_project_type` (`project_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目元数据';


-- ============================================================
-- 2. pipeline_template — 流水线模板
-- ============================================================
DROP TABLE IF EXISTS `pipeline_template`;
CREATE TABLE `pipeline_template` (
  `id`              BIGINT       NOT NULL                            COMMENT '主键',
  `project_id`      BIGINT       NOT NULL                            COMMENT '所属项目',
  `version`         INT          NOT NULL                            COMMENT '版本号 (从 1 自增)',
  `yaml_content`    MEDIUMTEXT   NOT NULL                            COMMENT '流水线 YAML 内容',
  `review_status`   VARCHAR(16)  NOT NULL DEFAULT 'draft'            COMMENT '审核状态: draft / approved / rejected',
  `is_active`       TINYINT      NOT NULL DEFAULT 0                  COMMENT '是否当前生效 (一个 project 同时只有一个 active)',
  `created_by`      VARCHAR(64)  NOT NULL                            COMMENT '创建人',
  `ai_modified_by`  VARCHAR(64)  NULL                                COMMENT 'AI 修改人 (格式: provider/model)',
  `ai_prompt`       TEXT         NULL                                COMMENT 'AI 修改时的 prompt',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pipeline_project_version` (`project_id`, `version`),
  KEY `idx_pipeline_active` (`project_id`, `is_active`),
  KEY `idx_pipeline_review` (`review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流水线模板';


-- ============================================================
-- 3. dockerfile_template — Dockerfile 模板 (shipyard 自带)
-- ============================================================
DROP TABLE IF EXISTS `dockerfile_template`;
CREATE TABLE `dockerfile_template` (
  `id`                BIGINT       NOT NULL                            COMMENT '主键',
  `name`              VARCHAR(64)  NOT NULL                            COMMENT '模板名 (如 java_maven_jdk21)',
  `display_name`      VARCHAR(128) NOT NULL                            COMMENT '显示名',
  `language`          VARCHAR(32)  NOT NULL                            COMMENT '语言: java / node / python',
  `build_tool`        VARCHAR(32)  NOT NULL                            COMMENT '构建工具: maven / gradle / pnpm / poetry',
  `template_content`  MEDIUMTEXT   NOT NULL                            COMMENT '模板内容 (Mustache/Go template 含 ${var})',
  `variable_schema`   JSON         NOT NULL                            COMMENT '变量定义: [{key, type, default, description, required}]',
  `version`           INT          NOT NULL DEFAULT 1                  COMMENT '模板版本',
  `is_builtin`        TINYINT      NOT NULL DEFAULT 1                  COMMENT '是否 shipyard 自带: 1=内置, 0=用户自定义 (V1.5)',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dockerfile_template_name` (`name`),
  KEY `idx_dockerfile_template_lang` (`language`, `build_tool`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dockerfile 模板 (shipyard 自带)';


-- ============================================================
-- 4. project_dockerfile — 项目 Dockerfile 实例
-- ============================================================
DROP TABLE IF EXISTS `project_dockerfile`;
CREATE TABLE `project_dockerfile` (
  `id`                    BIGINT       NOT NULL                            COMMENT '主键',
  `project_id`            BIGINT       NOT NULL                            COMMENT '所属项目',
  `dockerfile_template_id` BIGINT      NOT NULL                            COMMENT '使用的模板',
  `rendered_content`      MEDIUMTEXT   NOT NULL                            COMMENT '渲染后内容 (即将 commit 进项目仓库)',
  `variable_values`       JSON         NOT NULL                            COMMENT '渲染时用的变量值',
  `repo_branch`           VARCHAR(64)  NOT NULL                            COMMENT '提交的目标分支',
  `repo_commit_sha`       VARCHAR(64)  NULL                                COMMENT '提交后的 commit SHA',
  `commit_message`        VARCHAR(512) NULL                                COMMENT '提交 message',
  `status`                VARCHAR(16)  NOT NULL DEFAULT 'draft'            COMMENT '状态: draft / pushed / rejected',
  `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `pushed_at`             DATETIME     NULL                                COMMENT 'push 到仓库的时间',
  `deleted`               TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_project_dockerfile_project` (`project_id`, `status`),
  KEY `idx_project_dockerfile_template` (`dockerfile_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目 Dockerfile 实例';


-- ============================================================
-- 5. env — 环境定义
-- ============================================================
DROP TABLE IF EXISTS `env`;
CREATE TABLE `env` (
  `id`                  BIGINT       NOT NULL                            COMMENT '主键',
  `name`                VARCHAR(64)  NOT NULL                            COMMENT '环境名 (英文)',
  `display_name`        VARCHAR(128) NOT NULL                            COMMENT '显示名',
  `cluster_type`        VARCHAR(16)  NOT NULL DEFAULT 'k8s'              COMMENT '集群类型: k8s',
  `k8s_namespace`       VARCHAR(64)  NOT NULL                            COMMENT 'k8s namespace',
  `worker_url`          VARCHAR(512) NOT NULL                            COMMENT 'worker 服务的 URL',
  `worker_token_enc`    TEXT         NULL                                COMMENT 'worker 鉴权 token (AES-256 加密)',
  `is_production`       TINYINT      NOT NULL DEFAULT 0                  COMMENT '是否生产环境',
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_env_name` (`name`),
  KEY `idx_env_production` (`is_production`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='环境定义';


-- ============================================================
-- 6. project_env — 项目-环境关联
-- ============================================================
DROP TABLE IF EXISTS `project_env`;
CREATE TABLE `project_env` (
  `project_id`  BIGINT NOT NULL COMMENT '项目 ID',
  `env_id`      BIGINT NOT NULL COMMENT '环境 ID',
  `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
  PRIMARY KEY (`project_id`, `env_id`),
  KEY `idx_project_env_env` (`env_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目-环境关联 (N:N)';


-- ============================================================
-- 7. env_variable — 环境变量 (加密存储)
-- ============================================================
DROP TABLE IF EXISTS `env_variable`;
CREATE TABLE `env_variable` (
  `id`            BIGINT       NOT NULL                            COMMENT '主键',
  `env_id`        BIGINT       NOT NULL                            COMMENT '环境 ID',
  `project_id`    BIGINT       NULL                                COMMENT '项目 ID (NULL = 全局变量, 不为 NULL = 项目级变量)',
  `var_key`       VARCHAR(128) NOT NULL                            COMMENT '变量名',
  `var_value_enc` TEXT         NOT NULL                            COMMENT '变量值 (AES-256 加密)',
  `is_secret`     TINYINT      NOT NULL DEFAULT 1                  COMMENT '是否敏感: 1=敏感 (UI 隐藏), 0=明文显示',
  `description`   VARCHAR(512) NULL                                COMMENT '说明',
  `updated_by`    VARCHAR(64)  NOT NULL                            COMMENT '更新人',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_env_variable_unique` (`env_id`, `project_id`, `var_key`),
  KEY `idx_env_variable_project` (`project_id`),
  KEY `idx_env_variable_key` (`var_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='环境变量 (加密存储)';


-- ============================================================
-- 8. build_record — 构建记录
-- ============================================================
DROP TABLE IF EXISTS `build_record`;
CREATE TABLE `build_record` (
  `id`                      BIGINT       NOT NULL                            COMMENT '主键',
  `project_id`              BIGINT       NOT NULL                            COMMENT '项目 ID',
  `pipeline_template_id`    BIGINT       NULL                                COMMENT '使用的流水线模板版本 ID',
  `commit_sha`              VARCHAR(64)  NOT NULL                            COMMENT '构建的 commit SHA',
  `commit_message`          TEXT         NULL                                COMMENT 'commit message',
  `triggered_by`            VARCHAR(64)  NOT NULL                            COMMENT '触发人',
  `trigger_type`            VARCHAR(16)  NOT NULL                            COMMENT '触发类型: manual / webhook / api',
  `drone_build_id`          VARCHAR(64)  NULL                                COMMENT 'drone 构建 ID',
  `status`                  VARCHAR(16)  NOT NULL DEFAULT 'pending'          COMMENT '状态: pending / running / success / failed / timeout / canceled',
  `image_tag`               VARCHAR(128) NULL                                COMMENT '构建出的镜像 tag',
  `harbor_image_url`        VARCHAR(512) NULL                                COMMENT 'Harbor 镜像 URL',
  `started_at`              DATETIME     NULL                                COMMENT '开始时间',
  `finished_at`             DATETIME     NULL                                COMMENT '结束时间',
  `log_url`                 VARCHAR(512) NULL                                COMMENT 'drone 日志 URL (兜底,主走 shipyard 持久化)',
  `log_persisted`           TINYINT      NOT NULL DEFAULT 0                  COMMENT '日志是否已落 shipyard (1=是)',
  `created_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `deleted`                 TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_build_record_project` (`project_id`, `created_at` DESC),
  KEY `idx_build_record_status` (`status`),
  KEY `idx_build_record_drone` (`drone_build_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='构建记录';


-- ============================================================
-- 9. build_log — 构建日志 (按 step 存)
-- ============================================================
DROP TABLE IF EXISTS `build_log`;
CREATE TABLE `build_log` (
  `id`               BIGINT       NOT NULL                            COMMENT '主键',
  `build_record_id`  BIGINT       NOT NULL                            COMMENT '所属构建记录',
  `step_name`        VARCHAR(64)  NOT NULL                            COMMENT 'step 名 (如 compile, test, docker-push)',
  `step_order`       INT          NOT NULL                            COMMENT 'step 执行顺序',
  `log_content`      LONGTEXT     NOT NULL                            COMMENT '完整日志内容',
  `log_size_bytes`   BIGINT       NOT NULL                            COMMENT '日志字节数 (便于查询/分页)',
  `started_at`       DATETIME     NULL                                COMMENT 'step 开始时间',
  `finished_at`      DATETIME     NULL                                COMMENT 'step 结束时间',
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '落库时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_build_log_step` (`build_record_id`, `step_name`),
  KEY `idx_build_log_order` (`build_record_id`, `step_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='构建日志 (按 step 持久化)';


-- ============================================================
-- 10. deploy_record — 发布记录 (含 snapshot 用于回滚)
-- ============================================================
DROP TABLE IF EXISTS `deploy_record`;
CREATE TABLE `deploy_record` (
  `id`                       BIGINT       NOT NULL                            COMMENT '主键',
  `build_record_id`          BIGINT       NOT NULL                            COMMENT '关联的构建记录',
  `project_id`               BIGINT       NOT NULL                            COMMENT '项目 ID',
  `env_id`                   BIGINT       NOT NULL                            COMMENT '环境 ID',
  `deploy_status`            VARCHAR(16)  NOT NULL DEFAULT 'pending'          COMMENT '状态: pending / running / success / failed / rolled_back',
  `snapshot_yaml`            MEDIUMTEXT   NOT NULL                            COMMENT 'k8s deployment yaml 快照 (回滚用,全量存)',
  `k8s_deployment_name`      VARCHAR(128) NULL                                COMMENT 'k8s deployment 名',
  `triggered_by`             VARCHAR(64)  NOT NULL                            COMMENT '触发人',
  `started_at`               DATETIME     NULL                                COMMENT '开始时间',
  `finished_at`              DATETIME     NULL                                COMMENT '结束时间',
  `log_url`                  VARCHAR(512) NULL                                COMMENT 'worker 日志 URL',
  `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `deleted`                  TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_deploy_record_build` (`build_record_id`),
  KEY `idx_deploy_record_project_env` (`project_id`, `env_id`, `created_at` DESC),
  KEY `idx_deploy_record_status` (`deploy_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发布记录 (snapshot 全量,支持回滚)';


-- ============================================================
-- 11. worker — worker 注册
-- ============================================================
DROP TABLE IF EXISTS `worker`;
CREATE TABLE `worker` (
  `id`                  BIGINT       NOT NULL                            COMMENT '主键',
  `env_id`              BIGINT       NOT NULL                            COMMENT '所属环境 (一对多: env 1 — N worker)',
  `worker_url`          VARCHAR(512) NOT NULL                            COMMENT 'worker 服务 URL',
  `worker_token_hash`   VARCHAR(128) NOT NULL                            COMMENT 'worker 鉴权 token 哈希 (不存明文)',
  `last_heartbeat_at`   DATETIME     NULL                                COMMENT '最后心跳时间',
  `status`              VARCHAR(16)  NOT NULL DEFAULT 'offline'          COMMENT '状态: online / offline / unhealthy',
  `version`             VARCHAR(32)  NULL                                COMMENT 'worker 版本',
  `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '注册时间',
  `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_worker_env` (`env_id`),
  KEY `idx_worker_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='worker 注册表 (每环境多个 worker)';


-- ============================================================
-- 12. ai_interaction — AI 对话留痕
-- ============================================================
DROP TABLE IF EXISTS `ai_interaction`;
CREATE TABLE `ai_interaction` (
  `id`            BIGINT       NOT NULL                            COMMENT '主键',
  `user_id`       VARCHAR(64)  NOT NULL                            COMMENT '用户',
  `capability`    VARCHAR(32)  NOT NULL                            COMMENT '能力: pipeline_gen / diagnosis / decision',
  `input_prompt`  TEXT         NOT NULL                            COMMENT '输入 prompt',
  `llm_provider`  VARCHAR(32)  NOT NULL                            COMMENT 'LLM provider: mock / tongyi / deepseek',
  `llm_model`     VARCHAR(64)  NULL                                COMMENT 'LLM 模型名',
  `llm_request`   JSON         NULL                                COMMENT 'LLM 请求 (脱敏后)',
  `llm_response`  JSON         NULL                                COMMENT 'LLM 响应 (完整)',
  `output_action` TEXT         NULL                                COMMENT '输出动作描述 (如: 改了 pipeline 模板 ID 5 → 6)',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_user` (`user_id`, `created_at` DESC),
  KEY `idx_ai_capability` (`capability`),
  KEY `idx_ai_provider` (`llm_provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话留痕 (可观测 + 调试 + 计费)';


-- ============================================================
-- 13. alert_log — 告警记录 (V1 不出站,只记录 + UI 展示)
-- ============================================================
DROP TABLE IF EXISTS `alert_log`;
CREATE TABLE `alert_log` (
  `id`           BIGINT       NOT NULL                            COMMENT '主键',
  `level`        VARCHAR(8)   NOT NULL                            COMMENT '级别: P0 / P1 / P2',
  `event_type`   VARCHAR(64)  NOT NULL                            COMMENT '事件类型 (如 BUILD_FAILED, DEPLOY_CRASHLOOP)',
  `message`      VARCHAR(1024) NOT NULL                           COMMENT '告警消息',
  `context_json` JSON         NULL                                COMMENT '上下文 (build_id, deploy_id, etc.)',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'open'             COMMENT '状态: open / acknowledged / resolved',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `resolved_at`  DATETIME     NULL                                COMMENT '解决时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0                  COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_alert_level_status` (`level`, `status`),
  KEY `idx_alert_created` (`created_at` DESC),
  KEY `idx_alert_event_type` (`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警记录 (V1 只入库, V1.5 出站飞书/钉钉)';


SET FOREIGN_KEY_CHECKS = 1;

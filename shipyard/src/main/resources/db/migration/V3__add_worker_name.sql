-- shipyard V3 — worker 表加 worker_name 字段 (M9 commit-16)
-- ============================================================
-- 背景:
--   之前 WorkerServiceImpl.register 用 workerUrl 做 SELECT 主键, 2 pod 1 worker
--   模型下 pod 重启 register 不同 URL → 插新 row, 旧 row 永远 stale + 手动 UPDATE
--   workerUrl 后 register 又插新行覆盖手动改的端口.
--
-- 修复: SELECT 主键改成 (env_id, worker_name), 2 pod 同 name 复用同一行,
--   复用时保留手动 UPDATE 后的 workerUrl (dev 阶段 shipyard→worker 走 port-forward,
--   worker 上报 svc.cluster.local:8888 调不通, 必须 shipyard 端 workerUrl 改 localhost:18888).
--
-- 字段:
--   worker_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'worker 唯一名, 默认 worker-${HOSTNAME}'
--   联合唯一索引 (env_id, worker_name) — 保证同 env 下 worker name 唯一
--
-- ⚠️ 注意: 这个 SQL 假设 dev DB 已经手动跑过 (列已经存在, UNIQUE 已经存在).
--   干净的 dev 应该是 DROP TABLE 重建, 但 M9 阶段不允许 (DB 已有 M8 数据).
--   实际做法: V3 SQL 在 V3*apply 时 Flyway 会先看 schema_history 有没有 V3 记录 —
--   第一次启动 (列未加) → apply 这个 SQL; 已 apply (列已加) → 跳过.
--   MySQL 8 不支持 IF NOT EXISTS ADD COLUMN, 所以列已存在会报 1060 错.
--   解决: 用存储过程 + INFORMATION_SCHEMA 判断 (过于复杂), 或 V3 SQL 假定 dev
--   列已存在, 第一次启动手动执行这个 SQL (V3 标 success=1, Flyway 不再重跑).
--
-- 干净生产: 第一次启动 V3 没记录, Flyway apply 这个 SQL, 6 个老 row UPDATE 唯一名
--   (假设还没部署) → 不会冲突. 已部署老 row → dev ops 手动 UPDATE unique.
--
-- 迁移规则: append-only, V1/V2 已 applied 不改.
-- ============================================================

-- 加列 (假定未 apply 过; 已 apply 时会 1060 错, dev 阶段手动 DROP COLUMN + 标 V3 success=1)
ALTER TABLE `worker`
  ADD COLUMN `worker_name` VARCHAR(128) NOT NULL DEFAULT ''
  COMMENT 'worker 唯一名 (env 下唯一, register SELECT 主键之一)'
  AFTER `env_id`;

-- 老数据兜底: 给历史 worker row 补唯一 name (避免加 UNIQUE 时 1062 冲突)
-- 顺序: 先 ADD COLUMN (列已建, 默认 '') 再 UPDATE 老 row (空字符串会 UPDATE 成 'legacy-{id}')
UPDATE `worker`
  SET `worker_name` = CONCAT('legacy-', `id`)
  WHERE `deleted` = 0 AND `worker_name` = '';

-- 联合唯一索引 — 同 env 下 worker name 唯一
ALTER TABLE `worker`
  ADD UNIQUE KEY `uk_worker_env_name` (`env_id`, `worker_name`);

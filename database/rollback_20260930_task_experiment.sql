SET NAMES utf8mb4;

-- ============================================================
-- 回滚：任务↔实验目录桥接（upgrade_20260930_task_experiment.sql 的逆操作）
-- 2026-09-30
-- ============================================================

ALTER TABLE `lab_project_task`
    DROP KEY `idx_task_experiment`,
    DROP COLUMN `experiment_id`;

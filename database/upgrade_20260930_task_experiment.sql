-- ========================================================
-- LTPMS 升级脚本：任务↔实验目录桥接（教学闭环收口 T03）
-- 日期：2026-09-30
-- 依据：Q5 深桥接——lab_project_task.experiment_id 可空逻辑外键；
--       删实验时关联任务置空（应用层钩子），不设数据库级联
-- 外键策略与 init.sql 一致：逻辑外键，应用层保证完整性
-- ========================================================

SET NAMES utf8mb4;

ALTER TABLE `lab_project_task`
    ADD COLUMN `experiment_id` BIGINT NULL COMMENT 'FK → experiment.id（可空；所属课程容器目录行，删实验时置空）',
    ADD KEY `idx_task_experiment` (`experiment_id`);

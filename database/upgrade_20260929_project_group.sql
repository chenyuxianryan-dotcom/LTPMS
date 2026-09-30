-- ========================================================
-- LTPMS 升级脚本：课题实验分组（成员板块「分组」能力）
-- 日期：2026-09-29
-- 依据：每组人数唯一事实源为 experiment.group_size；分组按「每个实验各一套」建模
-- 外键策略与 init.sql 一致：逻辑外键，应用层保证完整性
-- ========================================================

SET NAMES utf8mb4;

-- --------------------------------------------------------
-- 1. 实验分组表（一个实验一套分组，组名在课题×实验内唯一）
--    baseline_size 落「建组时」的每组人数，供目录变更后不一致提示
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lab_project_group` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL COMMENT 'FK → lab_project.id',
    `experiment_id` BIGINT NOT NULL COMMENT 'FK → experiment.id（每组人数取自该实验）',
    `group_name` VARCHAR(64) NOT NULL COMMENT '组名（自动分组=第N组，手动建组可自定义）',
    `baseline_size` INT NOT NULL COMMENT '建组时快照的每组人数（experiment.group_size）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_name` (`project_id`, `experiment_id`, `group_name`),
    KEY `idx_pg_experiment` (`experiment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课题实验分组表';

-- --------------------------------------------------------
-- 2. 分组组员关系表
--    「同一实验下一人只属一组」由服务层保证（移入时先从同实验其他组摘出）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lab_project_group_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `group_id` BIGINT NOT NULL COMMENT 'FK → lab_project_group.id',
    `student_id` BIGINT NOT NULL COMMENT 'FK → student.id',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_student` (`group_id`, `student_id`),
    KEY `idx_pgm_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课题分组组员关系表';

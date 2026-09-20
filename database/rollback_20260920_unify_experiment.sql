-- ========================================================
-- LTPMS 回滚脚本：课题项目 × 实验项目 合并（批次三）回退
-- 日期：2026-09-20 ｜ 配套：database/upgrade_20260920_unify_experiment.sql
-- 依据：LTPMS_课题项目合并_需求拷问记录.md §7.3（按标记先删成员后删容器）
-- 语义：只清理带标记的行（容器 source='SYSTEM' 及其全部子行），
--       存量 USER 课题与 experiment 目录行零触碰；可重放。
-- 顺序：孙 → 子 → 父（提交记录 → 任务 → 版本 → 数据集 → 成员 → 容器）
-- ========================================================

SET NAMES utf8mb4;

-- 0. 预检：回滚前先看将被清理的容器清单（只读，人工确认）
-- SELECT id, project_code, project_name, course_id, semester_id FROM lab_project WHERE source = 'SYSTEM';

-- 1. 容器任务提交记录
DELETE s FROM `lab_task_submission` s
JOIN `lab_project_task` t ON t.id = s.task_id
JOIN `lab_project` p ON p.id = t.project_id
WHERE p.source = 'SYSTEM';

-- 2. 容器任务（含软删行）
DELETE t FROM `lab_project_task` t
JOIN `lab_project` p ON p.id = t.project_id
WHERE p.source = 'SYSTEM';

-- 3. 容器数据集版本
DELETE v FROM `lab_dataset_version` v
JOIN `lab_dataset` d ON d.id = v.dataset_id
JOIN `lab_project` p ON p.id = d.project_id
WHERE p.source = 'SYSTEM';

-- 4. 容器数据集
DELETE d FROM `lab_dataset` d
JOIN `lab_project` p ON p.id = d.project_id
WHERE p.source = 'SYSTEM';

-- 5. 容器成员（含 AUTO_SNAPSHOT 快照与后续人工加入的行——容器整体拆除，不留孤儿）
DELETE m FROM `lab_project_member` m
JOIN `lab_project` p ON p.id = m.project_id
WHERE p.source = 'SYSTEM';

-- 6. 容器本体（按打标，禁止用 LIKE 项目名等模糊条件）
DELETE FROM `lab_project` WHERE source = 'SYSTEM';

-- 7. schema 侧说明（按 §7.3 军规不执行）：
--    `lab_project.source` 列 / `uk_course_semester_source` 索引 / ENUM 第四值
--    在标记行删净后无引用，回退后端代码后自然失效，无需数据库层回退。
--    如确需彻底回退 schema，按下列顺序手工执行（下方语句默认注释）：
-- ALTER TABLE `lab_project` DROP INDEX `uk_course_semester_source`;
-- ALTER TABLE `lab_project` DROP COLUMN `source`;
-- ALTER TABLE `lab_project_member`
--     MODIFY COLUMN `source` ENUM('CREATE','MANUAL','COURSE_SNAPSHOT') NOT NULL DEFAULT 'MANUAL'
--     COMMENT '带入来源';

-- 8. 回滚后自检（只读）：
-- SELECT COUNT(*) AS leftover_container FROM lab_project WHERE source = 'SYSTEM';
-- SELECT COUNT(*) AS leftover_snapshot FROM lab_project_member WHERE source = 'AUTO_SNAPSHOT';

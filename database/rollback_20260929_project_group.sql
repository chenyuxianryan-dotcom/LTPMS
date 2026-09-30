SET NAMES utf8mb4;

-- ============================================================
-- 回滚：课题实验分组（upgrade_20260929_project_group.sql 的逆操作）
-- 2026-09-29
-- 顺序：先删关系表再删组表
-- ============================================================

DROP TABLE IF EXISTS `lab_project_group_member`;
DROP TABLE IF EXISTS `lab_project_group`;

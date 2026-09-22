SET NAMES utf8mb4;

-- ============================================================
-- 回滚：知识库（upgrade_20260922_knowledge_base.sql 的逆操作）
-- 2026-09-22
-- 注意：仅删表，不清理 uploads/kb 下已落盘文件（如需清理请手工删除）
-- ============================================================

DROP TABLE IF EXISTS `knowledge_chunk`;
DROP TABLE IF EXISTS `knowledge_document`;

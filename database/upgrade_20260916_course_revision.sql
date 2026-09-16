-- ========================================================
-- LTPMS 升级脚本：教师课程管理优化 —— 修订留痕表（2026-09-16）
-- 功能：schedule_revision 字段级修订记录（谁/何时/旧值→新值），
--       支撑行级修订标记、明细抽屉与导入冲突提示
-- 在已初始化的 ltpms_db 上执行一次即可
-- ========================================================

USE ltpms_db;

CREATE TABLE IF NOT EXISTS `schedule_revision` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `target_type` VARCHAR(16) NOT NULL COMMENT '目标类型：SCHEDULE / COURSE',
    `target_id` BIGINT NOT NULL COMMENT '目标行 ID（对应行被删除后记录保留为历史）',
    `field_name` VARCHAR(64) NOT NULL COMMENT '字段名（如 labCode / courseName）',
    `old_value` VARCHAR(255) DEFAULT NULL COMMENT '旧值',
    `new_value` VARCHAR(255) DEFAULT NULL COMMENT '新值',
    `source_role` VARCHAR(16) NOT NULL COMMENT '来源角色：ADMIN / TEACHER（服务端判定）',
    `operator_id` BIGINT NOT NULL COMMENT '操作人 user_id',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_rev_target` (`target_type`, `target_id`),
    KEY `idx_rev_operator` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表/课程修订留痕表';

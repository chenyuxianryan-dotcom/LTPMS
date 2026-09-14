-- ========================================================
-- LTPMS 升级脚本：学期周历与实验室开放管理（2026-09-14）
-- 功能：semester.week1_start_date + holiday / lab_open_time / lab_open_exception 三表
-- 在已初始化的 ltpms_db 上执行一次即可
-- ========================================================

USE ltpms_db;

ALTER TABLE `semester`
    ADD COLUMN `week1_start_date` DATE DEFAULT NULL
        COMMENT '第1周起始日；空视为 start_date'
        AFTER `end_date`;

UPDATE `semester` SET `week1_start_date` = `start_date` WHERE `week1_start_date` IS NULL;

CREATE TABLE IF NOT EXISTS `holiday` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `holiday_date` DATE NOT NULL COMMENT '停课日',
    `name` VARCHAR(64) NOT NULL COMMENT '名称 如国庆节',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_holiday_date` (`holiday_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节假日表';

CREATE TABLE IF NOT EXISTS `lab_open_time` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `lab_code` VARCHAR(32) NOT NULL COMMENT '实验室编号（逻辑FK → laboratory.lab_code）',
    `semester_id` BIGINT NOT NULL COMMENT '逻辑FK → semester.id',
    `day_of_week` TINYINT NOT NULL COMMENT '星期几（1=周一 ... 7=周日）',
    `start_section` TINYINT NOT NULL COMMENT '开始节次',
    `end_section` TINYINT NOT NULL COMMENT '结束节次',
    `week_range` VARCHAR(32) NOT NULL COMMENT '周次范围（如 1-16）',
    `week_type` ENUM('ALL','ODD','EVEN') NOT NULL DEFAULT 'ALL' COMMENT '全部/单周/双周',
    `audience` VARCHAR(16) NOT NULL DEFAULT '全校' COMMENT '开放对象（全校/本院/指定专业/教师专用）',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '0=停用 1=启用',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_lot_semester` (`semester_id`),
    KEY `idx_lot_lab` (`lab_code`, `semester_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验室开放时段表';

CREATE TABLE IF NOT EXISTS `lab_open_exception` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `lab_code` VARCHAR(32) NOT NULL COMMENT '实验室编号（逻辑FK → laboratory.lab_code）',
    `semester_id` BIGINT NOT NULL COMMENT '逻辑FK → semester.id',
    `start_date` DATE NOT NULL COMMENT '开始日期（闭馆）',
    `end_date` DATE NOT NULL COMMENT '结束日期（闭馆）',
    `reason` VARCHAR(128) DEFAULT NULL COMMENT '原因',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_loe_lab` (`lab_code`, `semester_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验室开放例外日表';

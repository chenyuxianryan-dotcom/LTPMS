-- ========================================================
-- LTPMS 数据库初始化脚本
-- 字符集：utf8mb4_unicode_ci
-- 外键策略：逻辑外键，应用层保证完整性
-- ========================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- --------------------------------------------------------
-- 1. 用户表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(32) NOT NULL COMMENT '登录用户名（工号）',
    `password` VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
    `real_name` VARCHAR(32) NOT NULL COMMENT '真实姓名',
    `role` ENUM('ADMIN','TEACHER') NOT NULL DEFAULT 'TEACHER' COMMENT '角色',
    `department` VARCHAR(64) DEFAULT NULL COMMENT '所属院系',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    `is_default_pwd` TINYINT NOT NULL DEFAULT 1 COMMENT '1=默认密码（首次登录强制改密）',
    `failed_attempts` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    `locked_until` DATETIME DEFAULT NULL COMMENT '锁定截止时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- --------------------------------------------------------
-- 2. 实验室表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `laboratory`;
CREATE TABLE `laboratory` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `lab_code` VARCHAR(32) NOT NULL COMMENT '实验室编号（如 36-401）',
    `lab_name` VARCHAR(64) NOT NULL COMMENT '实验室名称',
    `manager` VARCHAR(32) DEFAULT NULL COMMENT '实验室负责人',
    `device_count` INT NOT NULL DEFAULT 0 COMMENT '设备数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=停用 1=启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lab_code` (`lab_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验室表';

-- --------------------------------------------------------
-- 3. 学期表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `semester`;
CREATE TABLE `semester` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `academic_year` VARCHAR(16) NOT NULL COMMENT '学年（如 2026-2027）',
    `semester_num` TINYINT NOT NULL COMMENT '学期序号（1或2）',
    `semester_name` VARCHAR(32) NOT NULL COMMENT '如 2026-2027-1',
    `start_date` DATE NOT NULL COMMENT '开始日期',
    `end_date` DATE NOT NULL COMMENT '结束日期',
    `status` ENUM('UPCOMING','ACTIVE','ENDED') NOT NULL DEFAULT 'UPCOMING' COMMENT '状态（查询时动态计算并回写缓存）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_semester_name` (`semester_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学期表';

-- --------------------------------------------------------
-- 4. 课表表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `schedule`;
CREATE TABLE `schedule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `semester_id` BIGINT NOT NULL COMMENT 'FK → semester.id',
    `course_code` VARCHAR(32) NOT NULL COMMENT '课程号',
    `course_name` VARCHAR(64) NOT NULL COMMENT '课程名',
    `total_hours` DECIMAL(5,1) NOT NULL COMMENT '实验总学时',
    `teacher_id` BIGINT NOT NULL COMMENT 'FK → user.id',
    `lab_code` VARCHAR(32) NOT NULL COMMENT '上课地点（实验室编号）',
    `class_names` VARCHAR(256) NOT NULL COMMENT '上课班级（逗号分隔）',
    `student_count` INT NOT NULL COMMENT '选课人数',
    `raw_schedule_text` VARCHAR(256) DEFAULT NULL COMMENT '原始上课时间文本（兜底）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_semester_id` (`semester_id`),
    KEY `idx_teacher_id` (`teacher_id`),
    KEY `idx_course_code` (`course_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表表';

-- --------------------------------------------------------
-- 5. 课表时间明细表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `schedule_time`;
CREATE TABLE `schedule_time` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `schedule_id` BIGINT NOT NULL COMMENT 'FK → schedule.id',
    `day_of_week` TINYINT NOT NULL COMMENT '星期几（1=周一 ... 7=周日）',
    `start_section` TINYINT NOT NULL COMMENT '开始节次',
    `end_section` TINYINT NOT NULL COMMENT '结束节次',
    `week_range` VARCHAR(32) DEFAULT NULL COMMENT '周次范围（如 1-16）',
    `week_type` ENUM('ALL','ODD','EVEN') NOT NULL DEFAULT 'ALL' COMMENT '全部/单周/双周',
    PRIMARY KEY (`id`),
    KEY `idx_schedule_id` (`schedule_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表时间明细表';

-- --------------------------------------------------------
-- 6. 实验课程表（由课表自动生成）
-- --------------------------------------------------------
DROP TABLE IF EXISTS `course`;
CREATE TABLE `course` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_code` VARCHAR(32) NOT NULL COMMENT '课程号',
    `course_name` VARCHAR(64) NOT NULL COMMENT '课程名',
    `semester_id` BIGINT NOT NULL COMMENT 'FK → semester.id',
    `teacher_id` BIGINT NOT NULL COMMENT 'FK → user.id',
    `total_hours` DECIMAL(5,1) NOT NULL COMMENT '实验总学时',
    `major` VARCHAR(128) DEFAULT NULL COMMENT '面向专业（从班级名解析）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_semester` (`course_code`, `semester_id`),
    KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验课程表';

-- --------------------------------------------------------
-- 7. 实验项目表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `experiment`;
CREATE TABLE `experiment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `experiment_code` VARCHAR(32) NOT NULL COMMENT '实验编号（自动生成：课程号-学期编码-流水号）',
    `course_id` BIGINT NOT NULL COMMENT 'FK → course.id',
    `experiment_name` VARCHAR(128) NOT NULL COMMENT '实验名称',
    `category` VARCHAR(32) NOT NULL COMMENT '实验类别（字典值）',
    `type` VARCHAR(32) NOT NULL COMMENT '实验类型（字典值）',
    `subject` VARCHAR(64) NOT NULL COMMENT '实验所属学科（字典值）',
    `requirement` VARCHAR(16) NOT NULL COMMENT '实验要求（字典值）',
    `experimenter_type` VARCHAR(16) NOT NULL COMMENT '实验者类别（字典值）',
    `group_size` INT NOT NULL DEFAULT 1 COMMENT '每组人数',
    `hours` DECIMAL(4,1) NOT NULL COMMENT '实验学时数',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_experiment_code` (`experiment_code`),
    KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验项目表';

-- --------------------------------------------------------
-- 8. 字典表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `dictionary`;
CREATE TABLE `dictionary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dict_type` VARCHAR(32) NOT NULL COMMENT '字典类型',
    `dict_label` VARCHAR(64) NOT NULL COMMENT '显示值',
    `dict_value` VARCHAR(64) NOT NULL COMMENT '存储值',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    PRIMARY KEY (`id`),
    KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典表';

-- --------------------------------------------------------
-- 9. 查询历史表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `query_history`;
CREATE TABLE `query_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT 'FK → user.id',
    `question` TEXT NOT NULL COMMENT '用户输入的自然语言问题',
    `generated_sql` TEXT DEFAULT NULL COMMENT '生成的SQL',
    `result_summary` VARCHAR(256) DEFAULT NULL COMMENT '结果摘要',
    `success` TINYINT NOT NULL DEFAULT 1 COMMENT '0=失败 1=成功',
    `role` VARCHAR(16) NOT NULL COMMENT '执行时用户角色（ADMIN/TEACHER）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查询历史表';

-- --------------------------------------------------------
-- 10. 站内通知表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT DEFAULT NULL COMMENT '接收人；NULL=全体广播',
    `title` VARCHAR(128) NOT NULL COMMENT '通知标题',
    `content` VARCHAR(512) NOT NULL COMMENT '通知正文',
    `type` VARCHAR(32) NOT NULL COMMENT 'SEMESTER_REMIND / SYSTEM',
    `ref_url` VARCHAR(128) DEFAULT NULL COMMENT '点击跳转前端路由',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0=未读 1=已读',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`),
    KEY `idx_type_created` (`type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知表';

-- --------------------------------------------------------
-- 11. 操作审计日志表
-- --------------------------------------------------------
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '操作人 FK → user.id',
    `action` VARCHAR(64) NOT NULL COMMENT '操作类型',
    `target_type` VARCHAR(32) NOT NULL COMMENT '目标实体',
    `target_id` BIGINT DEFAULT NULL COMMENT '目标记录ID',
    `detail` JSON DEFAULT NULL COMMENT '变更明细',
    `ip` VARCHAR(45) NOT NULL COMMENT '客户端IP',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';

SET FOREIGN_KEY_CHECKS = 1;

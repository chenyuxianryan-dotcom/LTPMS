-- ========================================================
-- LTPMS 升级脚本：课题项目模块（lab-project-mgmt 批次一）
-- 日期：2026-09-18
-- 依据：LTPMS_课题项目_技术方案.md §1（r2，规则①②已批准）
-- 外键策略与 init.sql 一致：逻辑外键，应用层保证完整性
-- ========================================================

SET NAMES utf8mb4;

-- --------------------------------------------------------
-- 0. user.role ENUM 扩容：新增 STUDENT
-- --------------------------------------------------------
ALTER TABLE `user`
    MODIFY COLUMN `role` ENUM('ADMIN','TEACHER','STUDENT') NOT NULL DEFAULT 'TEACHER'
    COMMENT '角色：ADMIN/TEACHER/STUDENT（STUDENT 登录名=学号，由教师端建档）';

-- --------------------------------------------------------
-- 1. 学生档案表（与 user 一对一；学号=登录名）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT 'FK → user.id（role=STUDENT）',
    `student_no` VARCHAR(32) NOT NULL COMMENT '学号（全局唯一键，与 user.username 同值）',
    `class_name` VARCHAR(64) DEFAULT NULL COMMENT '班级',
    `major` VARCHAR(128) DEFAULT NULL COMMENT '专业',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_no` (`student_no`),
    UNIQUE KEY `uk_student_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生档案表';

-- --------------------------------------------------------
-- 2. 课程选课名单表（批次三 S6 启用，表结构一期建齐——Q3 C-分步）
--    平行班粒度：course 按 (course_code, semester_id) 唯一只有一行，名单必须按班存（r2-B4）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `course_student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_id` BIGINT NOT NULL COMMENT 'FK → course.id',
    `student_id` BIGINT NOT NULL COMMENT 'FK → student.id',
    `class_name` VARCHAR(64) NOT NULL COMMENT '上课班级名——平行班区分',
    `source` ENUM('IMPORT','MANUAL') NOT NULL DEFAULT 'IMPORT' COMMENT '名单来源',
    `joined_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_student` (`course_id`, `student_id`),
    KEY `idx_cs_class` (`course_id`, `class_name`),
    KEY `idx_cs_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程选课名单表';

-- --------------------------------------------------------
-- 3. 课题表（UI 一律自称「课题」r2-H4；代码保留 project 命名）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lab_project` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_code` VARCHAR(32) NOT NULL COMMENT '课题编号 LP-YYYY-NNN',
    `project_name` VARCHAR(128) NOT NULL COMMENT '课题名称',
    `teacher_id` BIGINT NOT NULL COMMENT '归属教师 FK → user.id（行级隔离锚点）',
    `course_id` BIGINT DEFAULT NULL COMMENT '可选归类标签 FK → course.id（不产生成员关系）',
    `semester_id` BIGINT DEFAULT NULL COMMENT '可选归类标签 FK → semester.id',
    `description` TEXT DEFAULT NULL COMMENT '课题说明',
    `status` ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（归档 S7 批次三启用）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_code` (`project_code`),
    KEY `idx_lp_teacher` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课题表';

-- --------------------------------------------------------
-- 4. 课题成员表（带入即快照，课程名单变更不回写——Q3）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lab_project_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL COMMENT 'FK → lab_project.id',
    `student_id` BIGINT NOT NULL COMMENT 'FK → student.id',
    `source` ENUM('CREATE','MANUAL','COURSE_SNAPSHOT') NOT NULL DEFAULT 'MANUAL' COMMENT '带入来源',
    `joined_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_member` (`project_id`, `student_id`),
    KEY `idx_pm_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课题成员表（快照语义）';

-- --------------------------------------------------------
-- 5. 课题任务表（一任务一负责人；派多人=复制多条——Q4）
--    CLOSED：成员移出 / 规则①驳回后老师关闭；已提交(含已验收)不可删只能关闭
--    逾期不落字段：deadline<NOW() && status IN (NOT_STARTED,IN_PROGRESS) 查询时派生
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lab_project_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL COMMENT 'FK → lab_project.id',
    `title` VARCHAR(128) NOT NULL COMMENT '任务标题',
    `description` TEXT DEFAULT NULL COMMENT '任务说明',
    `assignee_student_id` BIGINT NOT NULL COMMENT '负责学生 FK → student.id',
    `status` ENUM('NOT_STARTED','IN_PROGRESS','SUBMITTED','ACCEPTED','REJECTED','CLOSED') NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态机',
    `deadline` DATETIME DEFAULT NULL COMMENT '截止时间',
    `dataset_id` BIGINT DEFAULT NULL COMMENT '可选关联数据集 FK → lab_dataset.id',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删（规则①：仅未提交任务可软删）',
    `created_by` BIGINT NOT NULL COMMENT '创建人 FK → user.id',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_task_project` (`project_id`),
    KEY `idx_task_assignee` (`assignee_student_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课题任务表';

-- --------------------------------------------------------
-- 6. 任务提交表（追加式：驳回重提=新行，历史保留——S2）
--    content/review_comment 仅本人+归属老师+ADMIN 可见（Q6，后端字段裁剪）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lab_task_submission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `task_id` BIGINT NOT NULL COMMENT 'FK → lab_project_task.id',
    `submitter_student_id` BIGINT NOT NULL COMMENT '提交人 FK → student.id',
    `content` TEXT NOT NULL COMMENT '提交说明',
    `dataset_version_id` BIGINT DEFAULT NULL COMMENT '可选交付物 FK → lab_dataset_version.id',
    `submitted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `reviewed_by` BIGINT DEFAULT NULL COMMENT '验收人 FK → user.id',
    `review_comment` VARCHAR(512) DEFAULT NULL COMMENT '验收意见（驳回必填）',
    `reviewed_at` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_sub_task` (`task_id`, `submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务提交表（追加式）';

-- --------------------------------------------------------
-- 7. 数据集表（批次二 S4 完整启用；本期建表+任务挂载所需最小读写）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lab_dataset` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `project_id` BIGINT NOT NULL COMMENT 'FK → lab_project.id',
    `dataset_name` VARCHAR(128) NOT NULL COMMENT '数据集名（项目内同名→409 二选一，r2-C4）',
    `description` VARCHAR(512) DEFAULT NULL,
    `latest_version` INT NOT NULL DEFAULT 1 COMMENT '最新版本号（版本链只增不覆盖）',
    `created_by` BIGINT NOT NULL COMMENT '创建人 FK → user.id',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ds_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课题数据集表';

-- --------------------------------------------------------
-- 8. 数据集版本表（只增链；stored_path 为服务端 UUID 文件名，绝不用用户原名拼路径 r2-B6）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lab_dataset_version` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `dataset_id` BIGINT NOT NULL COMMENT 'FK → lab_dataset.id',
    `version_no` INT NOT NULL COMMENT '版本号 v1/v2/...',
    `stored_path` VARCHAR(255) NOT NULL COMMENT '服务端 UUID 落盘相对路径',
    `original_filename` VARCHAR(255) NOT NULL COMMENT '原始文件名（仅展示/下载头用）',
    `file_size` BIGINT NOT NULL COMMENT '字节数',
    `file_type` VARCHAR(32) NOT NULL COMMENT '魔数校验结果类型（r2-B6，不信后缀）',
    `uploader_id` BIGINT NOT NULL COMMENT '上传者 FK → user.id',
    `note` VARCHAR(256) DEFAULT NULL COMMENT '版本说明',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ds_version` (`dataset_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据集版本链表';

-- --------------------------------------------------------
-- 9. 通用评论表（DATASET / DATASET_VERSION；S4 批次二前端启用，表一期建齐）
-- --------------------------------------------------------
CREATE TABLE IF NOT EXISTS `lab_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `target_type` VARCHAR(16) NOT NULL COMMENT 'DATASET / DATASET_VERSION',
    `target_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL COMMENT 'FK → user.id',
    `content` VARCHAR(512) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_cmt_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课题通用评论表';

-- --------------------------------------------------------
-- 10. schedule_revision：不改结构，仅扩 target_type 取值域
--     （VARCHAR(16) 通用列；新取值 'LAB_TASK' 用于 deadline 修改留痕 r2-B2）
-- --------------------------------------------------------
ALTER TABLE `schedule_revision`
    COMMENT = '课表/课程/课题任务修订留痕表（target_type: SCHEDULE / COURSE / LAB_TASK）';

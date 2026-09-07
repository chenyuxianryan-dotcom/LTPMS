-- ============================================================
-- LTPMS 数据库初始化脚本（幂等）
-- 依据：LTPMS_API设计文档.md V1.1 §1.2 表结构
-- 字符集：utf8mb4 / utf8mb4_unicode_ci（全局约定 §0，禁止 general_ci）
-- 外键策略：逻辑外键，应用层保证（不建物理 FOREIGN KEY）
-- 执行：mysql -u root -h 127.0.0.1 -P 3307 < init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS ltpms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ltpms_db;

-- ---------- 账号 ----------
-- 业务账号（Spring Boot 使用，ALL PRIVILEGES）
CREATE USER IF NOT EXISTS 'ltpms'@'%' IDENTIFIED BY 'ltpms';
GRANT ALL PRIVILEGES ON ltpms_db.* TO 'ltpms'@'%';
-- NL2SQL 只读账号（NLQ 服务使用，仅 SELECT —— 四层防护第三层，P0-6 修复项）
CREATE USER IF NOT EXISTS 'nlq_readonly'@'%' IDENTIFIED BY 'nlq_ro_2026';
GRANT SELECT ON ltpms_db.* TO 'nlq_readonly'@'%';
FLUSH PRIVILEGES;

-- ---------- 1.2.1 user 用户表 ----------
CREATE TABLE IF NOT EXISTS user (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  username        VARCHAR(32)  NOT NULL COMMENT '登录用户名(=工号)',
  password        VARCHAR(128) NOT NULL COMMENT 'BCrypt哈希',
  real_name       VARCHAR(32)  NOT NULL COMMENT '真实姓名',
  role            ENUM('ADMIN','TEACHER') NOT NULL DEFAULT 'TEACHER',
  department      VARCHAR(64)  NULL,
  phone           VARCHAR(20)  NULL,
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
  is_default_pwd  TINYINT      NOT NULL DEFAULT 1 COMMENT '1=默认密码,首登强制改密',
  failed_attempts INT          NOT NULL DEFAULT 0 COMMENT '连续失败次数,达5次锁定',
  locked_until    DATETIME     NULL COMMENT '锁定截止时间',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username),
  KEY idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ---------- 1.2.2 laboratory 实验室表 ----------
CREATE TABLE IF NOT EXISTS laboratory (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  lab_code     VARCHAR(32) NOT NULL COMMENT '实验室编号 如36-401',
  lab_name     VARCHAR(64) NOT NULL,
  manager      VARCHAR(32) NULL COMMENT '负责人',
  device_count INT         NOT NULL DEFAULT 0,
  status       TINYINT     NOT NULL DEFAULT 1,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_lab_code (lab_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验室表';

-- ---------- 1.2.3 semester 学期表 ----------
CREATE TABLE IF NOT EXISTS semester (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  academic_year VARCHAR(16) NOT NULL COMMENT '学年 如2026-2027',
  semester_num  TINYINT     NOT NULL COMMENT '学期序号 1或2',
  semester_name VARCHAR(32) NOT NULL COMMENT '如 2026-2027-1',
  start_date    DATE        NOT NULL,
  end_date      DATE        NOT NULL,
  status        ENUM('UPCOMING','ACTIVE','ENDED') NOT NULL DEFAULT 'UPCOMING' COMMENT '按日期动态计算',
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_semester_name (semester_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学期表';

-- ---------- 1.2.4 schedule 课表表 ----------
CREATE TABLE IF NOT EXISTS schedule (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  semester_id       BIGINT       NOT NULL,
  course_code       VARCHAR(32)  NOT NULL COMMENT '课程号',
  course_name       VARCHAR(64)  NOT NULL,
  total_hours       DECIMAL(5,1) NOT NULL COMMENT '实验总学时',
  teacher_id        BIGINT       NOT NULL COMMENT '逻辑FK->user.id',
  lab_code          VARCHAR(32)  NOT NULL COMMENT '上课地点',
  class_names       VARCHAR(256) NOT NULL COMMENT '上课班级(逗号分隔)',
  student_count     INT          NOT NULL COMMENT '选课人数',
  raw_schedule_text VARCHAR(256) NULL COMMENT '原始上课时间文本',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_semester (semester_id),
  KEY idx_teacher (teacher_id),
  KEY idx_course_code (course_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表表';

-- ---------- 1.2.5 schedule_time 课表时间明细 ----------
CREATE TABLE IF NOT EXISTS schedule_time (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  schedule_id   BIGINT      NOT NULL,
  day_of_week   TINYINT     NOT NULL COMMENT '1=周一..7=周日',
  start_section TINYINT     NOT NULL,
  end_section   TINYINT     NOT NULL,
  week_range    VARCHAR(32) NULL COMMENT '周次范围 如1-16',
  week_type     ENUM('ALL','ODD','EVEN') NOT NULL DEFAULT 'ALL',
  KEY idx_schedule (schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表时间明细表';

-- ---------- 1.2.6 course 实验课程表 ----------
CREATE TABLE IF NOT EXISTS course (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_code VARCHAR(32)  NOT NULL,
  course_name VARCHAR(64)  NOT NULL,
  semester_id BIGINT       NOT NULL,
  teacher_id  BIGINT       NOT NULL,
  total_hours DECIMAL(5,1) NOT NULL,
  major       VARCHAR(128) NULL COMMENT '面向专业(从班级名解析)',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_code_semester (course_code, semester_id),
  KEY idx_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验课程表';

-- ---------- 1.2.7 experiment 实验项目表 ----------
CREATE TABLE IF NOT EXISTS experiment (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  experiment_code  VARCHAR(32)  NOT NULL COMMENT '实验编号 自动生成 {课程号}-{学期编码}-{流水}',
  course_id        BIGINT       NOT NULL,
  experiment_name  VARCHAR(128) NOT NULL,
  category         VARCHAR(32)  NOT NULL COMMENT '实验类别(字典)',
  type             VARCHAR(32)  NOT NULL COMMENT '实验类型(字典)',
  subject          VARCHAR(64)  NOT NULL COMMENT '所属学科(字典)',
  requirement      VARCHAR(16)  NOT NULL COMMENT '实验要求(字典)',
  experimenter_type VARCHAR(16) NOT NULL COMMENT '实验者类别(字典)',
  group_size       INT          NOT NULL DEFAULT 1,
  hours            DECIMAL(4,1) NOT NULL,
  sort_order       INT          NOT NULL DEFAULT 0,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_experiment_code (experiment_code),
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验项目表';

-- ---------- 1.2.8 dictionary 字典表 ----------
CREATE TABLE IF NOT EXISTS dictionary (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  dict_type  VARCHAR(32) NOT NULL COMMENT 'category/type/subject/requirement/experimenter_type',
  dict_label VARCHAR(64) NOT NULL COMMENT '显示值',
  dict_value VARCHAR(64) NOT NULL COMMENT '存储值',
  sort_order INT         NOT NULL DEFAULT 0,
  status     TINYINT     NOT NULL DEFAULT 1,
  KEY idx_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典表';

-- ---------- 1.2.9 query_history 查询历史表 ----------
CREATE TABLE IF NOT EXISTS query_history (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id        BIGINT       NOT NULL,
  question       TEXT         NOT NULL,
  generated_sql  TEXT         NULL,
  result_summary VARCHAR(256) NULL,
  success        TINYINT      NOT NULL DEFAULT 1,
  role           VARCHAR(16)  NOT NULL COMMENT '执行时角色(排查权限问题)',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='查询历史表';

-- ---------- 1.2.10 notification 站内通知表 ----------
CREATE TABLE IF NOT EXISTS notification (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT       NULL COMMENT 'NULL=全体广播',
  title      VARCHAR(128) NOT NULL,
  content    VARCHAR(512) NOT NULL,
  type       VARCHAR(32)  NOT NULL COMMENT 'SEMESTER_REMIND / SYSTEM',
  ref_url    VARCHAR(128) NULL COMMENT '前端跳转路由',
  is_read    TINYINT      NOT NULL DEFAULT 0,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_read (user_id, is_read),
  KEY idx_type_time (type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知表';

-- ---------- 1.2.11 operation_log 操作审计日志表 ----------
CREATE TABLE IF NOT EXISTS operation_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  action      VARCHAR(64) NOT NULL COMMENT 'SCHEDULE_IMPORT/EXPERIMENT_CREATE...',
  target_type VARCHAR(32) NOT NULL,
  target_id   BIGINT      NULL,
  detail      JSON        NULL COMMENT '变更明细',
  ip          VARCHAR(45) NOT NULL COMMENT '客户端IP(兼容IPv6)',
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_time (user_id, created_at),
  KEY idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';

-- ============================================================
-- 种子数据
-- ============================================================

-- 初始管理员（密码= admin123 的 BCrypt 哈希，由后端同版本 BCryptPasswordEncoder 生成）
INSERT INTO user (username, password, real_name, role, status, is_default_pwd)
SELECT 'admin', '$2a$10$thr/YzDwh4/1wm83Mdsbsekh5.BIT3ze58dM08d3u5ks.4iOOZ2T.', '系统管理员', 'ADMIN', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM user WHERE username = 'admin');

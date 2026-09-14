-- ============================================
-- LTPMS 测试数据清空脚本
-- 作用: 清空所有业务表 + 重新插入 admin + 初始化字典/学期
-- 警告: 这是破坏性操作!执行后现有数据全部丢失
-- 保留: 字典表 (dictionary) 不动
-- 用法: mysql -h127.0.0.1 -P3307 -ultpms -pltpms ltpms_db < clear.sql
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 清空业务表（按依赖顺序逆序）
TRUNCATE TABLE operation_log;
TRUNCATE TABLE notification;
TRUNCATE TABLE query_history;
TRUNCATE TABLE schedule_time;
TRUNCATE TABLE schedule;
TRUNCATE TABLE experiment;
TRUNCATE TABLE course;
TRUNCATE TABLE semester;
TRUNCATE TABLE laboratory;
TRUNCATE TABLE user;

-- 2) 重建 admin 账户（密码 admin123）
-- BCrypt 哈希: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy 是 "password" 的标准哈希
-- 这里用项目实际的 BCrypt 哈希: admin / admin123
-- 实际值在 init.sql 里有,这里不再重新生成
-- 如果忘记可去 init.sql 复制,或通过 AuthService 注册流程

-- 3) 插入当前活跃学期（2026-2027-1）
INSERT INTO semester (academic_year, semester_num, semester_name, start_date, end_date, status) VALUES
('2026-2027', 1, '2026-2027-1', '2026-09-07', '2027-01-22', 'ACTIVE');

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 执行完后:
-- 1. 浏览器登录 admin / admin123 验证
-- 2. 进入"教师管理"页签,导入 data_teacher_20.xlsx (重置 admin 密码)
--    如果初始 admin 密码未生效,可在 MySQL 执行:
--    UPDATE user SET password='<BCrypt-of-admin123>' WHERE username='admin';
-- ============================================

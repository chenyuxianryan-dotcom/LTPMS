-- ============================================================
-- 目标：T015 韩艳 (id=33, 已改密) — 软件学院教师
-- 学期：2026-2027-1 (id=2, ACTIVE)
-- 范围：2 门课程 + 2 条课表 + 4 个实验项目
-- 作者：WorkBuddy / 日期：2026-09-10
-- 备注：事务化执行；失败自动回滚
-- ============================================================

START TRANSACTION;

-- 1) 课程表 course（2 条）
INSERT INTO course (course_code, course_name, semester_id, teacher_id, total_hours, major, created_at) VALUES
  ('SE301',  '软件工程实验',       2, 33, 32.0, '软件工程',     NOW()),
  ('WEB301', 'Web应用开发实验',     2, 33, 24.0, '软件工程',     NOW());

-- 2) 课表 schedule（2 条；与 course 的 lab_code/class_names/raw_schedule_text 对应）
INSERT INTO schedule (semester_id, course_code, course_name, total_hours, teacher_id, lab_code, class_names, student_count, raw_schedule_text, created_at) VALUES
  (2, 'SE301',  '软件工程实验',     32.0, 33, '36-201', '软件2401',     45, '周一第3-4节(1-16周)',              NOW()),
  (2, 'WEB301', 'Web应用开发实验',   24.0, 33, '37-102', '软件2402',     40, '周三第5-7节(1-16周)',              NOW());

-- 3) 实验项目 experiment（4 条；实验编号复刻 CodeGenerator 规则：课程号-学期编码-流水号）
--    学期编码：起始年(2026) + 学期数(1) = "20261"
INSERT INTO experiment (experiment_code, course_id, experiment_name, category, type, subject, requirement, experimenter_type, group_size, hours, sort_order, created_at, updated_at) VALUES
  ('SE301-20261-001',  (SELECT id FROM course WHERE course_code='SE301'  AND teacher_id=33), '需求分析与建模实验', 'basic',         'design',        'se', 'required', 'undergraduate', 2, 2.0, 1, NOW(), NOW()),
  ('SE301-20261-002',  (SELECT id FROM course WHERE course_code='SE301'  AND teacher_id=33), '系统设计与架构实验', 'comprehensive', 'design',        'se', 'required', 'undergraduate', 3, 4.0, 2, NOW(), NOW()),
  ('WEB301-20261-001', (SELECT id FROM course WHERE course_code='WEB301' AND teacher_id=33), 'RESTful API设计实现', 'basic',         'comprehensive', 'se', 'required', 'undergraduate', 1, 2.0, 1, NOW(), NOW()),
  ('WEB301-20261-002', (SELECT id FROM course WHERE course_code='WEB301' AND teacher_id=33), '前端组件开发实践',   'basic',         'comprehensive', 'se', 'elective', 'undergraduate', 2, 2.0, 2, NOW(), NOW());

COMMIT;

-- 4) 验证
SELECT '=== 课程 ===' AS step;
SELECT id, course_code, course_name, semester_id, teacher_id, total_hours, major
  FROM course WHERE teacher_id=33 ORDER BY id;

SELECT '=== 课表 ===' AS step;
SELECT id, semester_id, course_code, course_name, teacher_id, lab_code, class_names, student_count, raw_schedule_text
  FROM schedule WHERE teacher_id=33 ORDER BY id;

SELECT '=== 实验项目 ===' AS step;
SELECT e.id, e.experiment_code, e.experiment_name, e.category, e.type, e.subject, e.requirement,
       e.experimenter_type, e.group_size, e.hours, e.sort_order, e.course_id
  FROM experiment e LEFT JOIN course c ON e.course_id=c.id
  WHERE c.teacher_id=33 ORDER BY e.course_id, e.sort_order;

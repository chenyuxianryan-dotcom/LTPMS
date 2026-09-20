-- ========================================================
-- LTPMS 升级脚本：课题项目 × 实验项目 合并（统一「实验项目」模块，批次三）
-- 日期：2026-09-20
-- 依据：LTPMS_课题项目合并_需求拷问记录.md（V1.2，M-Q1~Q9 + R1~R4）§7 迁移军规
-- 内容：① lab_project.source 打标列 + 容器唯一约束
--       ② lab_project_member.source ENUM 扩第四值 AUTO_SNAPSHOT
--       ③ 存量容器迁移（INSERT...SELECT 幂等，可重放）
-- 回滚：database/rollback_20260920_unify_experiment.sql（随批交付）
-- 军规：打标写入零例外（容器=SYSTEM / 成员=AUTO_SNAPSHOT）；禁 UPDATE 存量行
-- ========================================================

SET NAMES utf8mb4;

-- --------------------------------------------------------
-- 1~2. schema 变更（幂等：按 information_schema 存在性判断，整脚本可重放）
--   ① lab_project 增加 source 打标列
--      SYSTEM = 课程容器（course_id/semester_id 必填）；USER = 自建课题（默认，兼容存量行）
--   ② 容器唯一约束 uk_course_semester_source：同一 (课程, 学期) 至多一个 SYSTEM 容器
--      （并发 ensure 兜底，M-Q3；USER 行标签为 NULL 或存量归类，ENUM 值不同不与之冲突）
--   ③ lab_project_member.source ENUM 扩第四值（容器懒生成时全量快照，M-Q6）
-- --------------------------------------------------------
DROP PROCEDURE IF EXISTS `ltpms_upgrade_20260920`;
DELIMITER $$
CREATE PROCEDURE `ltpms_upgrade_20260920`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'lab_project' AND COLUMN_NAME = 'source') THEN
        ALTER TABLE `lab_project`
            ADD COLUMN `source` ENUM('SYSTEM','USER') NOT NULL DEFAULT 'USER'
                COMMENT '课题来源：SYSTEM=课程容器（系统生成）/ USER=自建课题' AFTER `semester_id`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'lab_project' AND INDEX_NAME = 'uk_course_semester_source') THEN
        ALTER TABLE `lab_project`
            ADD UNIQUE KEY `uk_course_semester_source` (`course_id`, `semester_id`, `source`);
    END IF;

    IF (SELECT COLUMN_TYPE FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'lab_project_member' AND COLUMN_NAME = 'source') NOT LIKE '%AUTO_SNAPSHOT%' THEN
        ALTER TABLE `lab_project_member`
            MODIFY COLUMN `source` ENUM('CREATE','MANUAL','COURSE_SNAPSHOT','AUTO_SNAPSHOT') NOT NULL DEFAULT 'MANUAL'
            COMMENT '带入来源（AUTO_SNAPSHOT=容器生成时课程名单快照）';
    END IF;
END$$
DELIMITER ;
CALL `ltpms_upgrade_20260920`();
DROP PROCEDURE `ltpms_upgrade_20260920`;

-- --------------------------------------------------------
-- 3. 存量容器迁移（幂等可重放）
--    范围：已有实验目录（experiment 行存在）的课程行 → 每课程一个 SYSTEM 容器
--    编号：LP-<迁移年>-S<3位课程ID>（S 前缀与运行期 LP-YYYY-NNN 生成器互不撞号）
-- --------------------------------------------------------
INSERT INTO `lab_project`
    (`project_code`, `project_name`, `teacher_id`, `course_id`, `semester_id`,
     `description`, `status`, `source`, `created_at`, `updated_at`)
SELECT CONCAT('LP-', YEAR(NOW()), '-S', LPAD(c.id, 3, '0')),
       c.course_name,
       c.teacher_id,
       c.id,
       c.semester_id,
       '系统自动生成的课程容器（存量迁移，2026-09-20）',
       'ACTIVE',
       'SYSTEM',
       NOW(),
       NOW()
FROM `course` c
WHERE c.semester_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM `experiment` e WHERE e.course_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM `lab_project` p
                  WHERE p.course_id = c.id
                    AND p.semester_id = c.semester_id
                    AND p.source = 'SYSTEM');

-- --------------------------------------------------------
-- 4. 存量容器成员快照（course_student 全量带入，打标 AUTO_SNAPSHOT）
--    幂等：按 (project_id, student_id) 唯一键排除既有行
-- --------------------------------------------------------
INSERT INTO `lab_project_member` (`project_id`, `student_id`, `source`, `joined_at`)
SELECT p.id, cs.student_id, 'AUTO_SNAPSHOT', NOW()
FROM `lab_project` p
JOIN `course_student` cs ON cs.course_id = p.course_id
WHERE p.source = 'SYSTEM'
  AND NOT EXISTS (SELECT 1 FROM `lab_project_member` m
                  WHERE m.project_id = p.id
                    AND m.student_id = cs.student_id);

-- --------------------------------------------------------
-- 5. 验收自检（只读，人工核对；不落库）
-- --------------------------------------------------------
-- 容器数应与「有实验目录的课程数」一致：
-- SELECT COUNT(*) AS container_count FROM lab_project WHERE source = 'SYSTEM';
-- SELECT COUNT(*) AS course_with_exp FROM course c
--   WHERE c.semester_id IS NOT NULL AND EXISTS (SELECT 1 FROM experiment e WHERE e.course_id = c.id);
-- 快照成员数应与 course_student 对齐：
-- SELECT COUNT(*) AS snapshot_rows FROM lab_project_member WHERE source = 'AUTO_SNAPSHOT';

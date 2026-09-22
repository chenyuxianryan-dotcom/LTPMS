SET NAMES utf8mb4;

-- ============================================================
-- 知识库（AI 知识域·锁①）：制度与流程（管理员维护） + 课程资料（教师自助上传）
-- 2026-09-22
-- 依据：Doc/LTPMS_智能查询AI能力扩展蓝图.md「① 知识域」
-- 外键策略：逻辑外键，不建物理外键（与 lab_project 迁移一致）
-- 向量：embedding 为 float32 小端序字节串，维度=1024（text-embedding-v4），
--       4 字节/维 → 单条 4096 字节；维度变更时需重建全部段落
-- ============================================================

CREATE TABLE IF NOT EXISTS `knowledge_document` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `title`             VARCHAR(200) NOT NULL             COMMENT '文档标题',
    `category`          VARCHAR(20)  NOT NULL             COMMENT 'POLICY=制度与流程 / COURSE=课程资料',
    `course_id`         BIGINT       DEFAULT NULL         COMMENT '归属课程 course.id（仅 COURSE 类；POLICY 为 NULL）',
    `original_filename` VARCHAR(255) NOT NULL             COMMENT '原始文件名',
    `stored_path`       VARCHAR(255) NOT NULL             COMMENT '落盘文件名（uploads/kb 下，UUID 命名）',
    `file_type`         VARCHAR(16)  NOT NULL             COMMENT '文件类型：docx / pdf / txt / md',
    `file_size`         BIGINT       NOT NULL             COMMENT '文件字节数',
    `chunk_count`       INT          NOT NULL DEFAULT 0   COMMENT '切分段数（含向量）',
    `uploader_id`       BIGINT       NOT NULL             COMMENT '上传者 user.id',
    `deleted`           TINYINT(1)   NOT NULL DEFAULT 0   COMMENT '逻辑删除 1=已删',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_kd_category` (`category`, `deleted`),
    KEY `idx_kd_course` (`course_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='知识库文档（制度与流程 / 课程资料）';

CREATE TABLE IF NOT EXISTS `knowledge_chunk` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `document_id` BIGINT       NOT NULL             COMMENT 'FK → knowledge_document.id',
    `seq`         INT          NOT NULL             COMMENT '段序号（同一文档内从 1 起）',
    `heading`     VARCHAR(255) DEFAULT NULL         COMMENT '所属标题（切分时保留，用于出处展示）',
    `content`     TEXT         NOT NULL             COMMENT '段落原文',
    `embedding`   MEDIUMBLOB   NOT NULL             COMMENT '向量（float32 小端序，1024 维 = 4096 字节）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kc_doc_seq` (`document_id`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='知识库文档分段（含向量，检索侧只读）';

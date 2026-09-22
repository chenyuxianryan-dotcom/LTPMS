SET NAMES utf8mb4;

-- ============================================================
-- 用户通知偏好表：每用户每类型一行，默认全部开启
-- 类型常量：MORNING_BRIEF / DEADLINE_WARNING / STAGNATION_ALERT /
--           REVIEW_PENDING / SUBMISSION_ARRIVED / DATASET_NEW_VERSION
-- ============================================================

CREATE TABLE IF NOT EXISTS `notification_preference` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT       NOT NULL             COMMENT 'FK → user.id',
    `notification_type` VARCHAR(32) NOT NULL             COMMENT '通知类型常量',
    `enabled`          TINYINT(1)   NOT NULL DEFAULT 1   COMMENT '1=开启 0=关闭',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_type` (`user_id`, `notification_type`),
    KEY `idx_pref_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户通知偏好表（晨报/预警/提醒的订阅开关）';

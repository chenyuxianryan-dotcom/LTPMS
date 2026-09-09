-- ========================================================
-- LTPMS 升级脚本：用户头像字段（2026-09-09）
-- 功能：内置默认头像 + 教师头像更换 + 本地照片上传
-- 在已初始化的 ltpms_db 上执行一次即可
-- ========================================================

USE ltpms_db;

ALTER TABLE `user`
    ADD COLUMN `avatar` VARCHAR(255) DEFAULT NULL
        COMMENT '头像:preset:avatar-XX(内置)或/api/v1/files/avatar/x.png(上传);NULL=未设置(按用户ID取默认)'
        AFTER `phone`;

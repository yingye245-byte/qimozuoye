-- ============================================================
-- 应用数据库账号初始化脚本
-- 用途：创建本平台专用的低权限 MySQL 账号（只授权 campus_secondhand 库），
--       避免应用直接使用 root 账号连接数据库。
-- 执行方式（需要 root 权限）：
--   mysql -uroot -p < sql/db_init_user.sql
-- 为什么需要：如果手工禁用过 campus 账号，应用启动会报
--   Access denied for user 'campus'@'localhost'，重跑本脚本即可恢复。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `campus_secondhand`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'campus'@'localhost' IDENTIFIED WITH mysql_native_password BY 'campus123';

ALTER USER 'campus'@'localhost' IDENTIFIED WITH mysql_native_password BY 'campus123';

GRANT ALL PRIVILEGES ON `campus_secondhand`.* TO 'campus'@'localhost';

FLUSH PRIVILEGES;

SELECT 'APP ACCOUNT READY' AS result;
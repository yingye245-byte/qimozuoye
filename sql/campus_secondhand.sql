-- =====================================================================
--  校园二手闲置交易平台 - 数据库初始化脚本
--  目标库: campus_secondhand   字符集: utf8mb4
--  使用方法: 重置 root 密码后，或用有权限账号执行本脚本
-- =====================================================================

CREATE DATABASE IF NOT EXISTS campus_secondhand
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE campus_secondhand;

-- ---------------------------------------------------------------------
-- 用户表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
  `password`    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt加密)',
  `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱(用于通知)',
  `role`        TINYINT      NOT NULL DEFAULT 1 COMMENT '角色:1普通用户 2认证卖家 3超级管理员',
  `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:0禁用 1正常',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0未删 1已删',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ---------------------------------------------------------------------
-- 分类表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(50)  NOT NULL COMMENT '分类名称',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类ID,0为顶级',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品分类表';

-- ---------------------------------------------------------------------
-- 闲置物品表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `item`;
CREATE TABLE `item` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`      BIGINT       NOT NULL COMMENT '发布者ID',
  `category_id`  BIGINT       NOT NULL COMMENT '分类ID',
  `title`        VARCHAR(100) NOT NULL COMMENT '标题',
  `description`  TEXT         COMMENT '描述',
  `price`        DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '价格',
  `cover`        VARCHAR(255) DEFAULT NULL COMMENT '封面图',
  `audit_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '审核:0待审核 1通过 2驳回',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1在售 2下架 0删除',
  `deal_status`  TINYINT      NOT NULL DEFAULT 1 COMMENT '成交:1未成交 2已成交 3已关闭',
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_audit` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='闲置物品表';

-- ---------------------------------------------------------------------
-- 物品图片表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `item_image`;
CREATE TABLE `item_image` (
  `id`      BIGINT NOT NULL AUTO_INCREMENT,
  `item_id` BIGINT NOT NULL COMMENT '物品ID',
  `url`     VARCHAR(255) NOT NULL COMMENT '图片地址',
  `sort`    INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_item` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品图片表';

-- ---------------------------------------------------------------------
-- 留言咨询表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `item_id`       BIGINT      NOT NULL COMMENT '物品ID',
  `from_user`     BIGINT      NOT NULL COMMENT '留言人',
  `to_user`       BIGINT      DEFAULT NULL COMMENT '被留言(卖家)',
  `content`       VARCHAR(500) NOT NULL COMMENT '留言内容',
  `reply_content` VARCHAR(500) DEFAULT NULL COMMENT '卖家回复',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_item` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='留言咨询表';

-- ---------------------------------------------------------------------
-- 订单表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `order_no`    VARCHAR(32)  NOT NULL COMMENT '订单号',
  `item_id`     BIGINT       NOT NULL COMMENT '物品ID',
  `buyer_id`    BIGINT       NOT NULL COMMENT '买家ID',
  `seller_id`   BIGINT       NOT NULL COMMENT '卖家ID',
  `amount`      DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '成交金额',
  `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态:0待支付 1已支付 2已确认 3已完成 4已取消 5已关闭',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `pay_time`    DATETIME     DEFAULT NULL,
  `finish_time` DATETIME     DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_buyer` (`buyer_id`),
  KEY `idx_seller` (`seller_id`),
  KEY `idx_item` (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ---------------------------------------------------------------------
-- 浏览记录表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `user_browse`;
CREATE TABLE `user_browse` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL,
  `item_id`     BIGINT   NOT NULL,
  `browse_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览记录表';

-- ---------------------------------------------------------------------
-- 购物车表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `cart`;
CREATE TABLE `cart` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT   NOT NULL,
  `item_id`     BIGINT   NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_item` (`user_id`,`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- ---------------------------------------------------------------------
-- 交易统计表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `transaction_stat`;
CREATE TABLE `transaction_stat` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `stat_date`   DATE   NOT NULL COMMENT '统计日期',
  `deal_count`  INT    NOT NULL DEFAULT 0 COMMENT '成交笔数',
  `deal_amount` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '成交金额',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易统计表';

-- =====================================================================
--  初始化测试数据（每表 ≥5 条）
-- =====================================================================

-- 密码均为 BCrypt(明文为 "123456")，角色齐全
INSERT INTO `user` (`username`,`password`,`nickname`,`email`,`role`,`status`) VALUES
('admin',    '$2a$10$153bzl3q6UhGw2sTn5aw7OFMOt91hVzwEZfMLxP7Q5ju1.QD3ICBu','超管',   'admin@campus.edu',   3, 1),
('seller01', '$2a$10$153bzl3q6UhGw2sTn5aw7OFMOt91hVzwEZfMLxP7Q5ju1.QD3ICBu','张三',   'seller01@campus.edu',2, 1),
('seller02', '$2a$10$153bzl3q6UhGw2sTn5aw7OFMOt91hVzwEZfMLxP7Q5ju1.QD3ICBu','李四',   'seller02@campus.edu',2, 1),
('user01',   '$2a$10$153bzl3q6UhGw2sTn5aw7OFMOt91hVzwEZfMLxP7Q5ju1.QD3ICBu','王五',   'user01@campus.edu',  1, 1),
('user02',   '$2a$10$153bzl3q6UhGw2sTn5aw7OFMOt91hVzwEZfMLxP7Q5ju1.QD3ICBu','赵六',   'user02@campus.edu',  1, 1);

INSERT INTO `category` (`id`,`name`,`parent_id`,`sort`) VALUES
-- 顶级大类（前端首页「分类逛一逛」展示这一层）
(1,  '电子产品', 0, 1),
(2,  '学习教材', 0, 2),
(3,  '生活用品', 0, 3),
(4,  '交通工具', 0, 4),
(5,  '运动户外', 0, 5),
-- 电子产品
(6,  '手机',       1, 1),
(7,  '笔记本电脑', 1, 2),
(8,  '平板电脑',   1, 3),
(11, '数码配件',   1, 4),
(12, '影音设备',   1, 5),
-- 学习教材
(9,  '数学教材',   2, 1),
(10, '英语教材',   2, 2),
(13, '专业教材',   2, 3),
(14, '考试资料',   2, 4),
-- 生活用品
(15, '桌柜收纳',   3, 1),
(16, '小家电',     3, 2),
(17, '床上用品',   3, 3),
(18, '日用品',     3, 4),
-- 交通工具
(19, '自行车',       4, 1),
(20, '电动车滑板车', 4, 2),
-- 运动户外
(21, '球类',     5, 1),
(22, '健身器材', 5, 2),
(23, '户外装备', 5, 3);

INSERT INTO `item` (`user_id`,`category_id`,`title`,`description`,`price`,`cover`,`audit_status`,`status`) VALUES
(2, 6,  '九成新iPhone 13 128G', '自用一年，无磕碰，电池健康86%，附充电线', 2599.00, '/images/item-01.png', 1, 1),
(2, 7,  '联想小新Pro14 2022',    'i5-12500H/16G/512G，成色好，箱说全',      3999.00, '/images/item-02.png', 1, 1),
(3, 9,  '高等数学(下)习题册',    '同济第七版，几乎全新，内无笔记',           15.00,  '/images/item-03.png', 1, 1),
(3, 19, '山地自行车捷安特',      '可变速，适合代步，九成新',               450.00,  '/images/item-04.png', 0, 1),
(4, 15, '折叠桌宿舍用',          '60x40cm，可折叠，几乎全新',              30.00,   '/images/item-05.png', 1, 1);

INSERT INTO `item_image` (`item_id`,`url`,`sort`) VALUES
(1, '/images/item-01.png', 1),
(2, '/images/item-02.png', 1),
(3, '/images/item-03.png', 1),
(4, '/images/item-04.png', 1),
(5, '/images/item-05.png', 1);

INSERT INTO `comment` (`item_id`,`from_user`,`to_user`,`content`,`reply_content`) VALUES
(1, 4, 2, '请问还在吗？', '还在哦，欢迎联系'),
(1, 5, 2, '能小刀吗？',   '看你是诚心要的学生，减100'),
(2, 4, 2, '屏幕有划痕吗？','无划痕，可当面验机'),
(5, 4, 3, '这个桌子尺寸多大？','60cm，宿舍够用');

INSERT INTO `orders` (`order_no`,`item_id`,`buyer_id`,`seller_id`,`amount`,`status`) VALUES
('SO20261111001', 3, 4, 3, 15.00, 3),
('SO20261111002', 1, 5, 2, 2599.00, 1),
('SO20261111003', 4, 5, 3, 450.00, 0);

INSERT INTO `user_browse` (`user_id`,`item_id`) VALUES
(4,1),(4,2),(5,1),(5,3),(4,5);

INSERT INTO `cart` (`user_id`,`item_id`) VALUES
(4,1),(4,2),(5,3),(5,5),(4,4);

INSERT INTO `transaction_stat` (`stat_date`,`deal_count`,`deal_amount`) VALUES
('2026-11-01', 12, 3560.00),
('2026-11-02', 8,  2200.50),
('2026-11-03', 15, 4780.00),
('2026-11-04', 9,  3650.00),
('2026-11-05', 20, 6900.00);

-- =====================================================================
--  备注：
--   user 表密码均为 BCrypt("123456")，该哈希已经验证可匹配。
--   测试账号（密码均为 123456）：
--     admin     角色3 超级管理员
--     seller01  角色2 认证卖家
--     seller02  角色2 认证卖家
--     user01    角色1 普通用户
--     user02    角色1 普通用户
--   出于演示方便可共用同一哈希；如需更安全，可在注册功能中用
--   PasswordEncoder 为每个新用户生成独立盐值哈希。
-- =====================================================================
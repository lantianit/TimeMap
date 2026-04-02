-- ============================================================
-- 地图时迹 MapTrace - 完整数据库初始化脚本
-- 版本: 1.0.0
-- 说明: 包含所有表结构、索引，可直接用于全新部署
-- ============================================================

CREATE DATABASE IF NOT EXISTS maptrace DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE maptrace;

-- -----------------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_user` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `openid` VARCHAR(64) NOT NULL COMMENT '微信openid',
  `nickname` VARCHAR(64) DEFAULT '' COMMENT '昵称',
  `avatar_url` VARCHAR(512) DEFAULT '' COMMENT '头像URL',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `country_code` VARCHAR(10) DEFAULT NULL COMMENT '手机号国家区号',
  `gender` TINYINT DEFAULT 0 COMMENT '性别 0-未知 1-男 2-女',
  `country` VARCHAR(50) DEFAULT '' COMMENT '国家',
  `province` VARCHAR(50) DEFAULT '' COMMENT '省份',
  `city` VARCHAR(50) DEFAULT '' COMMENT '城市',
  `profile_completed` TINYINT DEFAULT 0 COMMENT '资料是否完善 0-否 1-是',
  `mute_until` DATETIME DEFAULT NULL COMMENT '禁言截止时间',
  `ban_upload_until` DATETIME DEFAULT NULL COMMENT '禁止上传截止时间',
  `is_banned` TINYINT(1) DEFAULT 0 COMMENT '是否封号: 0否 1是',
  `violation_count` INT DEFAULT 0 COMMENT '违规次数',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  INDEX `idx_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------------
-- 2. 照片表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_photo` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '上传用户ID',
  `image_url` VARCHAR(512) NOT NULL COMMENT '图片COS存储URL',
  `thumbnail_url` VARCHAR(512) DEFAULT '' COMMENT '缩略图URL',
  `description` VARCHAR(500) DEFAULT '' COMMENT '图片描述',
  `longitude` DOUBLE NOT NULL COMMENT '经度',
  `latitude` DOUBLE NOT NULL COMMENT '纬度',
  `location_name` VARCHAR(200) DEFAULT '' COMMENT '地点名称',
  `district` VARCHAR(100) DEFAULT '' COMMENT '行政区划（区/县）',
  `visibility` TINYINT NOT NULL DEFAULT 2 COMMENT '可见性: 0=仅自己 1=互关可见 2=所有人可见',
  `photo_date` DATE NOT NULL COMMENT '拍摄日期',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_photo_date` (`photo_date`),
  INDEX `idx_location` (`latitude`, `longitude`),
  INDEX `idx_district_date` (`district`, `photo_date`, `deleted`),
  INDEX `idx_visibility` (`visibility`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='照片表';

-- -----------------------------------------------------------
-- 3. 照片点赞表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_photo_like` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `photo_id` BIGINT NOT NULL COMMENT '照片ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_photo_user` (`photo_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='照片点赞表';

-- -----------------------------------------------------------
-- 4. 评论表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_comment` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `photo_id` BIGINT NOT NULL COMMENT '照片ID',
  `user_id` BIGINT NOT NULL COMMENT '评论用户ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父评论ID，顶级为0',
  `reply_to_user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '被回复者ID',
  `content` VARCHAR(500) NOT NULL COMMENT '评论内容',
  `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  `reply_count` INT NOT NULL DEFAULT 0 COMMENT '回复数',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  INDEX `idx_photo_id` (`photo_id`, `parent_id`, `create_time`),
  INDEX `idx_parent_id` (`parent_id`, `create_time`),
  INDEX `idx_user_time` (`user_id`, `create_time`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- -----------------------------------------------------------
-- 5. 评论点赞表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_comment_like` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `comment_id` BIGINT NOT NULL COMMENT '评论ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_comment_user` (`comment_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论点赞表';

-- -----------------------------------------------------------
-- 6. 私信表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_message` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `from_user_id` BIGINT NOT NULL COMMENT '发送者ID',
  `to_user_id` BIGINT NOT NULL COMMENT '接收者ID',
  `content` VARCHAR(2000) NOT NULL COMMENT '消息内容',
  `msg_type` VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '消息类型: text/image',
  `read_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  INDEX `idx_conversation` (`from_user_id`, `to_user_id`, `create_time`),
  INDEX `idx_to_user` (`to_user_id`, `read_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信表';

-- -----------------------------------------------------------
-- 7. 互动通知表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_notification` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '接收通知的用户ID',
  `from_user_id` BIGINT NOT NULL COMMENT '触发通知的用户ID',
  `type` VARCHAR(20) NOT NULL COMMENT '通知类型: comment/reply/photo_like/comment_like',
  `photo_id` BIGINT DEFAULT NULL COMMENT '关联照片ID',
  `comment_id` BIGINT DEFAULT NULL COMMENT '关联评论ID',
  `content` VARCHAR(200) DEFAULT '' COMMENT '通知摘要',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_read` (`user_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='互动通知表';

-- -----------------------------------------------------------
-- 8. 举报表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_report` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '举报人ID',
  `target_type` VARCHAR(20) NOT NULL COMMENT '被举报类型: photo/comment',
  `target_id` BIGINT NOT NULL COMMENT '被举报对象ID',
  `reason` VARCHAR(50) NOT NULL COMMENT '举报原因',
  `description` VARCHAR(500) DEFAULT '' COMMENT '详细描述',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理 2已驳回',
  `handle_result` VARCHAR(500) DEFAULT '' COMMENT '处理结果/驳回原因',
  `handled_by` BIGINT DEFAULT NULL COMMENT '处理人ID',
  `handled_time` DATETIME DEFAULT NULL COMMENT '处理时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_target` (`target_type`, `target_id`),
  INDEX `idx_status_create_time` (`status`, `create_time`),
  INDEX `idx_user_create_time` (`user_id`, `create_time`),
  INDEX `idx_target_status` (`target_type`, `target_id`, `status`),
  INDEX `idx_handler_time` (`handled_by`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报表';

-- -----------------------------------------------------------
-- 9. 用户违规记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_user_violation` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '违规用户ID',
  `report_id` BIGINT DEFAULT NULL COMMENT '关联举报ID',
  `violation_type` VARCHAR(50) NOT NULL COMMENT '违规类型: content_removed/warning/mute/ban_upload/ban_account',
  `reason` VARCHAR(500) DEFAULT '' COMMENT '违规原因',
  `target_type` VARCHAR(30) DEFAULT '' COMMENT '被处置内容类型',
  `target_id` BIGINT DEFAULT NULL COMMENT '被处置内容ID',
  `punishment_type` VARCHAR(50) DEFAULT '' COMMENT '处罚类型: warning/mute/ban_upload/ban_account',
  `punishment_days` INT DEFAULT 0 COMMENT '处罚天数(0表示永久或仅警告)',
  `handled_by` BIGINT DEFAULT NULL COMMENT '处理管理员ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_report_id` (`report_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户违规记录';

-- -----------------------------------------------------------
-- 10. 申诉记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_appeal` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '申诉人ID',
  `type` VARCHAR(30) NOT NULL COMMENT '申诉类型: content_removed/report_rejected',
  `report_id` BIGINT DEFAULT NULL COMMENT '关联举报ID',
  `reason` VARCHAR(1000) NOT NULL COMMENT '申诉原因',
  `status` TINYINT DEFAULT 0 COMMENT '状态: 0待处理 1已采纳 2已驳回',
  `handle_result` VARCHAR(500) DEFAULT '' COMMENT '处理结果',
  `handled_by` BIGINT DEFAULT NULL COMMENT '处理管理员ID',
  `handled_time` DATETIME DEFAULT NULL COMMENT '处理时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_report_id` (`report_id`),
  INDEX `idx_status` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申诉记录';

-- -----------------------------------------------------------
-- 11. 管理员账号表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_admin_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(50) NOT NULL COMMENT '登录账号',
  `password_hash` VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
  `nickname` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '显示名称',
  `role` VARCHAR(30) NOT NULL DEFAULT 'moderator' COMMENT '角色: super_admin/moderator/viewer',
  `linked_user_id` BIGINT DEFAULT NULL COMMENT '关联小程序用户ID',
  `is_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `must_change_password` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否需要强制修改密码',
  `password_changed_at` DATETIME DEFAULT NULL COMMENT '最后修改密码时间',
  `password_history` TEXT DEFAULT NULL COMMENT '最近3次密码hash(JSON数组)',
  `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` VARCHAR(50) DEFAULT '' COMMENT '最后登录IP',
  `login_fail_count` INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `lock_until` DATETIME DEFAULT NULL COMMENT '锁定截止时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员账号';

-- -----------------------------------------------------------
-- 12. 管理员操作日志表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_admin_log` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `admin_user_id` BIGINT NOT NULL COMMENT '管理员用户ID',
  `action` VARCHAR(50) NOT NULL COMMENT '操作类型',
  `target_type` VARCHAR(30) DEFAULT '' COMMENT '操作对象类型',
  `target_id` BIGINT DEFAULT NULL COMMENT '操作对象ID',
  `detail` VARCHAR(2000) DEFAULT '' COMMENT '操作详情(JSON)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_admin_user_id` (`admin_user_id`, `create_time`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作日志';

-- -----------------------------------------------------------
-- 13. 管理员登录日志表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_admin_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `admin_account_id` BIGINT NOT NULL COMMENT '管理员账号ID',
  `action` VARCHAR(30) NOT NULL COMMENT 'login_success/login_fail/logout/password_change',
  `ip` VARCHAR(50) DEFAULT '' COMMENT '客户端IP',
  `user_agent` VARCHAR(500) DEFAULT '' COMMENT '浏览器UA',
  `detail` VARCHAR(500) DEFAULT '' COMMENT '附加信息',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_account_time` (`admin_account_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员登录日志';

-- -----------------------------------------------------------
-- 14. COS文件延迟删除记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_cos_delete_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_url` VARCHAR(512) NOT NULL COMMENT '文件URL',
  `content_type` VARCHAR(20) NOT NULL COMMENT '内容类型: photo/comment',
  `content_id` BIGINT NOT NULL COMMENT '关联内容ID',
  `delete_reason` VARCHAR(50) NOT NULL COMMENT '删除原因: report_resolved/user_delete',
  `scheduled_delete_time` DATETIME NOT NULL COMMENT '计划删除时间（30天后）',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已物理删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_scheduled_time` (`scheduled_delete_time`, `is_deleted`),
  INDEX `idx_content` (`content_type`, `content_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='COS文件延迟删除记录';

-- -----------------------------------------------------------
-- 15. 关注关系表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_follow` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '关注者ID（谁发起关注）',
  `target_user_id` BIGINT NOT NULL COMMENT '被关注者ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_target` (`user_id`, `target_user_id`),
  INDEX `idx_target_user` (`target_user_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系表';

-- -----------------------------------------------------------
-- 16. 用户订阅消息授权记录表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_subscribe_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `template_id` VARCHAR(100) NOT NULL COMMENT '模板ID',
  `remaining_count` INT NOT NULL DEFAULT 0 COMMENT '剩余可发送次数',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_template` (`user_id`, `template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅消息授权记录';

-- ============================================================
-- 默认超级管理员由应用启动时自动创建（AdminAccountInitializer）
-- 初始密码通过环境变量 ADMIN_INIT_PASSWORD 配置，未配置则自动生成随机密码（查看启动日志）
-- ============================================================

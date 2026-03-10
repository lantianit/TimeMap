-- v08: Web 管理后台 — 数据库迁移

-- 1. 管理员账号表
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
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员账号';

-- 2. 管理员登录日志表
CREATE TABLE IF NOT EXISTS `t_admin_login_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `admin_account_id` BIGINT NOT NULL COMMENT '管理员账号ID',
  `action` VARCHAR(30) NOT NULL COMMENT 'login_success/login_fail/logout/password_change',
  `ip` VARCHAR(50) DEFAULT '' COMMENT '客户端IP',
  `user_agent` VARCHAR(500) DEFAULT '' COMMENT '浏览器UA',
  `detail` VARCHAR(500) DEFAULT '' COMMENT '附加信息',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_account_time` (`admin_account_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员登录日志';

-- 3. 默认超级管理员由应用启动时自动创建（见 AdminAccountInitializer）
-- 默认账号: admin  密码: Admin@2026

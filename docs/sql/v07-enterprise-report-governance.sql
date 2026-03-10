-- v07: 企业级举报治理系统 — 数据库迁移

-- 1. 用户表：新增处罚字段
ALTER TABLE `t_user`
  ADD COLUMN `mute_until` DATETIME DEFAULT NULL COMMENT '禁言截止时间' AFTER `profile_completed`,
  ADD COLUMN `ban_upload_until` DATETIME DEFAULT NULL COMMENT '禁止上传截止时间' AFTER `mute_until`,
  ADD COLUMN `is_banned` TINYINT(1) DEFAULT 0 COMMENT '是否封号: 0否 1是' AFTER `ban_upload_until`,
  ADD COLUMN `violation_count` INT DEFAULT 0 COMMENT '违规次数' AFTER `is_banned`;

-- 2. 用户违规记录表
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
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_report_id` (`report_id`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户违规记录';

-- 3. 申诉记录表
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
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_report_id` (`report_id`),
  INDEX `idx_status` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申诉记录';

-- 4. 管理员操作日志表
CREATE TABLE IF NOT EXISTS `t_admin_log` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `admin_user_id` BIGINT NOT NULL COMMENT '管理员用户ID',
  `action` VARCHAR(50) NOT NULL COMMENT '操作类型: resolve_report/reject_report/punish_user/resolve_appeal/reject_appeal',
  `target_type` VARCHAR(30) DEFAULT '' COMMENT '操作对象类型',
  `target_id` BIGINT DEFAULT NULL COMMENT '操作对象ID',
  `detail` VARCHAR(2000) DEFAULT '' COMMENT '操作详情(JSON)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_admin_user_id` (`admin_user_id`, `create_time`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作日志';

-- 5. 举报表新增索引（用于聚合查询）
ALTER TABLE `t_report`
  ADD INDEX `idx_target` (`target_type`, `target_id`);

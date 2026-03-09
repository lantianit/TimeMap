-- 互动通知表
CREATE TABLE IF NOT EXISTS `t_notification` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL COMMENT '接收通知的用户ID',
  `from_user_id` BIGINT NOT NULL COMMENT '触发通知的用户ID',
  `type` VARCHAR(20) NOT NULL COMMENT 'comment/reply/photo_like/comment_like',
  `photo_id` BIGINT DEFAULT NULL COMMENT '关联照片ID',
  `comment_id` BIGINT DEFAULT NULL COMMENT '关联评论ID',
  `content` VARCHAR(200) DEFAULT '' COMMENT '通知摘要',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `create_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_user_read` (`user_id`, `is_read`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 举报表
CREATE TABLE IF NOT EXISTS `t_report` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL COMMENT '举报人ID',
  `target_type` VARCHAR(20) NOT NULL COMMENT 'photo/comment',
  `target_id` BIGINT NOT NULL COMMENT '被举报对象ID',
  `reason` VARCHAR(50) NOT NULL COMMENT '举报原因',
  `description` VARCHAR(500) DEFAULT '' COMMENT '详细描述',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已处理 2已驳回',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  INDEX `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

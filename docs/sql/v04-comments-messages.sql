-- 评论表
CREATE TABLE IF NOT EXISTS `t_comment` (
  `id` BIGINT NOT NULL,
  `photo_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父评论ID，顶级为0',
  `reply_to_user_id` BIGINT NOT NULL DEFAULT 0 COMMENT '被回复者ID',
  `content` VARCHAR(500) NOT NULL,
  `like_count` INT NOT NULL DEFAULT 0,
  `reply_count` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_photo_id` (`photo_id`, `parent_id`, `create_time`),
  INDEX `idx_parent_id` (`parent_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 评论点赞表
CREATE TABLE IF NOT EXISTS `t_comment_like` (
  `id` BIGINT NOT NULL,
  `comment_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `create_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_comment_user` (`comment_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 私信表
CREATE TABLE IF NOT EXISTS `t_message` (
  `id` BIGINT NOT NULL,
  `from_user_id` BIGINT NOT NULL,
  `to_user_id` BIGINT NOT NULL,
  `content` VARCHAR(2000) NOT NULL,
  `msg_type` VARCHAR(20) NOT NULL DEFAULT 'text',
  `read_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `create_time` DATETIME DEFAULT NULL,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_conversation` (`from_user_id`, `to_user_id`, `create_time`),
  INDEX `idx_to_user` (`to_user_id`, `read_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 迁移脚本: 新增关注表 + 照片可见性字段
-- 适用于: 从旧版本升级到支持互关/可见性功能
-- ============================================================

USE maptrace;

-- 1. 创建关注关系表
CREATE TABLE IF NOT EXISTS `t_follow` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '关注者ID（谁发起关注）',
  `target_user_id` BIGINT NOT NULL COMMENT '被关注者ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_target` (`user_id`, `target_user_id`),
  INDEX `idx_target_user` (`target_user_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系表';

-- 2. t_photo 表新增 visibility 字段（如果不存在）
SET @col_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = 'maptrace' AND TABLE_NAME = 't_photo' AND COLUMN_NAME = 'visibility'
);

SET @sql = IF(@col_exists = 0,
  'ALTER TABLE `t_photo` ADD COLUMN `visibility` TINYINT NOT NULL DEFAULT 2 COMMENT ''可见性: 0=仅自己 1=互关可见 2=所有人可见'' AFTER `district`',
  'SELECT ''visibility column already exists''');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 添加 visibility 索引（如果不存在）
SET @idx_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = 'maptrace' AND TABLE_NAME = 't_photo' AND INDEX_NAME = 'idx_visibility'
);

SET @sql2 = IF(@idx_exists = 0,
  'ALTER TABLE `t_photo` ADD INDEX `idx_visibility` (`visibility`, `deleted`)',
  'SELECT ''idx_visibility already exists''');

PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

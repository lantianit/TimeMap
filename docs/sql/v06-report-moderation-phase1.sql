-- 举报治理一期：补齐处理字段与索引

ALTER TABLE `t_report`
  ADD COLUMN `handle_result` VARCHAR(500) DEFAULT '' COMMENT '处理结果/驳回原因' AFTER `status`,
  ADD COLUMN `handled_by` BIGINT DEFAULT NULL COMMENT '处理人ID' AFTER `handle_result`,
  ADD COLUMN `handled_time` DATETIME DEFAULT NULL COMMENT '处理时间' AFTER `handled_by`;

ALTER TABLE `t_report`
  ADD INDEX `idx_status_create_time` (`status`, `create_time`),
  ADD INDEX `idx_user_create_time` (`user_id`, `create_time`),
  ADD INDEX `idx_target_status` (`target_type`, `target_id`, `status`);

ALTER TABLE t_user
    ADD COLUMN phone VARCHAR(20) DEFAULT NULL COMMENT '手机号' AFTER avatar_url,
    ADD COLUMN country_code VARCHAR(10) DEFAULT NULL COMMENT '手机号国家区号' AFTER phone;

CREATE INDEX idx_user_phone ON t_user(phone);

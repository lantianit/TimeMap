CREATE DATABASE IF NOT EXISTS timemap DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE timemap;

CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT NOT NULL COMMENT '主键',
    openid VARCHAR(64) NOT NULL COMMENT '微信openid',
    nickname VARCHAR(64) DEFAULT '' COMMENT '昵称',
    avatar_url VARCHAR(512) DEFAULT '' COMMENT '头像URL',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    country_code VARCHAR(10) DEFAULT NULL COMMENT '手机号国家区号',
    gender TINYINT DEFAULT 0 COMMENT '性别 0-未知 1-男 2-女',
    country VARCHAR(50) DEFAULT '' COMMENT '国家',
    province VARCHAR(50) DEFAULT '' COMMENT '省份',
    city VARCHAR(50) DEFAULT '' COMMENT '城市',
    profile_completed TINYINT DEFAULT 0 COMMENT '资料是否完善 0-否 1-是',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS t_photo (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '上传用户ID',
    image_url VARCHAR(512) NOT NULL COMMENT '图片COS存储URL',
    thumbnail_url VARCHAR(512) DEFAULT '' COMMENT '缩略图URL',
    description VARCHAR(500) DEFAULT '' COMMENT '图片描述',
    longitude DOUBLE NOT NULL COMMENT '经度',
    latitude DOUBLE NOT NULL COMMENT '纬度',
    location_name VARCHAR(200) DEFAULT '' COMMENT '地点名称',
    photo_date DATE NOT NULL COMMENT '拍摄日期',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_photo_date (photo_date),
    INDEX idx_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片表';

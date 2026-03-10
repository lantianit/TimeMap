package com.timemap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {
    /**
     * 管理员用户 ID 白名单，一期先用配置方案，避免引入完整角色系统。
     */
    private String userIds = "";
}

package com.timemap.model.dto;

import lombok.Data;

@Data
public class AdminAccountResponse {
    private Long id;
    private String username;
    private String nickname;
    private String role;
    private Long linkedUserId;
    private Boolean isEnabled;
    private String lastLoginTime;
    private String lastLoginIp;
    private String createTime;
}

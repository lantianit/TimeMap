package com.timemap.model.dto;

import lombok.Data;

@Data
public class AdminLoginResponse {
    private String token;
    private String role;
    private String nickname;
    private Long adminId;
    private Boolean mustChangePassword;
}

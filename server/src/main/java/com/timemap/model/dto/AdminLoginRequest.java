package com.timemap.model.dto;

import lombok.Data;

@Data
public class AdminLoginRequest {
    private String username;
    private String password;
    private Boolean rememberMe;
}

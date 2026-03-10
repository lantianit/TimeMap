package com.timemap.model.dto;

import lombok.Data;

@Data
public class CreateAdminAccountRequest {
    private String username;
    private String password;
    private String nickname;
    private String role;
    private Long linkedUserId;
}

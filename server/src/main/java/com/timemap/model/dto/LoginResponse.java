package com.timemap.model.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private Long userId;
    private Boolean isNew;
    private Boolean needPhone;
    private Boolean needProfile;

    public static LoginResponse of(String token, Long userId, Boolean isNew,
                                   Boolean needPhone, Boolean needProfile) {
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(userId);
        resp.setIsNew(isNew);
        resp.setNeedPhone(needPhone);
        resp.setNeedProfile(needProfile);
        return resp;
    }
}

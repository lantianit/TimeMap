package com.timemap.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserInfoResponse {

    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Integer gender;
    private String country;
    private String province;
    private String city;
    private Boolean profileCompleted;
    private LocalDateTime createTime;

    public static UserInfoResponse from(com.timemap.model.entity.User user) {
        UserInfoResponse resp = new UserInfoResponse();
        resp.setUserId(user.getId());
        resp.setNickname(user.getNickname());
        resp.setAvatarUrl(user.getAvatarUrl());
        resp.setGender(user.getGender());
        resp.setCountry(user.getCountry());
        resp.setProvince(user.getProvince());
        resp.setCity(user.getCity());
        resp.setProfileCompleted(user.getProfileCompleted() == 1);
        resp.setCreateTime(user.getCreateTime());
        return resp;
    }
}

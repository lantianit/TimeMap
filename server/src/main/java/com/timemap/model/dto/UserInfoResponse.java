package com.timemap.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserInfoResponse {

    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String phoneMasked;
    private Boolean phoneBound;
    private Boolean needPhone;
    private Boolean needProfile;
    private Integer gender;
    private String country;
    private String province;
    private String city;
    private Boolean profileCompleted;
    private Boolean isAdmin;
    private LocalDateTime createTime;
    private LocalDateTime muteUntil;
    private LocalDateTime banUploadUntil;
    private Boolean isBanned;
    private Integer violationCount;

    public static UserInfoResponse from(com.timemap.model.entity.User user) {
        UserInfoResponse resp = new UserInfoResponse();
        resp.setUserId(user.getId());
        resp.setNickname(user.getNickname());
        resp.setAvatarUrl(user.getAvatarUrl());
        resp.setPhoneMasked(maskPhone(user.getPhone()));
        resp.setPhoneBound(user.getPhone() != null && !user.getPhone().isBlank());
        resp.setNeedPhone(user.getPhone() == null || user.getPhone().isBlank());
        resp.setNeedProfile(user.getNickname() == null || user.getNickname().isBlank()
                || user.getAvatarUrl() == null || user.getAvatarUrl().isBlank());
        resp.setGender(user.getGender());
        resp.setCountry(user.getCountry());
        resp.setProvince(user.getProvince());
        resp.setCity(user.getCity());
        resp.setProfileCompleted(user.getProfileCompleted() != null && user.getProfileCompleted() == 1);
        resp.setCreateTime(user.getCreateTime());
        resp.setMuteUntil(user.getMuteUntil());
        resp.setBanUploadUntil(user.getBanUploadUntil());
        resp.setIsBanned(user.getIsBanned() != null && user.getIsBanned() == 1);
        resp.setViolationCount(user.getViolationCount() != null ? user.getViolationCount() : 0);
        return resp;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        if (phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}

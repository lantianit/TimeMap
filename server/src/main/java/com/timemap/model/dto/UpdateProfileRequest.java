package com.timemap.model.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String nickname;

    private String avatarUrl;

    private Integer gender;

    private String country;

    private String province;

    private String city;
}

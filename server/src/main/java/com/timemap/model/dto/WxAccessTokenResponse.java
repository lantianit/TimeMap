package com.timemap.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WxAccessTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private Long expiresIn;

    private Integer errcode;

    private String errmsg;

    public boolean isSuccess() {
        return errcode == null || errcode == 0;
    }
}

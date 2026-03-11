package com.timemap.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WxPhoneNumberResponse {

    private Integer errcode;

    private String errmsg;

    @JsonProperty("phone_info")
    private PhoneInfo phoneInfo;

    public boolean isSuccess() {
        return errcode == null || errcode == 0;
    }

    @Data
    public static class PhoneInfo {
        private String phoneNumber;
        private String purePhoneNumber;
        private String countryCode;
    }
}

package com.timemap.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindPhoneRequest {

    @NotBlank(message = "手机号授权code不能为空")
    private String code;
}

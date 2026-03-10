package com.timemap.model.dto;

import lombok.Data;

@Data
public class HandleAppealRequest {
    private Long appealId;
    private String handleResult;
}

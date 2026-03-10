package com.timemap.model.dto;

import lombok.Data;

@Data
public class AppealSubmitRequest {
    private String type;
    private Long reportId;
    private String reason;
}

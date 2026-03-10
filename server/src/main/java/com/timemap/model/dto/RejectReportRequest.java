package com.timemap.model.dto;

import lombok.Data;

@Data
public class RejectReportRequest {
    private Long reportId;
    private String handleResult;
}

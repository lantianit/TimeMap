package com.timemap.model.dto;

import lombok.Data;

@Data
public class ResolveReportRequest {
    private Long reportId;
    private String action;
    private String handleResult;
    private String punishmentType;
    private Integer punishmentDays;
}

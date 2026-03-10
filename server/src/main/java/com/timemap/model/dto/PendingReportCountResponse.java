package com.timemap.model.dto;

import lombok.Data;

@Data
public class PendingReportCountResponse {
    private Long reportCount;
    private Long appealCount;
}

package com.timemap.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminReportPageResponse {
    private List<AdminReportListItemResponse> list;
    private Long total;
    private Boolean hasMore;
}

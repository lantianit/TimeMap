package com.timemap.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class MyReportPageResponse {
    private List<MyReportItemResponse> list;
    private Long total;
    private Boolean hasMore;
}

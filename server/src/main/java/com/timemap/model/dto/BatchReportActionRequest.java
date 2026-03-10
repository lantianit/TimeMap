package com.timemap.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchReportActionRequest {
    private List<Long> reportIds;
    private String action;
    private String handleResult;
}

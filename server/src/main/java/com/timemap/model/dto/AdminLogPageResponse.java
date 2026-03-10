package com.timemap.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminLogPageResponse {
    private List<AdminLogResponse> list;
    private Long total;
    private Boolean hasMore;
}

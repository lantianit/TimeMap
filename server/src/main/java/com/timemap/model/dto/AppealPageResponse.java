package com.timemap.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class AppealPageResponse {
    private List<AppealResponse> list;
    private Long total;
    private Boolean hasMore;
}

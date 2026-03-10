package com.timemap.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserViolationPageResponse {
    private List<UserViolationResponse> list;
    private Long total;
    private Boolean hasMore;
}

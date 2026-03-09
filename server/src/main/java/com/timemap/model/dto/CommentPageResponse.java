package com.timemap.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class CommentPageResponse {
    private List<CommentResponse> list;
    private Long total;
    private Boolean hasMore;
}

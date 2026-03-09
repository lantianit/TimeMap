package com.timemap.model.dto;

import lombok.Data;

@Data
public class AddCommentRequest {
    private Long photoId;
    private String content;
    private Long parentId;
    private Long replyToUserId;
}

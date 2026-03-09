package com.timemap.model.dto;

import lombok.Data;

@Data
public class SendMessageRequest {
    private Long toUserId;
    private String content;
    private String msgType; // text / image
}

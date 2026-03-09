package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class MessageResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromUserId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toUserId;
    private String content;
    private String msgType;
    private Integer readStatus;
    private String createTime;
    // 发送者信息
    private String fromNickname;
    private String fromAvatarUrl;
}

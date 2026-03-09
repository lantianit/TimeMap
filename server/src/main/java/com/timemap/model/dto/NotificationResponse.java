package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class NotificationResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String type;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromUserId;
    private String fromNickname;
    private String fromAvatarUrl;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long photoId;
    private String photoThumbnail;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long commentId;
    private String content;
    private Integer isRead;
    private String createTime;
}

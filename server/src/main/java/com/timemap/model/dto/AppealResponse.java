package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class AppealResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String userNickname;
    private String userAvatarUrl;
    private String type;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reportId;
    private String reason;
    private Integer status;
    private String handleResult;
    private String createTime;
    private String handledTime;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long handledBy;
    private String handledByNickname;
    private String reportReason;
    private String reportTargetType;
}

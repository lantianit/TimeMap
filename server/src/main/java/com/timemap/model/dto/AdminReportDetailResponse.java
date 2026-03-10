package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class AdminReportDetailResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String targetType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    private String reason;
    private String description;
    private Integer status;
    private String handleResult;
    private String createTime;
    private String handledTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long reporterUserId;
    private String reporterNickname;
    private String reporterAvatarUrl;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetOwnerUserId;
    private String targetOwnerNickname;
    private String targetOwnerAvatarUrl;

    private String targetPreview;
    private String targetImageUrl;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long photoId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long commentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long handledBy;
    private String handledByNickname;
}

package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class UserViolationResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String userNickname;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reportId;
    private String violationType;
    private String reason;
    private String targetType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    private String punishmentType;
    private Integer punishmentDays;
    private String createTime;
    private Boolean canAppeal;
}

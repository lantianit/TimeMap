package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.util.List;

@Data
public class AggregatedReportResponse {
    private String targetType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    private String targetPreview;
    private String targetImageUrl;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetOwnerUserId;
    private String targetOwnerNickname;
    private Integer reportCount;
    private List<String> reasons;
    private String earliestTime;
    private String latestTime;
    private List<Long> reportIds;
}

package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ReportSubmitResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reportId;
    private Integer status;
}

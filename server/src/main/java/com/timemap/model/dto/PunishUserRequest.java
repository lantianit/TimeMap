package com.timemap.model.dto;

import lombok.Data;

@Data
public class PunishUserRequest {
    private Long userId;
    private Long reportId;
    private String punishmentType;
    private Integer punishmentDays;
    private String reason;
}

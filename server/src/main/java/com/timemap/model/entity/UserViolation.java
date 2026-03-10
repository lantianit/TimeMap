package com.timemap.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user_violation")
public class UserViolation {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long reportId;
    private String violationType;
    private String reason;
    private String targetType;
    private Long targetId;
    private String punishmentType;
    private Integer punishmentDays;
    private Long handledBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

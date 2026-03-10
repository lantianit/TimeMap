package com.timemap.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_report")
public class Report {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String targetType;
    private Long targetId;
    private String reason;
    private String description;
    private Integer status;
    private String handleResult;
    private Long handledBy;
    private LocalDateTime handledTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

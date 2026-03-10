package com.timemap.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_admin_log")
public class AdminLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long adminUserId;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

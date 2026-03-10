package com.timemap.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_admin_login_log")
public class AdminLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long adminAccountId;
    private String action;
    private String ip;
    private String userAgent;
    private String detail;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

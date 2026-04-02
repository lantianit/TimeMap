package com.maptrace.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_follow")
public class Follow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关注者ID */
    private Long userId;

    /** 被关注者ID */
    private Long targetUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

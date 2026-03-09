package com.timemap.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_photo_like")
public class PhotoLike {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long photoId;
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

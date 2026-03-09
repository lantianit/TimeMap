package com.timemap.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_comment")
public class Comment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long photoId;
    private Long userId;

    /** 父评论ID，顶级评论为0 */
    private Long parentId;

    /** 被回复者ID，顶级评论为0 */
    private Long replyToUserId;

    private String content;

    /** 点赞数 */
    private Integer likeCount;

    /** 回复数（仅顶级评论维护） */
    private Integer replyCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}

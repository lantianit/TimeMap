package com.timemap.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_message")
public class Message {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 发送者ID */
    private Long fromUserId;

    /** 接收者ID */
    private Long toUserId;

    /** 消息内容 */
    private String content;

    /** 消息类型：text=文本, image=图片 */
    private String msgType;

    /** 是否已读：0未读 1已读 */
    private Integer readStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}

package com.timemap.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String openid;

    private String nickname;

    private String avatarUrl;

    private Integer gender;

    private String country;

    private String province;

    private String city;

    private Integer profileCompleted;

    private LocalDateTime muteUntil;
    private LocalDateTime banUploadUntil;
    private Integer isBanned;
    private Integer violationCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}

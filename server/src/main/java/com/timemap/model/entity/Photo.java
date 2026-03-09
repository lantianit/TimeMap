package com.timemap.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_photo")
public class Photo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 图片 COS 存储 URL */
    private String imageUrl;

    /** 缩略图 URL */
    private String thumbnailUrl;

    /** 图片描述 */
    private String description;

    /** 经度 */
    private Double longitude;

    /** 纬度 */
    private Double latitude;

    /** 地点名称 */
    private String locationName;

    /** 行政区划（区/县） */
    private String district;

    /** 拍摄日期 */
    private LocalDate photoDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}

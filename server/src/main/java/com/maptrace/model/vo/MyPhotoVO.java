package com.maptrace.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class MyPhotoVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String imageUrl;
    private String thumbnailUrl;
    private String locationName;
    private String photoDate;
    private String createTime;
    private Integer commentCount;
    private Integer likeCount;
    private Integer visibility;
}

package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class MyPhotoResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String imageUrl;
    private String thumbnailUrl;
    private String locationName;
    private String photoDate;
    private String createTime;
    private Integer commentCount;
    private Integer likeCount;
}

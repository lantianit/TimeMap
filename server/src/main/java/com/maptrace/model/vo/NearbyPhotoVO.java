package com.maptrace.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class NearbyPhotoVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String imageUrl;
    private String thumbnailUrl;
    private Double longitude;
    private Double latitude;
    private String locationName;
    private String photoDate;
    private Double distance;
    private Integer commentCount;
    private Integer likeCount;
    private Integer visibility;
}

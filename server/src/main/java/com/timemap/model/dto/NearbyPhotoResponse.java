package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class NearbyPhotoResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String imageUrl;
    private String thumbnailUrl;
    private Double longitude;
    private Double latitude;
    private String locationName;
    private String photoDate;
    private Double distance;
    private Integer commentCount;
    private Integer likeCount;
}

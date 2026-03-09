package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class PhotoDetailResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String imageUrl;
    private String thumbnailUrl;
    private String description;
    private Double longitude;
    private Double latitude;
    private String locationName;
    private String photoDate;
    private String createTime;
    private String nickname;
    private String avatarUrl;
    private Integer likeCount;
    private Boolean liked;
}

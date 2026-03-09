package com.timemap.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class CommunityPhotoResponse {
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
    private String createTime;
    private String nickname;
    private String avatarUrl;
    private Integer commentCount;
    private Integer likeCount;
}

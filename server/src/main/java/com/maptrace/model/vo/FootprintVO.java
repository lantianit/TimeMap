package com.maptrace.model.vo;

import lombok.Data;
import java.util.List;

@Data
public class FootprintVO {
    private List<FootprintPhotoVO> photos;
    private FootprintSummary summary;

    @Data
    public static class FootprintPhotoVO {
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        private Long id;
        private String imageUrl;
        private String thumbnailUrl;
        private Double longitude;
        private Double latitude;
        private String locationName;
        private String photoDate;
        private String district;
        private String city;
    }

    @Data
    public static class FootprintSummary {
        private Integer totalPhotos;
        private Integer totalDistricts;
        private Integer totalCities;
        private List<CityGroup> cityGroups;
    }

    @Data
    public static class CityGroup {
        private String city;
        private Integer count;
        private Double latitude;
        private Double longitude;
        private List<DistrictGroup> districts;
    }

    @Data
    public static class DistrictGroup {
        private String district;
        private Integer count;
        private Double latitude;
        private Double longitude;
    }
}

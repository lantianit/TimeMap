package com.timemap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.timemap.model.dto.NearbyPhotoResponse;
import com.timemap.model.dto.PhotoDetailResponse;
import com.timemap.model.entity.Photo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface PhotoService extends IService<Photo> {

    PhotoDetailResponse upload(MultipartFile file, Long userId,
                               Double longitude, Double latitude,
                               String locationName, String photoDate,
                               String description, String district);

    List<NearbyPhotoResponse> findNearby(double lat, double lng, double radiusKm,
                                         String startDate, String endDate);

    PhotoDetailResponse getDetail(Long id);

    List<PhotoDetailResponse> getBatchDetail(String ids);


    com.timemap.model.dto.CommunityPageResponse findCommunity(String district, int page, int size, String sortBy);

    Map<String, Long> getAreaStats(String district);
}

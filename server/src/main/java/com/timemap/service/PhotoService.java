package com.timemap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.timemap.model.dto.NearbyPhotoResponse;
import com.timemap.model.dto.PhotoDetailResponse;
import com.timemap.model.entity.Photo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PhotoService extends IService<Photo> {

    PhotoDetailResponse upload(MultipartFile file, Long userId,
                               Double longitude, Double latitude,
                               String locationName, String photoDate,
                               String description);

    List<NearbyPhotoResponse> findNearby(double lat, double lng, double radiusKm,
                                         String startDate, String endDate);

    PhotoDetailResponse getDetail(Long id);

    List<PhotoDetailResponse> getBatchDetail(String ids);
}

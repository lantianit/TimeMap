package com.timemap.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.timemap.mapper.PhotoMapper;
import com.timemap.mapper.UserMapper;
import com.timemap.model.dto.NearbyPhotoResponse;
import com.timemap.model.dto.PhotoDetailResponse;
import com.timemap.model.entity.Photo;
import com.timemap.model.entity.User;
import com.timemap.service.CosService;
import com.timemap.service.PhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoServiceImpl extends ServiceImpl<PhotoMapper, Photo> implements PhotoService {

    private final PhotoMapper photoMapper;
    private final UserMapper userMapper;
    private final CosService cosService;

    @Override
    public PhotoDetailResponse upload(MultipartFile file, Long userId,
                                      Double longitude, Double latitude,
                                      String locationName, String photoDate,
                                      String description) {
        try {
            String imageUrl = cosService.upload(file);

            Photo photo = new Photo();
            photo.setUserId(userId);
            photo.setImageUrl(imageUrl);
            photo.setThumbnailUrl(imageUrl);
            photo.setLongitude(longitude);
            photo.setLatitude(latitude);
            photo.setLocationName(locationName != null ? locationName : "");
            photo.setPhotoDate(LocalDate.parse(photoDate, DateTimeFormatter.ISO_LOCAL_DATE));
            photo.setDescription(description != null ? description : "");

            this.save(photo);
            log.info("照片上传成功, userId={}, photoId={}", userId, photo.getId());
            return getDetail(photo.getId());
        } catch (Exception e) {
            log.error("照片上传失败", e);
            throw new RuntimeException("照片上传失败: " + e.getMessage());
        }
    }

    @Override
    public List<NearbyPhotoResponse> findNearby(double lat, double lng, double radiusKm,
                                                 String startDate, String endDate) {
        return photoMapper.findNearby(lat, lng, radiusKm, startDate, endDate);
    }

    @Override
    public PhotoDetailResponse getDetail(Long id) {
        Photo photo = this.getById(id);
        if (photo == null) return null;

        PhotoDetailResponse resp = new PhotoDetailResponse();
        resp.setId(photo.getId());
        resp.setUserId(photo.getUserId());
        resp.setImageUrl(photo.getImageUrl());
        resp.setThumbnailUrl(photo.getThumbnailUrl());
        resp.setDescription(photo.getDescription());
        resp.setLongitude(photo.getLongitude());
        resp.setLatitude(photo.getLatitude());
        resp.setLocationName(photo.getLocationName());
        resp.setPhotoDate(photo.getPhotoDate().toString());
        resp.setCreateTime(photo.getCreateTime() != null ? photo.getCreateTime().toString() : "");

        User user = userMapper.selectById(photo.getUserId());
        if (user != null) {
            resp.setNickname(user.getNickname());
            resp.setAvatarUrl(user.getAvatarUrl());
        }
        return resp;
    }
}

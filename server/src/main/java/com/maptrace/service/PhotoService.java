package com.maptrace.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.maptrace.model.vo.NearbyPhotoVO;
import com.maptrace.model.vo.PhotoDetailVO;
import com.maptrace.model.entity.Photo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface PhotoService extends IService<Photo> {

    PhotoDetailVO upload(MultipartFile file, Long userId,
                         Double longitude, Double latitude,
                         String locationName, String photoDate,
                         String description, String district,
                         Integer visibility);

    List<NearbyPhotoVO> findNearby(double lat, double lng, double radiusKm,
                                   String startDate, String endDate,
                                   Long viewerUserId);

    PhotoDetailVO getDetail(Long id);

    PhotoDetailVO getDetail(Long id, Long userId);

    Map<String, Object> toggleLike(Long photoId, Long userId);

    List<PhotoDetailVO> getBatchDetail(String ids, Long userId);

    com.maptrace.model.vo.CommunityPageVO findCommunity(String district, int page, int size, String sortBy, Long viewerUserId);

    Map<String, Long> getAreaStats(String district, String startDate, String endDate);

    Map<String, Object> getMyPhotos(Long userId, int page, int size);

    /** 查看他人主页照片（带可见性过滤） */
    Map<String, Object> getUserPhotos(Long targetUserId, Long viewerUserId, int page, int size);

    void deletePhoto(Long photoId, Long userId);

    void updatePhotoDate(Long photoId, Long userId, String photoDate);

    void updateVisibility(Long photoId, Long userId, Integer visibility);

    com.maptrace.model.vo.UserProfileVO getUserProfile(Long userId, Long viewerUserId);

    Map<String, Object> getDistrictRanking(String sortBy, int limit);

    com.maptrace.model.vo.FootprintVO getFootprint(Long targetUserId, Long viewerUserId);

    /** 检查照片是否存在（不做可见性过滤） */
    boolean existsById(Long id);
}

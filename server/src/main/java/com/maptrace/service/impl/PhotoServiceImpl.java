package com.maptrace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maptrace.common.BusinessException;
import com.maptrace.common.ErrorCode;
import com.maptrace.mapper.PhotoLikeMapper;
import com.maptrace.mapper.PhotoMapper;
import com.maptrace.mapper.UserMapper;
import com.maptrace.model.vo.NearbyPhotoVO;
import com.maptrace.model.vo.PhotoDetailVO;
import com.maptrace.model.vo.UserProfileVO;
import com.maptrace.model.entity.Photo;
import com.maptrace.model.entity.PhotoLike;
import com.maptrace.model.entity.User;
import com.maptrace.monitor.BusinessMetricsCollector;
import com.maptrace.service.CosService;
import com.maptrace.service.NotificationService;
import com.maptrace.service.PhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoServiceImpl extends ServiceImpl<PhotoMapper, Photo> implements PhotoService {

    private final PhotoMapper photoMapper;
    private final UserMapper userMapper;
    private final CosService cosService;
    private final PhotoLikeMapper photoLikeMapper;
    private final NotificationService notificationService;
    private final BusinessMetricsCollector metricsCollector;

    @Override
    public PhotoDetailVO upload(MultipartFile file, Long userId,
                                      Double longitude, Double latitude,
                                      String locationName, String photoDate,
                                      String description, String district) {
        java.time.Instant start = java.time.Instant.now();
        try {
            String imageUrl = cosService.upload(file);

            Photo photo = new Photo();
            photo.setUserId(userId);
            photo.setImageUrl(imageUrl);
            photo.setThumbnailUrl(imageUrl);
            photo.setLongitude(longitude);
            photo.setLatitude(latitude);
            photo.setLocationName(locationName != null ? locationName : "");
            photo.setDistrict(district != null ? district : "");
            photo.setPhotoDate(LocalDate.parse(photoDate, DateTimeFormatter.ISO_LOCAL_DATE));
            photo.setDescription(description != null ? description : "");

            this.save(photo);
            log.info("照片上传成功, userId={}, photoId={}", userId, photo.getId());

            // 监控埋点
            metricsCollector.recordPhotoUpload(String.valueOf(userId), district != null ? district : "unknown");
            metricsCollector.recordPhotoUploadDuration(String.valueOf(userId),
                    java.time.Duration.between(start, java.time.Instant.now()));

            return getDetail(photo.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("照片上传失败", e);
            throw new BusinessException(ErrorCode.PHOTO_UPLOAD_FAILED, "照片上传失败，请稍后重试");
        }
    }

    @Override
    public List<NearbyPhotoVO> findNearby(double lat, double lng, double radiusKm,
                                                 String startDate, String endDate) {
        return photoMapper.findNearby(lat, lng, radiusKm, startDate, endDate);
    }

    @Override
    public PhotoDetailVO getDetail(Long id) {
        return getDetail(id, null);
    }

    @Override
    public PhotoDetailVO getDetail(Long id, Long userId) {
        log.info("getDetail 调用: photoId={}, userId={}", id, userId);
        Photo photo = this.getById(id);
        if (photo == null) return null;

        PhotoDetailVO resp = new PhotoDetailVO();
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

        // 点赞数
        long likeCount = photoLikeMapper.selectCount(
                new LambdaQueryWrapper<PhotoLike>().eq(PhotoLike::getPhotoId, id));
        resp.setLikeCount(Math.toIntExact(likeCount));
        log.info("照片 {} 的点赞数: {}", id, likeCount);

        // 当前用户是否已点赞
        if (userId != null) {
            long liked = photoLikeMapper.selectCount(
                    new LambdaQueryWrapper<PhotoLike>()
                            .eq(PhotoLike::getPhotoId, id)
                            .eq(PhotoLike::getUserId, userId));
            resp.setLiked(liked > 0);
            log.info("用户 {} 对照片 {} 的点赞状态: {}", userId, id, liked > 0);
        } else {
            resp.setLiked(false);
            log.info("未登录用户查看照片 {}, liked 设置为 false", id);
        }

        log.info("getDetail 返回: photoId={}, liked={}, likeCount={}", id, resp.getLiked(), resp.getLikeCount());
        return resp;
    }

    @Override
    public Map<String, Object> toggleLike(Long photoId, Long userId) {
        LambdaQueryWrapper<PhotoLike> wrapper = new LambdaQueryWrapper<PhotoLike>()
                .eq(PhotoLike::getPhotoId, photoId)
                .eq(PhotoLike::getUserId, userId);
        PhotoLike existing = photoLikeMapper.selectOne(wrapper);

        boolean liked;
        if (existing != null) {
            photoLikeMapper.deleteById(existing.getId());
            liked = false;
            metricsCollector.recordLike(String.valueOf(userId), "photo", "unlike");
        } else {
            PhotoLike like = new PhotoLike();
            like.setPhotoId(photoId);
            like.setUserId(userId);
            photoLikeMapper.insert(like);
            liked = true;
            metricsCollector.recordLike(String.valueOf(userId), "photo", "like");
            // 通知照片作者
            Photo photo = this.getById(photoId);
            if (photo != null) {
                notificationService.createNotification(
                        photo.getUserId(), userId, "photo_like",
                        photoId, null, null);
            }
        }

        long likeCount = photoLikeMapper.selectCount(
                new LambdaQueryWrapper<PhotoLike>().eq(PhotoLike::getPhotoId, photoId));

        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", likeCount);
        return result;
    }

    @Override
    public List<PhotoDetailVO> getBatchDetail(String ids, Long userId) {
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Long.parseLong(s); }
                    catch (NumberFormatException e) { return null; }
                })
                .filter(Objects::nonNull)
                .map(id -> this.getDetail(id, userId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public com.maptrace.model.vo.CommunityPageVO findCommunity(String district, int page, int size, String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) sortBy = "photoDate";
        if (district == null) district = "";
        int offset = (page - 1) * size;
        var list = photoMapper.findCommunity(offset, size, sortBy, district);
        long total = photoMapper.countCommunity(district);
        var resp = new com.maptrace.model.vo.CommunityPageVO();
        resp.setList(list);
        resp.setTotal(total);
        resp.setHasMore(offset + size < total);
        return resp;
    }

    @Override
    public Map<String, Long> getAreaStats(String district, String startDate, String endDate) {
        boolean hasFilter = (startDate != null && !startDate.isEmpty()) || (endDate != null && !endDate.isEmpty());

        if (hasFilter) {
            // 筛选模式：按日期范围统计
            long total = photoMapper.countByDistrictAndDate(district, startDate, endDate);
            long users = photoMapper.countUsersByDistrictAndDate(district, startDate, endDate);
            Map<String, Long> stats = new HashMap<>();
            stats.put("total", total);
            stats.put("users", users);
            stats.put("today", 0L);
            stats.put("todayUsers", 0L);
            return stats;
        } else {
            // 无筛选：全量 + 今日
            long total = photoMapper.countByDistrict(district);
            long today = photoMapper.countTodayByDistrict(district);
            long todayUsers = photoMapper.countTodayUsersByDistrict(district);
            Map<String, Long> stats = new HashMap<>();
            stats.put("total", total);
            stats.put("today", today);
            stats.put("todayUsers", todayUsers);
            stats.put("users", 0L);
            return stats;
        }
    }

    @Override
    public Map<String, Object> getMyPhotos(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        var list = photoMapper.findMyPhotos(userId, offset, size);
        long total = photoMapper.countMyPhotos(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("hasMore", offset + size < total);
        return result;
    }

    @Override
    @Transactional
    public void deletePhoto(Long photoId, Long userId) {
        Photo photo = this.getById(photoId);
        if (photo == null) throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
        if (!photo.getUserId().equals(userId)) throw new BusinessException(ErrorCode.PHOTO_DELETE_FORBIDDEN);
        // 登记 COS 文件到 30 天后删除队列
        cosService.scheduleDelete(photo.getImageUrl(), "photo", photo.getId(), "user_delete");
        if (photo.getThumbnailUrl() != null && !photo.getThumbnailUrl().equals(photo.getImageUrl())) {
            cosService.scheduleDelete(photo.getThumbnailUrl(), "photo", photo.getId(), "user_delete");
        }
        this.removeById(photoId);
        metricsCollector.recordPhotoDelete(String.valueOf(userId), "user");
    }

    @Override
    public void updatePhotoDate(Long photoId, Long userId, String photoDate) {
        Photo photo = this.getById(photoId);
        if (photo == null) throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
        if (!photo.getUserId().equals(userId)) throw new BusinessException(ErrorCode.PHOTO_DELETE_FORBIDDEN);
        photo.setPhotoDate(LocalDate.parse(photoDate, DateTimeFormatter.ISO_LOCAL_DATE));
        this.updateById(photo);
    }

    @Override
    public UserProfileVO getUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return null;
        UserProfileVO resp = new UserProfileVO();
        resp.setUserId(user.getId());
        resp.setNickname(user.getNickname());
        resp.setAvatarUrl(user.getAvatarUrl());
        resp.setPhotoCount(Math.toIntExact(photoMapper.countMyPhotos(userId)));
        resp.setAreaCount(Math.toIntExact(photoMapper.countUserAreas(userId)));
        resp.setLikeCount(Math.toIntExact(photoMapper.countUserTotalLikes(userId)));
        resp.setLatestPhotoDate(photoMapper.findLatestPhotoDate(userId));
        resp.setTopAreas(photoMapper.findTopAreas(userId, 3));
        resp.setCreateTime(user.getCreateTime() != null ? user.getCreateTime().toString() : "");
        return resp;
    }

    @Override
    public Map<String, Object> getDistrictRanking(String sortBy, int limit) {
        if (sortBy == null || sortBy.isEmpty()) sortBy = "photoCount";
        var list = photoMapper.findDistrictRanking(sortBy, limit);
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setRank(i + 1);
        }
        long districtCount = photoMapper.countDistinctDistricts();
        long totalPhotos = photoMapper.countAllPhotosWithDistrict();
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("districtCount", districtCount);
        result.put("totalPhotos", totalPhotos);
        return result;
    }

}

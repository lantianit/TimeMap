package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.timemap.mapper.PhotoLikeMapper;
import com.timemap.mapper.PhotoMapper;
import com.timemap.mapper.UserMapper;
import com.timemap.model.dto.NearbyPhotoResponse;
import com.timemap.model.dto.PhotoDetailResponse;
import com.timemap.model.entity.Photo;
import com.timemap.model.entity.PhotoLike;
import com.timemap.model.entity.User;
import com.timemap.service.CosService;
import com.timemap.service.PhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    @Override
    public PhotoDetailResponse upload(MultipartFile file, Long userId,
                                      Double longitude, Double latitude,
                                      String locationName, String photoDate,
                                      String description, String district) {
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
        return getDetail(id, null);
    }

    @Override
    public PhotoDetailResponse getDetail(Long id, Long userId) {
        log.info("getDetail 调用: photoId={}, userId={}", id, userId);
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
        } else {
            PhotoLike like = new PhotoLike();
            like.setPhotoId(photoId);
            like.setUserId(userId);
            photoLikeMapper.insert(like);
            liked = true;
        }

        long likeCount = photoLikeMapper.selectCount(
                new LambdaQueryWrapper<PhotoLike>().eq(PhotoLike::getPhotoId, photoId));

        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", likeCount);
        return result;
    }

    @Override
    public List<PhotoDetailResponse> getBatchDetail(String ids, Long userId) {
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
    public com.timemap.model.dto.CommunityPageResponse findCommunity(String district, int page, int size, String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) sortBy = "photoDate";
        if (district == null) district = "";
        int offset = (page - 1) * size;
        var list = photoMapper.findCommunity(offset, size, sortBy, district);
        long total = photoMapper.countCommunity(district);
        var resp = new com.timemap.model.dto.CommunityPageResponse();
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

}

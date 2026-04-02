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
import com.maptrace.service.FollowService;
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
    private final FollowService followService;

    @Override
    public PhotoDetailVO upload(MultipartFile file, Long userId,
                                Double longitude, Double latitude,
                                String locationName, String photoDate,
                                String description, String district,
                                Integer visibility) {
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
            photo.setVisibility(visibility != null ? visibility : 2);

            this.save(photo);
            log.info("照片上传成功, userId={}, photoId={}, visibility={}", userId, photo.getId(), photo.getVisibility());

            metricsCollector.recordPhotoUpload(String.valueOf(userId), district != null ? district : "unknown");
            metricsCollector.recordPhotoUploadDuration(String.valueOf(userId),
                    java.time.Duration.between(start, java.time.Instant.now()));

            return getDetail(photo.getId(), userId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("照片上传失败", e);
            throw new BusinessException(ErrorCode.PHOTO_UPLOAD_FAILED, "照片上传失败，请稍后重试");
        }
    }

    @Override
    public List<NearbyPhotoVO> findNearby(double lat, double lng, double radiusKm,
                                          String startDate, String endDate,
                                          Long viewerUserId) {
        List<Long> mutualIds = followService.getMutualUserIds(viewerUserId);
        return photoMapper.findNearby(lat, lng, radiusKm, startDate, endDate, viewerUserId, mutualIds);
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

        // 可见性检查
        int vis = photo.getVisibility() != null ? photo.getVisibility() : 2;
        if (vis == 0) {
            // 仅自己可见
            if (userId == null || !userId.equals(photo.getUserId())) return null;
        } else if (vis == 1) {
            // 互关可见：本人或互关用户
            if (userId == null) return null;
            if (!userId.equals(photo.getUserId()) && !followService.isMutual(userId, photo.getUserId())) {
                return null;
            }
        }
        // vis == 2: 所有人可见

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
        resp.setVisibility(vis);

        User user = userMapper.selectById(photo.getUserId());
        if (user != null) {
            resp.setNickname(user.getNickname());
            resp.setAvatarUrl(user.getAvatarUrl());
        }

        // 点赞数
        long likeCount = photoLikeMapper.selectCount(
                new LambdaQueryWrapper<PhotoLike>().eq(PhotoLike::getPhotoId, id));
        resp.setLikeCount(Math.toIntExact(likeCount));

        // 当前用户是否已点赞
        if (userId != null) {
            long liked = photoLikeMapper.selectCount(
                    new LambdaQueryWrapper<PhotoLike>()
                            .eq(PhotoLike::getPhotoId, id)
                            .eq(PhotoLike::getUserId, userId));
            resp.setLiked(liked > 0);
        } else {
            resp.setLiked(false);
        }

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
    public com.maptrace.model.vo.CommunityPageVO findCommunity(String district, int page, int size, String sortBy, Long viewerUserId) {
        if (sortBy == null || sortBy.isEmpty()) sortBy = "photoDate";
        if (district == null) district = "";
        int offset = (page - 1) * size;
        List<Long> mutualIds = followService.getMutualUserIds(viewerUserId);
        var list = photoMapper.findCommunity(offset, size, sortBy, district, viewerUserId, mutualIds);
        long total = photoMapper.countCommunity(district, viewerUserId, mutualIds);
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
            long total = photoMapper.countByDistrictAndDate(district, startDate, endDate);
            long users = photoMapper.countUsersByDistrictAndDate(district, startDate, endDate);
            Map<String, Long> stats = new HashMap<>();
            stats.put("total", total);
            stats.put("users", users);
            stats.put("today", 0L);
            stats.put("todayUsers", 0L);
            return stats;
        } else {
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
    public Map<String, Object> getUserPhotos(Long targetUserId, Long viewerUserId, int page, int size) {
        int offset = (page - 1) * size;
        boolean isMutual = followService.isMutual(viewerUserId, targetUserId);
        var list = photoMapper.findUserPhotos(targetUserId, viewerUserId, isMutual, offset, size);
        long total = photoMapper.countUserPhotos(targetUserId, viewerUserId, isMutual);
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
    public void updateVisibility(Long photoId, Long userId, Integer visibility) {
        Photo photo = this.getById(photoId);
        if (photo == null) throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
        if (!photo.getUserId().equals(userId)) throw new BusinessException(ErrorCode.PHOTO_DELETE_FORBIDDEN);
        if (visibility < 0 || visibility > 2) throw new BusinessException(ErrorCode.PARAMS_ERROR, "可见性值无效");
        photo.setVisibility(visibility);
        this.updateById(photo);
        log.info("照片可见性更新: photoId={}, visibility={}", photoId, visibility);
    }

    @Override
    public UserProfileVO getUserProfile(Long userId, Long viewerUserId) {
        User user = userMapper.selectById(userId);
        if (user == null) return null;

        boolean isSelf = viewerUserId != null && viewerUserId.equals(userId);
        boolean isMutual = !isSelf && viewerUserId != null && followService.isMutual(viewerUserId, userId);

        UserProfileVO resp = new UserProfileVO();
        resp.setUserId(user.getId());
        resp.setNickname(user.getNickname());
        resp.setAvatarUrl(user.getAvatarUrl());
        resp.setPhotoCount(Math.toIntExact(photoMapper.countPhotosFiltered(userId, isSelf, isMutual)));
        resp.setAreaCount(Math.toIntExact(photoMapper.countUserAreasFiltered(userId, isSelf, isMutual)));
        resp.setLikeCount(Math.toIntExact(photoMapper.countUserTotalLikesFiltered(userId, isSelf, isMutual)));
        resp.setLatestPhotoDate(photoMapper.findLatestPhotoDate(userId));
        resp.setTopAreas(photoMapper.findTopAreasFiltered(userId, 3, isSelf, isMutual));
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

    @Override
    public com.maptrace.model.vo.FootprintVO getFootprint(Long targetUserId, Long viewerUserId) {
        boolean isSelf = targetUserId.equals(viewerUserId);
        List<Long> mutualUserIds = Collections.emptyList();
        if (!isSelf && viewerUserId != null) {
            mutualUserIds = followService.getMutualUserIds(viewerUserId);
        }

        var photos = photoMapper.findFootprintPhotos(targetUserId, isSelf, mutualUserIds);

        // 从 location_name 提取城市名（格式通常为 "xx省xx市xx区xxx" 或 "xx市xx区xxx"）
        for (var p : photos) {
            if (p.getCity() == null || p.getCity().isEmpty()) {
                p.setCity(extractCity(p.getLocationName(), p.getDistrict()));
            }
        }

        // 按 city → district 聚合
        var cityMap = new LinkedHashMap<String, List<com.maptrace.model.vo.FootprintVO.FootprintPhotoVO>>();
        for (var p : photos) {
            String city = p.getCity() != null && !p.getCity().isEmpty() ? p.getCity() : "其他";
            cityMap.computeIfAbsent(city, k -> new ArrayList<>()).add(p);
        }

        var cityGroups = new ArrayList<com.maptrace.model.vo.FootprintVO.CityGroup>();
        var allDistricts = new HashSet<String>();

        for (var entry : cityMap.entrySet()) {
            var cg = new com.maptrace.model.vo.FootprintVO.CityGroup();
            cg.setCity(entry.getKey());
            cg.setCount(entry.getValue().size());

            // 城市平均坐标
            double avgLat = entry.getValue().stream().mapToDouble(p -> p.getLatitude() != null ? p.getLatitude() : 0).average().orElse(0);
            double avgLng = entry.getValue().stream().mapToDouble(p -> p.getLongitude() != null ? p.getLongitude() : 0).average().orElse(0);
            cg.setLatitude(avgLat);
            cg.setLongitude(avgLng);

            // 按 district 聚合
            var distMap = new LinkedHashMap<String, List<com.maptrace.model.vo.FootprintVO.FootprintPhotoVO>>();
            for (var p : entry.getValue()) {
                String dist = p.getDistrict() != null && !p.getDistrict().isEmpty() ? p.getDistrict() : "未标注";
                distMap.computeIfAbsent(dist, k -> new ArrayList<>()).add(p);
            }

            var distGroups = new ArrayList<com.maptrace.model.vo.FootprintVO.DistrictGroup>();
            for (var de : distMap.entrySet()) {
                var dg = new com.maptrace.model.vo.FootprintVO.DistrictGroup();
                dg.setDistrict(de.getKey());
                dg.setCount(de.getValue().size());
                dg.setLatitude(de.getValue().stream().mapToDouble(p -> p.getLatitude() != null ? p.getLatitude() : 0).average().orElse(0));
                dg.setLongitude(de.getValue().stream().mapToDouble(p -> p.getLongitude() != null ? p.getLongitude() : 0).average().orElse(0));
                distGroups.add(dg);
                allDistricts.add(de.getKey());
            }
            distGroups.sort((a, b) -> b.getCount() - a.getCount());
            cg.setDistricts(distGroups);
            cityGroups.add(cg);
        }
        cityGroups.sort((a, b) -> b.getCount() - a.getCount());

        var summary = new com.maptrace.model.vo.FootprintVO.FootprintSummary();
        summary.setTotalPhotos(photos.size());
        summary.setTotalDistricts(allDistricts.size());
        summary.setTotalCities(cityGroups.size());
        summary.setCityGroups(cityGroups);

        var result = new com.maptrace.model.vo.FootprintVO();
        result.setPhotos(photos);
        result.setSummary(summary);
        return result;
    }

    @Override
    public boolean existsById(Long id) {
        return this.getById(id) != null;
    }

    /** 从 locationName 中提取城市名 */
    private String extractCity(String locationName, String district) {
        if (locationName == null || locationName.isEmpty()) {
            return district != null ? district : "其他";
        }
        // 尝试匹配 "xx市"
        int idx = locationName.indexOf("市");
        if (idx > 0) {
            // 往前找省份结尾
            String before = locationName.substring(0, idx + 1);
            int provinceEnd = before.lastIndexOf("省");
            if (provinceEnd < 0) provinceEnd = before.lastIndexOf("区"); // 自治区
            if (provinceEnd < 0) provinceEnd = -1;
            return before.substring(provinceEnd + 1);
        }
        // 直辖市/特别行政区等，直接用 district
        return district != null && !district.isEmpty() ? district : "其他";
    }
}

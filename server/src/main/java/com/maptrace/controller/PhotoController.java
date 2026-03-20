package com.maptrace.controller;

import com.maptrace.common.ErrorCode;
import com.maptrace.common.Result;
import com.maptrace.common.ThrowUtils;
import com.maptrace.model.vo.CommunityPageVO;
import com.maptrace.model.vo.NearbyPhotoVO;
import com.maptrace.model.vo.PhotoDetailVO;
import com.maptrace.model.vo.UserProfileVO;
import com.maptrace.service.PhotoService;
import com.maptrace.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/photo")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final UserService userService;

    @PostMapping("/upload")
    public Result<PhotoDetailVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("longitude") Double longitude,
            @RequestParam("latitude") Double latitude,
            @RequestParam("photoDate") String photoDate,
            @RequestParam(value = "locationName", required = false) String locationName,
            @RequestParam(value = "district", required = false) String district,
            @RequestParam(value = "description", required = false) String description,
            @RequestAttribute("userId") Long userId) {
        userService.checkBanUpload(userId);
        ThrowUtils.throwIf(file.isEmpty(), ErrorCode.PARAMS_ERROR, "请选择要上传的图片");
        PhotoDetailVO photo = photoService.upload(
                file, userId, longitude, latitude, locationName, photoDate, description, district);
        return Result.success(photo);
    }

    @GetMapping("/nearby")
    public Result<List<NearbyPhotoVO>> nearby(
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam(value = "radius", defaultValue = "10") double radius,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        List<NearbyPhotoVO> list = photoService.findNearby(
                latitude, longitude, radius, startDate, endDate);
        return Result.success(list);
    }

    @GetMapping("/detail/{id}")
    public Result<PhotoDetailVO> detail(
            @PathVariable Long id,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        log.info("[PhotoController] detail 请求: photoId={}, userId={}", id, userId);
        PhotoDetailVO photo = photoService.getDetail(id, userId);
        ThrowUtils.throwIf(photo == null, ErrorCode.PHOTO_NOT_FOUND);
        log.info("[PhotoController] detail 响应: photoId={}, liked={}, likeCount={}",
                id, photo.getLiked(), photo.getLikeCount());
        return Result.success(photo);
    }

    @PostMapping("/like")
    public Result<Map<String, Object>> likePhoto(
            @RequestParam("photoId") Long photoId,
            @RequestAttribute("userId") Long userId) {
        Map<String, Object> result = photoService.toggleLike(photoId, userId);
        return Result.success(result);
    }

    @GetMapping("/batch")
    public Result<List<PhotoDetailVO>> batch(
            @RequestParam("ids") String ids,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        log.info("[PhotoController] batch 请求: ids={}, userId={}", ids, userId);
        List<PhotoDetailVO> list = photoService.getBatchDetail(ids, userId);
        log.info("[PhotoController] batch 响应: 返回 {} 张照片", list.size());
        return Result.success(list);
    }

    @GetMapping("/community")
    public Result<CommunityPageVO> community(
            @RequestParam("district") String district,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "photoDate") String sortBy) {
        CommunityPageVO data = photoService.findCommunity(district, page, size, sortBy);
        return Result.success(data);
    }

    @GetMapping("/stats")
    public Result<Map<String, Long>> stats(
            @RequestParam("district") String district,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        Map<String, Long> stats = photoService.getAreaStats(district, startDate, endDate);
        return Result.success(stats);
    }

    @GetMapping("/my")
    public Result<Map<String, Object>> myPhotos(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Map<String, Object> photos = photoService.getMyPhotos(userId, page, size);
        UserProfileVO profile = photoService.getUserProfile(userId);
        Map<String, Object> result = new HashMap<>(photos);
        result.put("user", profile);
        return Result.success(result);
    }

    @PostMapping("/updateDate")
    public Result<Void> updatePhotoDate(
            @RequestParam("photoId") Long photoId,
            @RequestParam("photoDate") String photoDate,
            @RequestAttribute("userId") Long userId) {
        photoService.updatePhotoDate(photoId, userId, photoDate);
        return Result.success();
    }

    @PostMapping("/delete")
    public Result<Void> deletePhoto(
            @RequestParam("photoId") Long photoId,
            @RequestAttribute("userId") Long userId) {
        photoService.deletePhoto(photoId, userId);
        return Result.success();
    }

    @GetMapping("/district-ranking")
    public Result<Map<String, Object>> districtRanking(
            @RequestParam(value = "sortBy", defaultValue = "photoCount") String sortBy,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        Map<String, Object> data = photoService.getDistrictRanking(sortBy, limit);
        return Result.success(data);
    }

    @GetMapping("/user/{userId}")
    public Result<Map<String, Object>> userPhotos(
            @PathVariable Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Map<String, Object> photos = photoService.getMyPhotos(userId, page, size);
        UserProfileVO profile = photoService.getUserProfile(userId);
        Map<String, Object> result = new HashMap<>(photos);
        result.put("user", profile);
        return Result.success(result);
    }
}

package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.NearbyPhotoResponse;
import com.timemap.model.dto.PhotoDetailResponse;
import com.timemap.service.PhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/photo")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @PostMapping("/upload")
    public Result<PhotoDetailResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("longitude") Double longitude,
            @RequestParam("latitude") Double latitude,
            @RequestParam("photoDate") String photoDate,
            @RequestParam(value = "locationName", required = false) String locationName,
            @RequestParam(value = "district", required = false) String district,
            @RequestParam(value = "description", required = false) String description,
            @RequestAttribute("userId") Long userId) {
        if (file.isEmpty()) {
            return Result.fail("请选择要上传的图片");
        }
        PhotoDetailResponse photo = photoService.upload(
                file, userId, longitude, latitude, locationName, photoDate, description, district);
        return Result.ok(photo);
    }

    @GetMapping("/nearby")
    public Result<List<NearbyPhotoResponse>> nearby(
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam(value = "radius", defaultValue = "10") double radius,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        List<NearbyPhotoResponse> list = photoService.findNearby(
                latitude, longitude, radius, startDate, endDate);
        return Result.ok(list);
    }

    @GetMapping("/detail/{id}")
    public Result<PhotoDetailResponse> detail(
            @PathVariable Long id,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        log.info("[PhotoController] detail 请求: photoId={}, userId={}", id, userId);
        PhotoDetailResponse photo = photoService.getDetail(id, userId);
        if (photo == null) {
            return Result.fail("照片不存在");
        }
        log.info("[PhotoController] detail 响应: photoId={}, liked={}, likeCount={}", 
                id, photo.getLiked(), photo.getLikeCount());
        return Result.ok(photo);
    }

    @PostMapping("/like")
    public Result<Map<String, Object>> likePhoto(
            @RequestParam("photoId") Long photoId,
            @RequestAttribute("userId") Long userId) {
        Map<String, Object> result = photoService.toggleLike(photoId, userId);
        return Result.ok(result);
    }

    @GetMapping("/batch")
    public Result<List<PhotoDetailResponse>> batch(
            @RequestParam("ids") String ids,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        log.info("[PhotoController] batch 请求: ids={}, userId={}", ids, userId);
        List<PhotoDetailResponse> list = photoService.getBatchDetail(ids, userId);
        log.info("[PhotoController] batch 响应: 返回 {} 张照片", list.size());
        return Result.ok(list);
    }

    @GetMapping("/community")
    public Result<com.timemap.model.dto.CommunityPageResponse> community(
            @RequestParam("district") String district,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "photoDate") String sortBy) {
        com.timemap.model.dto.CommunityPageResponse data = photoService.findCommunity(district, page, size, sortBy);
        return Result.ok(data);
    }

    @GetMapping("/stats")
    public Result<Map<String, Long>> stats(
            @RequestParam("district") String district) {
        Map<String, Long> stats = photoService.getAreaStats(district);
        return Result.ok(stats);
    }

}

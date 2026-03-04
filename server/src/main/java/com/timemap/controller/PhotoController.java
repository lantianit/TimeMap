package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.NearbyPhotoResponse;
import com.timemap.model.dto.PhotoDetailResponse;
import com.timemap.service.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
            @RequestParam(value = "description", required = false) String description,
            @RequestAttribute("userId") Long userId) {
        if (file.isEmpty()) {
            return Result.fail("请选择要上传的图片");
        }
        PhotoDetailResponse photo = photoService.upload(
                file, userId, longitude, latitude, locationName, photoDate, description);
        return Result.ok(photo);
    }

    @GetMapping("/nearby")
    public Result<List<NearbyPhotoResponse>> nearby(
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam(value = "radius", defaultValue = "10") double radius) {
        List<NearbyPhotoResponse> list = photoService.findNearby(latitude, longitude, radius);
        return Result.ok(list);
    }

    @GetMapping("/detail/{id}")
    public Result<PhotoDetailResponse> detail(@PathVariable Long id) {
        PhotoDetailResponse photo = photoService.getDetail(id);
        if (photo == null) {
            return Result.fail("照片不存在");
        }
        return Result.ok(photo);
    }
}

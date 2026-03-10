package com.timemap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.timemap.common.Result;
import com.timemap.mapper.*;
import com.timemap.model.entity.*;
import com.timemap.service.AdminLogService;
import com.timemap.service.CommentService;
import com.timemap.service.CosService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminContentController {

    private final PhotoMapper photoMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final ReportMapper reportMapper;
    private final AdminLogService adminLogService;
    private final CosService cosService;
    private final CommentService commentService;

    @GetMapping("/photo/list")
    public Result<Map<String, Object>> photoList(
            @RequestParam(value = "district", required = false) String district,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Photo> p = new Page<>(page, size);
        LambdaQueryWrapper<Photo> qw = new LambdaQueryWrapper<Photo>().orderByDesc(Photo::getCreateTime);
        if (district != null && !district.isBlank()) qw.eq(Photo::getDistrict, district);
        if (userId != null) qw.eq(Photo::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Photo::getLocationName, keyword).or().like(Photo::getDescription, keyword));
        }
        photoMapper.selectPage(p, qw);
        List<Map<String, Object>> list = p.getRecords().stream().map(photo -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", photo.getId());
            m.put("imageUrl", photo.getImageUrl());
            m.put("thumbnailUrl", photo.getThumbnailUrl());
            m.put("locationName", photo.getLocationName());
            m.put("district", photo.getDistrict());
            m.put("photoDate", photo.getPhotoDate());
            m.put("description", photo.getDescription());
            m.put("userId", photo.getUserId());
            User u = userMapper.selectById(photo.getUserId());
            m.put("nickname", u != null ? u.getNickname() : "");
            m.put("avatarUrl", u != null ? u.getAvatarUrl() : "");
            m.put("commentCount", commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getPhotoId, photo.getId())));
            m.put("reportCount", reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                    .eq(Report::getTargetType, "photo").eq(Report::getTargetId, photo.getId())));
            m.put("createTime", photo.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        return Result.ok(result);
    }

    @GetMapping("/photo/{id}")
    public Result<Map<String, Object>> photoDetail(@PathVariable("id") Long id) {
        Photo photo = photoMapper.selectById(id);
        if (photo == null) throw new RuntimeException("照片不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", photo.getId());
        m.put("imageUrl", photo.getImageUrl());
        m.put("thumbnailUrl", photo.getThumbnailUrl());
        m.put("locationName", photo.getLocationName());
        m.put("district", photo.getDistrict());
        m.put("photoDate", photo.getPhotoDate());
        m.put("description", photo.getDescription());
        m.put("longitude", photo.getLongitude());
        m.put("latitude", photo.getLatitude());
        m.put("userId", photo.getUserId());
        User u = userMapper.selectById(photo.getUserId());
        m.put("nickname", u != null ? u.getNickname() : "");
        m.put("avatarUrl", u != null ? u.getAvatarUrl() : "");
        m.put("createTime", photo.getCreateTime());
        return Result.ok(m);
    }

    @DeleteMapping("/photo/{id}")
    public Result<Void> deletePhoto(@PathVariable("id") Long id,
                                    @RequestAttribute("adminAccountId") Long adminId) {
        Photo photo = photoMapper.selectById(id);
        if (photo == null) throw new RuntimeException("照片不存在");
        photoMapper.deleteById(id);
        adminLogService.log(adminId, "delete_photo", "photo", id, "管理员删除照片");
        return Result.ok();
    }

    @GetMapping("/comment/list")
    public Result<Map<String, Object>> commentList(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "photoId", required = false) Long photoId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Comment> p = new Page<>(page, size);
        LambdaQueryWrapper<Comment> qw = new LambdaQueryWrapper<Comment>().orderByDesc(Comment::getCreateTime);
        if (userId != null) qw.eq(Comment::getUserId, userId);
        if (photoId != null) qw.eq(Comment::getPhotoId, photoId);
        if (keyword != null && !keyword.isBlank()) qw.like(Comment::getContent, keyword);
        commentMapper.selectPage(p, qw);
        List<Map<String, Object>> list = p.getRecords().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("photoId", c.getPhotoId());
            m.put("userId", c.getUserId());
            User u = userMapper.selectById(c.getUserId());
            m.put("nickname", u != null ? u.getNickname() : "");
            m.put("avatarUrl", u != null ? u.getAvatarUrl() : "");
            m.put("likeCount", c.getLikeCount());
            m.put("reportCount", reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                    .eq(Report::getTargetType, "comment").eq(Report::getTargetId, c.getId())));
            m.put("createTime", c.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        return Result.ok(result);
    }

    @DeleteMapping("/comment/{id}")
    public Result<Void> deleteComment(@PathVariable("id") Long id,
                                      @RequestAttribute("adminAccountId") Long adminId) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) throw new RuntimeException("评论不存在");
        commentService.deleteCommentByAdmin(id);
        adminLogService.log(adminId, "delete_comment", "comment", id, "管理员删除评论");
        return Result.ok();
    }
}

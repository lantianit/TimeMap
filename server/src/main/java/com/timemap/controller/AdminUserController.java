package com.timemap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.timemap.common.ErrorCode;
import com.timemap.common.Result;
import com.timemap.common.ThrowUtils;
import com.timemap.mapper.*;
import com.timemap.model.dto.*;
import com.timemap.model.vo.*;
import com.timemap.model.entity.*;
import com.timemap.service.AdminLogService;
import com.timemap.service.NotificationService;
import com.timemap.service.ReportService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;
    private final PhotoMapper photoMapper;
    private final CommentMapper commentMapper;
    private final UserViolationMapper userViolationMapper;
    private final ReportMapper reportMapper;
    private final AdminLogService adminLogService;
    private final NotificationService notificationService;
    private final ReportService reportService;

    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<User> p = new Page<>(page, size);
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime);
        if ("banned".equals(status)) {
            qw.eq(User::getIsBanned, 1);
        } else if ("muted".equals(status)) {
            qw.gt(User::getMuteUntil, LocalDateTime.now());
        } else if ("ban_upload".equals(status)) {
            qw.gt(User::getBanUploadUntil, LocalDateTime.now());
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(User::getNickname, keyword).or().eq(User::getId, keyword));
        }
        userMapper.selectPage(p, qw);
        List<Map<String, Object>> list = p.getRecords().stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("nickname", u.getNickname());
            m.put("avatarUrl", u.getAvatarUrl());
            m.put("photoCount", photoMapper.countMyPhotos(u.getId()));
            m.put("commentCount", commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getUserId, u.getId())));
            m.put("violationCount", u.getViolationCount() != null ? u.getViolationCount() : 0);
            m.put("isBanned", u.getIsBanned() != null && u.getIsBanned() == 1);
            m.put("muteUntil", u.getMuteUntil());
            m.put("banUploadUntil", u.getBanUploadUntil());
            m.put("createTime", u.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        User u = userMapper.selectById(id);
        ThrowUtils.throwIf(u == null, ErrorCode.USER_NOT_FOUND);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("nickname", u.getNickname());
        m.put("avatarUrl", u.getAvatarUrl());
        m.put("createTime", u.getCreateTime());
        m.put("photoCount", photoMapper.countMyPhotos(u.getId()));
        m.put("commentCount", commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getUserId, u.getId())));
        m.put("totalLikes", photoMapper.countUserTotalLikes(u.getId()));
        m.put("violationCount", u.getViolationCount() != null ? u.getViolationCount() : 0);
        m.put("isBanned", u.getIsBanned() != null && u.getIsBanned() == 1);
        m.put("muteUntil", u.getMuteUntil());
        m.put("banUploadUntil", u.getBanUploadUntil());
        return Result.success(m);
    }

    @GetMapping("/{id}/photos")
    public Result<Map<String, Object>> userPhotos(@PathVariable("id") Long id,
                                                   @RequestParam(value = "page", defaultValue = "1") int page,
                                                   @RequestParam(value = "size", defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<MyPhotoVO> photos = photoMapper.findMyPhotos(id, offset, size);
        long total = photoMapper.countMyPhotos(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", photos);
        result.put("total", total);
        return Result.success(result);
    }

    @GetMapping("/{id}/comments")
    public Result<Map<String, Object>> userComments(@PathVariable("id") Long id,
                                                     @RequestParam(value = "page", defaultValue = "1") int page,
                                                     @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<Comment> p = new Page<>(page, size);
        commentMapper.selectPage(p, new LambdaQueryWrapper<Comment>()
                .eq(Comment::getUserId, id).orderByDesc(Comment::getCreateTime));
        List<Map<String, Object>> list = p.getRecords().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("photoId", c.getPhotoId());
            m.put("likeCount", c.getLikeCount());
            m.put("createTime", c.getCreateTime());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", p.getTotal());
        return Result.success(result);
    }

    @PostMapping("/punish")
    public Result<Void> punish(@RequestAttribute("adminAccountId") Long adminId,
                               @RequestBody PunishUserRequest request) {
        reportService.punishUser(adminId, request);
        return Result.success();
    }

    @PostMapping("/unpunish")
    public Result<Void> unpunish(@RequestAttribute("adminAccountId") Long adminId,
                                 @RequestBody UnpunishRequest request) {
        ThrowUtils.throwIf(request.getUserId() == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
        User user = userMapper.selectById(request.getUserId());
        ThrowUtils.throwIf(user == null, ErrorCode.USER_NOT_FOUND);

        String type = request.getType();
        switch (type != null ? type : "") {
            case "mute" -> {
                user.setMuteUntil(null);
                userMapper.updateById(user);
                notificationService.createNotification(user.getId(), adminId, "punishment", null, null, "你的禁言处罚已被解除");
            }
            case "ban_upload" -> {
                user.setBanUploadUntil(null);
                userMapper.updateById(user);
                notificationService.createNotification(user.getId(), adminId, "punishment", null, null, "你的禁止拍摄处罚已被解除");
            }
            case "ban_account" -> {
                user.setIsBanned(0);
                userMapper.updateById(user);
                notificationService.createNotification(user.getId(), adminId, "punishment", null, null, "你的账号封禁已被解除");
            }
            default -> throw new com.timemap.common.BusinessException(ErrorCode.PARAMS_ERROR, "不支持的解除类型");
        }
        adminLogService.log(adminId, "unpunish_user", "user", user.getId(), "解除处罚: " + type);
        return Result.success();
    }

    @Data
    public static class UnpunishRequest {
        private Long userId;
        private String type;
    }
}

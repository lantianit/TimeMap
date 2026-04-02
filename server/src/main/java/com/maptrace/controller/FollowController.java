package com.maptrace.controller;

import com.maptrace.common.Result;
import com.maptrace.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/toggle")
    public Result<Map<String, Object>> toggle(
            @RequestParam("targetUserId") Long targetUserId,
            @RequestAttribute("userId") Long userId) {
        return Result.success(followService.toggle(userId, targetUserId));
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status(
            @RequestParam("targetUserId") Long targetUserId,
            @RequestAttribute("userId") Long userId) {
        return Result.success(followService.getStatus(userId, targetUserId));
    }

    @GetMapping("/following")
    public Result<Map<String, Object>> following(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(followService.getFollowing(userId, page, size));
    }

    @GetMapping("/followers")
    public Result<Map<String, Object>> followers(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(followService.getFollowers(userId, page, size));
    }

    @GetMapping("/count")
    public Result<Map<String, Object>> count(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        Long queryUserId = targetUserId != null ? targetUserId : userId;
        if (queryUserId == null) {
            return Result.success(Map.of("followingCount", 0L, "followerCount", 0L, "mutualCount", 0L));
        }
        return Result.success(followService.getCount(queryUserId));
    }
}

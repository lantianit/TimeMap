package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.NotificationResponse;
import com.timemap.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/list")
    public Result<List<NotificationResponse>> list(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.ok(notificationService.getNotifications(userId, page, size));
    }

    @GetMapping("/unread")
    public Result<Map<String, Integer>> unread(
            @RequestAttribute("userId") Long userId) {
        return Result.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @PostMapping("/readAll")
    public Result<Void> readAll(@RequestAttribute("userId") Long userId) {
        notificationService.markAllRead(userId);
        return Result.ok();
    }
}

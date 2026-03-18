package com.maptrace.controller;

import com.maptrace.common.Result;
import com.maptrace.model.vo.NotificationVO;
import com.maptrace.service.NotificationService;
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
    public Result<List<NotificationVO>> list(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(notificationService.getNotifications(userId, page, size));
    }

    @GetMapping("/unread")
    public Result<Map<String, Integer>> unread(
            @RequestAttribute("userId") Long userId) {
        return Result.success(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @PostMapping("/readAll")
    public Result<Void> readAll(@RequestAttribute("userId") Long userId) {
        notificationService.markAllRead(userId);
        return Result.success();
    }
}

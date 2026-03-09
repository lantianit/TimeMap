package com.timemap.service;

import com.timemap.model.dto.NotificationResponse;
import java.util.List;

public interface NotificationService {
    void createNotification(Long userId, Long fromUserId, String type, Long photoId, Long commentId, String content);
    List<NotificationResponse> getNotifications(Long userId, int page, int size);
    int getUnreadCount(Long userId);
    void markAllRead(Long userId);
}

package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.timemap.mapper.NotificationMapper;
import com.timemap.mapper.PhotoMapper;
import com.timemap.mapper.UserMapper;
import com.timemap.model.dto.NotificationResponse;
import com.timemap.model.entity.Notification;
import com.timemap.model.entity.Photo;
import com.timemap.model.entity.User;
import com.timemap.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final PhotoMapper photoMapper;

    @Override
    public void createNotification(Long userId, Long fromUserId, String type, Long photoId, Long commentId, String content) {
        // 不给自己发通知
        if (userId.equals(fromUserId)) return;

        Notification n = new Notification();
        n.setUserId(userId);
        n.setFromUserId(fromUserId);
        n.setType(type);
        n.setPhotoId(photoId);
        n.setCommentId(commentId);
        n.setContent(content != null && content.length() > 200 ? content.substring(0, 200) : content);
        n.setIsRead(0);
        notificationMapper.insert(n);
    }

    @Override
    public List<NotificationResponse> getNotifications(Long userId, int page, int size) {
        Page<Notification> p = new Page<>(page, size);
        LambdaQueryWrapper<Notification> qw = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime);
        notificationMapper.selectPage(p, qw);

        return p.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public int getUnreadCount(Long userId) {
        return Math.toIntExact(notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)));
    }

    @Override
    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.setId(n.getId());
        r.setType(n.getType());
        r.setFromUserId(n.getFromUserId());
        r.setPhotoId(n.getPhotoId());
        r.setCommentId(n.getCommentId());
        r.setContent(n.getContent());
        r.setIsRead(n.getIsRead());
        r.setCreateTime(n.getCreateTime() != null ? n.getCreateTime().toString() : "");

        User from = userMapper.selectById(n.getFromUserId());
        if (from != null) {
            r.setFromNickname(from.getNickname());
            r.setFromAvatarUrl(from.getAvatarUrl());
        }

        if (n.getPhotoId() != null) {
            Photo photo = photoMapper.selectById(n.getPhotoId());
            if (photo != null) {
                r.setPhotoThumbnail(photo.getThumbnailUrl());
            }
        }
        return r;
    }
}

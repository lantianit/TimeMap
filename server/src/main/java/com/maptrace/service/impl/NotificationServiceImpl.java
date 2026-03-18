package com.maptrace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maptrace.mapper.NotificationMapper;
import com.maptrace.mapper.PhotoMapper;
import com.maptrace.mapper.UserMapper;
import com.maptrace.model.vo.NotificationVO;
import com.maptrace.model.entity.Notification;
import com.maptrace.model.entity.Photo;
import com.maptrace.model.entity.User;
import com.maptrace.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final PhotoMapper photoMapper;

    /** 举报/管理类通知类型（fromUserId 为管理员，不在 t_user 表中） */
    private static final Set<String> SYSTEM_TYPES = Set.of(
            "report_result", "content_removed", "appeal_result", "warning", "punishment");

    @Override
    public void createNotification(Long userId, Long fromUserId, String type,
                                   Long photoId, Long commentId, String content) {
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
    public List<NotificationVO> getNotifications(Long userId, int page, int size) {
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

    private NotificationVO toResponse(Notification n) {
        NotificationVO r = new NotificationVO();
        r.setId(n.getId());
        r.setType(n.getType());
        r.setFromUserId(n.getFromUserId());
        r.setPhotoId(n.getPhotoId());
        r.setCommentId(n.getCommentId());
        r.setContent(n.getContent());
        r.setIsRead(n.getIsRead());
        r.setCreateTime(n.getCreateTime() != null ? n.getCreateTime().toString() : "");

        if (SYSTEM_TYPES.contains(n.getType())) {
            // 系统/管理员通知，不查用户表
            r.setFromNickname("系统通知");
            r.setFromAvatarUrl(null);
        } else {
            User from = userMapper.selectById(n.getFromUserId());
            if (from != null) {
                r.setFromNickname(from.getNickname());
                r.setFromAvatarUrl(from.getAvatarUrl());
            }
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

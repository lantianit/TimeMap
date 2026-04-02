package com.maptrace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maptrace.common.BusinessException;
import com.maptrace.common.ErrorCode;
import com.maptrace.mapper.FollowMapper;
import com.maptrace.mapper.UserMapper;
import com.maptrace.model.entity.Follow;
import com.maptrace.model.entity.User;
import com.maptrace.model.vo.FollowUserVO;
import com.maptrace.service.FollowService;
import com.maptrace.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    @Override
    public Map<String, Object> toggle(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能关注自己");
        }

        Follow existing = followMapper.selectOne(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, userId)
                        .eq(Follow::getTargetUserId, targetUserId));

        boolean followed;
        boolean mutual = false;

        if (existing != null) {
            // 取消关注
            followMapper.deleteById(existing.getId());
            followed = false;
            log.info("用户 {} 取消关注 {}", userId, targetUserId);
        } else {
            // 关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setTargetUserId(targetUserId);
            followMapper.insert(follow);
            followed = true;
            log.info("用户 {} 关注了 {}", userId, targetUserId);

            // 检查是否形成互关
            long reverse = followMapper.selectCount(
                    new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getUserId, targetUserId)
                            .eq(Follow::getTargetUserId, userId));
            mutual = reverse > 0;

            // 发送通知
            if (mutual) {
                notificationService.createNotification(targetUserId, userId, "mutual_follow", null, null, "你们已成为互关好友");
                notificationService.createNotification(userId, targetUserId, "mutual_follow", null, null, "你们已成为互关好友");
            } else {
                notificationService.createNotification(targetUserId, userId, "follow", null, null, "关注了你");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("followed", followed);
        result.put("mutual", mutual);
        return result;
    }

    @Override
    public Map<String, Object> getStatus(Long userId, Long targetUserId) {
        long followed = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, userId)
                        .eq(Follow::getTargetUserId, targetUserId));
        long followedBy = followMapper.selectCount(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, targetUserId)
                        .eq(Follow::getTargetUserId, userId));

        Map<String, Object> result = new HashMap<>();
        result.put("followed", followed > 0);
        result.put("followedBy", followedBy > 0);
        result.put("mutual", followed > 0 && followedBy > 0);
        return result;
    }

    @Override
    public Map<String, Object> getFollowing(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Follow> follows = followMapper.selectList(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, userId)
                        .orderByDesc(Follow::getCreateTime)
                        .last("LIMIT " + offset + ", " + size));
        long total = followMapper.countFollowing(userId);

        Set<Long> mutualIds = new HashSet<>(followMapper.findMutualUserIds(userId));

        // 批量查用户信息，避免 N+1
        List<Long> targetUserIds = follows.stream().map(Follow::getTargetUserId).collect(Collectors.toList());
        Map<Long, User> userMap = targetUserIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(targetUserIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));

        List<FollowUserVO> list = follows.stream().map(f -> {
            FollowUserVO vo = new FollowUserVO();
            vo.setUserId(f.getTargetUserId());
            vo.setMutual(mutualIds.contains(f.getTargetUserId()));
            User user = userMap.get(f.getTargetUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatarUrl(user.getAvatarUrl());
            }
            return vo;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("hasMore", offset + size < total);
        return result;
    }

    @Override
    public Map<String, Object> getFollowers(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Follow> follows = followMapper.selectList(
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getTargetUserId, userId)
                        .orderByDesc(Follow::getCreateTime)
                        .last("LIMIT " + offset + ", " + size));
        long total = followMapper.countFollowers(userId);

        Set<Long> mutualIds = new HashSet<>(followMapper.findMutualUserIds(userId));

        // 批量查用户信息，避免 N+1
        List<Long> followerUserIds = follows.stream().map(Follow::getUserId).collect(Collectors.toList());
        Map<Long, User> userMap = followerUserIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(followerUserIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));

        List<FollowUserVO> list = follows.stream().map(f -> {
            FollowUserVO vo = new FollowUserVO();
            vo.setUserId(f.getUserId());
            vo.setMutual(mutualIds.contains(f.getUserId()));
            User user = userMap.get(f.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatarUrl(user.getAvatarUrl());
            }
            return vo;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("hasMore", offset + size < total);
        return result;
    }

    @Override
    public Map<String, Object> getCount(Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("followingCount", followMapper.countFollowing(userId));
        result.put("followerCount", followMapper.countFollowers(userId));
        result.put("mutualCount", followMapper.countMutuals(userId));
        return result;
    }

    @Override
    public boolean isMutual(Long userId, Long targetUserId) {
        if (userId == null || targetUserId == null) return false;
        return followMapper.countMutual(userId, targetUserId) > 0;
    }

    @Override
    public List<Long> getMutualUserIds(Long userId) {
        if (userId == null) return Collections.emptyList();
        return followMapper.findMutualUserIds(userId);
    }
}

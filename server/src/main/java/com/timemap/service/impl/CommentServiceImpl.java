package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.timemap.mapper.CommentLikeMapper;
import com.timemap.mapper.CommentMapper;
import com.timemap.mapper.UserMapper;
import com.timemap.model.dto.AddCommentRequest;
import com.timemap.model.dto.CommentPageResponse;
import com.timemap.model.dto.CommentResponse;
import com.timemap.model.entity.Comment;
import com.timemap.model.entity.CommentLike;
import com.timemap.model.entity.User;
import com.timemap.monitor.BusinessMetricsCollector;
import com.timemap.service.CommentService;
import com.timemap.service.NotificationService;
import com.timemap.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final PhotoMapper photoMapper;
    private final BusinessMetricsCollector metricsCollector;

    @Override
    public CommentPageResponse getComments(Long photoId, int page, int size, Long currentUserId) {
        // 查询顶级评论
        Page<Comment> p = new Page<>(page, size);
        LambdaQueryWrapper<Comment> qw = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPhotoId, photoId)
                .eq(Comment::getParentId, 0L)
                .orderByDesc(Comment::getCreateTime);
        commentMapper.selectPage(p, qw);

        long total = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPhotoId, photoId)
                .eq(Comment::getParentId, 0L));

        List<CommentResponse> list = p.getRecords().stream()
                .map(c -> toResponse(c, currentUserId))
                .collect(Collectors.toList());

        CommentPageResponse resp = new CommentPageResponse();
        resp.setList(list);
        resp.setTotal(total);
        resp.setHasMore((long) page * size < total);
        return resp;
    }

    @Override
    public CommentPageResponse getReplies(Long commentId, int page, int size, Long currentUserId) {
        Page<Comment> p = new Page<>(page, size);
        LambdaQueryWrapper<Comment> qw = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getParentId, commentId)
                .orderByAsc(Comment::getCreateTime);
        commentMapper.selectPage(p, qw);

        List<CommentResponse> list = p.getRecords().stream()
                .map(c -> toResponse(c, currentUserId))
                .collect(Collectors.toList());

        CommentPageResponse resp = new CommentPageResponse();
        resp.setList(list);
        resp.setTotal(p.getTotal());
        resp.setHasMore((long) page * size < p.getTotal());
        return resp;
    }

    @Override
    @Transactional
    public CommentResponse addComment(AddCommentRequest req, Long userId) {
        Comment comment = new Comment();
        comment.setPhotoId(req.getPhotoId());
        comment.setUserId(userId);
        comment.setContent(req.getContent());
        comment.setParentId(req.getParentId() != null ? req.getParentId() : 0L);
        comment.setReplyToUserId(req.getReplyToUserId() != null ? req.getReplyToUserId() : 0L);
        comment.setLikeCount(0);
        comment.setReplyCount(0);
        commentMapper.insert(comment);

        // 监控埋点
        String commentType = comment.getParentId() != 0L ? "reply" : "top_level";
        metricsCollector.recordComment(String.valueOf(userId), commentType);

        // 如果是回复，更新父评论的 replyCount
        if (comment.getParentId() != 0L) {
            Comment parent = commentMapper.selectById(comment.getParentId());
            if (parent != null) {
                parent.setReplyCount(parent.getReplyCount() + 1);
                commentMapper.updateById(parent);
                // 通知父评论作者：有人回复了你的评论
                notificationService.createNotification(
                        parent.getUserId(), userId, "reply",
                        req.getPhotoId(), comment.getId(), req.getContent());
            }
            // 如果回复了特定用户（且不是父评论作者），也通知被回复者
            if (req.getReplyToUserId() != null && req.getReplyToUserId() != 0L) {
                Comment parentComment = commentMapper.selectById(comment.getParentId());
                if (parentComment == null || !req.getReplyToUserId().equals(parentComment.getUserId())) {
                    notificationService.createNotification(
                            req.getReplyToUserId(), userId, "reply",
                            req.getPhotoId(), comment.getId(), req.getContent());
                }
            }
        } else {
            // 顶级评论：通知照片作者
            com.timemap.model.entity.Photo photo = photoMapper.selectById(req.getPhotoId());
            if (photo != null) {
                notificationService.createNotification(
                        photo.getUserId(), userId, "comment",
                        req.getPhotoId(), comment.getId(), req.getContent());
            }
        }

        return toResponse(comment, userId);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        deleteCommentInternal(commentId, userId, true);
    }

    @Override
    @Transactional
    public void deleteCommentByAdmin(Long commentId) {
        deleteCommentInternal(commentId, null, false);
    }

    private void deleteCommentInternal(Long commentId, Long userId, boolean checkOwner) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }
        if (checkOwner && !comment.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除");
        }

        // 如果是顶级评论，删除所有子评论
        if (comment.getParentId() == 0L) {
            LambdaQueryWrapper<Comment> qw = new LambdaQueryWrapper<Comment>()
                    .eq(Comment::getParentId, commentId);
            commentMapper.delete(qw);
        } else {
            // 子评论，更新父评论 replyCount
            Comment parent = commentMapper.selectById(comment.getParentId());
            if (parent != null) {
                parent.setReplyCount(Math.max(0, parent.getReplyCount() - 1));
                commentMapper.updateById(parent);
            }
        }

        commentMapper.deleteById(commentId);
        metricsCollector.recordCommentDelete(checkOwner ? "user" : "admin");
    }

    @Override
    @Transactional
    public Map<String, Object> toggleLike(Long commentId, Long userId) {
        LambdaQueryWrapper<CommentLike> qw = new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getCommentId, commentId)
                .eq(CommentLike::getUserId, userId);
        CommentLike existing = commentLikeMapper.selectOne(qw);

        Comment comment = commentMapper.selectById(commentId);
        boolean liked;
        if (existing != null) {
            commentLikeMapper.deleteById(existing.getId());
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            liked = false;
            metricsCollector.recordLike(String.valueOf(userId), "comment", "unlike");
        } else {
            CommentLike cl = new CommentLike();
            cl.setCommentId(commentId);
            cl.setUserId(userId);
            commentLikeMapper.insert(cl);
            comment.setLikeCount(comment.getLikeCount() + 1);
            liked = true;
            metricsCollector.recordLike(String.valueOf(userId), "comment", "like");
            // 通知评论作者
            notificationService.createNotification(
                    comment.getUserId(), userId, "comment_like",
                    comment.getPhotoId(), commentId, comment.getContent());
        }
        commentMapper.updateById(comment);

        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", comment.getLikeCount());
        return result;
    }

    private CommentResponse toResponse(Comment c, Long currentUserId) {
        CommentResponse r = new CommentResponse();
        r.setId(c.getId());
        r.setUserId(c.getUserId());
        r.setContent(c.getContent());
        r.setLikeCount(c.getLikeCount());
        r.setReplyCount(c.getReplyCount());
        r.setCreateTime(c.getCreateTime() != null ? c.getCreateTime().toString() : "");
        r.setReplies(new ArrayList<>());

        // 用户信息
        User user = userMapper.selectById(c.getUserId());
        if (user != null) {
            r.setNickname(user.getNickname());
            r.setAvatarUrl(user.getAvatarUrl());
        }

        // 被回复者昵称
        if (c.getReplyToUserId() != null && c.getReplyToUserId() != 0L) {
            User replyTo = userMapper.selectById(c.getReplyToUserId());
            if (replyTo != null) {
                r.setReplyToNickname(replyTo.getNickname());
            }
        }

        // 当前用户是否点赞
        if (currentUserId != null && currentUserId != 0L) {
            long likeCount = commentLikeMapper.selectCount(new LambdaQueryWrapper<CommentLike>()
                    .eq(CommentLike::getCommentId, c.getId())
                    .eq(CommentLike::getUserId, currentUserId));
            r.setLiked(likeCount > 0);
        } else {
            r.setLiked(false);
        }

        return r;
    }
}

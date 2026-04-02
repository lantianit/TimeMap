package com.maptrace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.maptrace.common.BusinessException;
import com.maptrace.common.ErrorCode;
import com.maptrace.mapper.CommentLikeMapper;
import com.maptrace.mapper.CommentMapper;
import com.maptrace.mapper.UserMapper;
import com.maptrace.model.dto.AddCommentRequest;
import com.maptrace.model.vo.CommentPageVO;
import com.maptrace.model.vo.CommentVO;
import com.maptrace.model.entity.Comment;
import com.maptrace.model.entity.CommentLike;
import com.maptrace.model.entity.User;
import com.maptrace.monitor.BusinessMetricsCollector;
import com.maptrace.service.CommentService;
import com.maptrace.service.NotificationService;
import com.maptrace.mapper.PhotoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
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
    public CommentPageVO getComments(Long photoId, int page, int size, Long currentUserId) {
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

        List<CommentVO> list = batchToResponse(p.getRecords(), currentUserId);

        CommentPageVO resp = new CommentPageVO();
        resp.setList(list);
        resp.setTotal(total);
        resp.setHasMore((long) page * size < total);
        return resp;
    }

    @Override
    public CommentPageVO getReplies(Long commentId, int page, int size, Long currentUserId) {
        Page<Comment> p = new Page<>(page, size);
        LambdaQueryWrapper<Comment> qw = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getParentId, commentId)
                .orderByAsc(Comment::getCreateTime);
        commentMapper.selectPage(p, qw);

        List<CommentVO> list = batchToResponse(p.getRecords(), currentUserId);

        CommentPageVO resp = new CommentPageVO();
        resp.setList(list);
        resp.setTotal(p.getTotal());
        resp.setHasMore((long) page * size < p.getTotal());
        return resp;
    }

    @Override
    @Transactional
    public CommentVO addComment(AddCommentRequest req, Long userId) {
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
            com.maptrace.model.entity.Photo photo = photoMapper.selectById(req.getPhotoId());
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
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (checkOwner && !comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.COMMENT_DELETE_FORBIDDEN);
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

    private CommentVO toResponse(Comment c, Long currentUserId) {
        return batchToResponse(List.of(c), currentUserId).get(0);
    }

    /** 批量转换评论，避免 N+1 查询 */
    private List<CommentVO> batchToResponse(List<Comment> comments, Long currentUserId) {
        if (comments.isEmpty()) return new ArrayList<>();

        // 批量查用户信息
        Set<Long> userIds = new HashSet<>();
        for (Comment c : comments) {
            userIds.add(c.getUserId());
            if (c.getReplyToUserId() != null && c.getReplyToUserId() != 0L) {
                userIds.add(c.getReplyToUserId());
            }
        }
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));

        // 批量查当前用户的点赞状态
        Set<Long> likedCommentIds = Collections.emptySet();
        if (currentUserId != null && currentUserId != 0L) {
            List<Long> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toList());
            likedCommentIds = commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>()
                    .eq(CommentLike::getUserId, currentUserId)
                    .in(CommentLike::getCommentId, commentIds))
                    .stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
        }

        Set<Long> finalLikedIds = likedCommentIds;
        return comments.stream().map(c -> {
            CommentVO r = new CommentVO();
            r.setId(c.getId());
            r.setUserId(c.getUserId());
            r.setContent(c.getContent());
            r.setLikeCount(c.getLikeCount());
            r.setReplyCount(c.getReplyCount());
            r.setCreateTime(c.getCreateTime() != null ? c.getCreateTime().toString() : "");
            r.setReplies(new ArrayList<>());

            User user = userMap.get(c.getUserId());
            if (user != null) {
                r.setNickname(user.getNickname());
                r.setAvatarUrl(user.getAvatarUrl());
            }

            if (c.getReplyToUserId() != null && c.getReplyToUserId() != 0L) {
                User replyTo = userMap.get(c.getReplyToUserId());
                if (replyTo != null) {
                    r.setReplyToNickname(replyTo.getNickname());
                }
            }

            r.setLiked(finalLikedIds.contains(c.getId()));
            return r;
        }).collect(Collectors.toList());
    }
}

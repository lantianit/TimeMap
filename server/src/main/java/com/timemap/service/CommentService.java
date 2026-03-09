package com.timemap.service;

import com.timemap.model.dto.AddCommentRequest;
import com.timemap.model.dto.CommentPageResponse;
import com.timemap.model.dto.CommentResponse;

import java.util.Map;

public interface CommentService {
    CommentPageResponse getComments(Long photoId, int page, int size, Long currentUserId);
    CommentPageResponse getReplies(Long commentId, int page, int size, Long currentUserId);
    CommentResponse addComment(AddCommentRequest req, Long userId);
    void deleteComment(Long commentId, Long userId);
    Map<String, Object> toggleLike(Long commentId, Long userId);
}

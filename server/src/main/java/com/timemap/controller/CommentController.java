package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.AddCommentRequest;
import com.timemap.model.dto.CommentPageResponse;
import com.timemap.model.dto.CommentResponse;
import com.timemap.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/list")
    public Result<CommentPageResponse> list(
            @RequestParam("photoId") Long photoId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        return Result.ok(commentService.getComments(photoId, page, size, userId));
    }

    @GetMapping("/replies")
    public Result<CommentPageResponse> replies(
            @RequestParam("commentId") Long commentId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        return Result.ok(commentService.getReplies(commentId, page, size, userId));
    }

    @PostMapping("/add")
    public Result<CommentResponse> add(
            @RequestBody AddCommentRequest req,
            @RequestAttribute("userId") Long userId) {
        if (req.getContent() == null || req.getContent().trim().isEmpty()) {
            return Result.fail("评论内容不能为空");
        }
        if (req.getContent().length() > 500) {
            return Result.fail("评论内容不能超过500字");
        }
        return Result.ok(commentService.addComment(req, userId));
    }

    @PostMapping("/delete")
    public Result<Void> delete(
            @RequestParam("commentId") Long commentId,
            @RequestAttribute("userId") Long userId) {
        commentService.deleteComment(commentId, userId);
        return Result.ok();
    }

    @PostMapping("/like")
    public Result<Map<String, Object>> like(
            @RequestParam("commentId") Long commentId,
            @RequestAttribute("userId") Long userId) {
        return Result.ok(commentService.toggleLike(commentId, userId));
    }
}

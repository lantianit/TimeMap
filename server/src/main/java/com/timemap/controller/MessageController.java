package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.ConversationResponse;
import com.timemap.model.dto.MessageResponse;
import com.timemap.model.dto.SendMessageRequest;
import com.timemap.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 会话列表 */
    @GetMapping("/conversations")
    public Result<List<ConversationResponse>> conversations(
            @RequestAttribute("userId") Long userId) {
        return Result.ok(messageService.getConversations(userId));
    }

    /** 聊天记录 */
    @GetMapping("/history")
    public Result<List<MessageResponse>> history(
            @RequestAttribute("userId") Long userId,
            @RequestParam("otherUserId") Long otherUserId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "30") int size) {
        return Result.ok(messageService.getChatHistory(userId, otherUserId, page, size));
    }

    /** 发送消息 */
    @PostMapping("/send")
    public Result<MessageResponse> send(
            @RequestBody SendMessageRequest req,
            @RequestAttribute("userId") Long userId) {
        if (req.getContent() == null || req.getContent().trim().isEmpty()) {
            return Result.fail("消息内容不能为空");
        }
        return Result.ok(messageService.sendMessage(req, userId));
    }

    /** 标记已读 */
    @PostMapping("/read")
    public Result<Void> read(
            @RequestParam("fromUserId") Long fromUserId,
            @RequestAttribute("userId") Long userId) {
        messageService.markAsRead(fromUserId, userId);
        return Result.ok();
    }

    /** 未读消息总数 */
    @GetMapping("/unread")
    public Result<Map<String, Integer>> unread(
            @RequestAttribute("userId") Long userId) {
        return Result.ok(Map.of("count", messageService.getUnreadCount(userId)));
    }
}

package com.timemap.service;

import com.timemap.model.dto.ConversationResponse;
import com.timemap.model.dto.MessageResponse;
import com.timemap.model.dto.SendMessageRequest;

import java.util.List;

public interface MessageService {
    List<ConversationResponse> getConversations(Long userId);
    List<MessageResponse> getChatHistory(Long userId, Long otherUserId, int page, int size);
    MessageResponse sendMessage(SendMessageRequest req, Long userId);
    void markAsRead(Long fromUserId, Long toUserId);
    int getUnreadCount(Long userId);
}

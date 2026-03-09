package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.timemap.mapper.MessageMapper;
import com.timemap.mapper.UserMapper;
import com.timemap.model.dto.ConversationResponse;
import com.timemap.model.dto.MessageResponse;
import com.timemap.model.dto.SendMessageRequest;
import com.timemap.model.entity.Message;
import com.timemap.model.entity.User;
import com.timemap.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;

    @Override
    public List<ConversationResponse> getConversations(Long userId) {
        return messageMapper.findConversations(userId);
    }

    @Override
    public List<MessageResponse> getChatHistory(Long userId, Long otherUserId, int page, int size) {
        Page<Message> p = new Page<>(page, size);
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<Message>()
                .and(w -> w
                        .and(a -> a.eq(Message::getFromUserId, userId).eq(Message::getToUserId, otherUserId))
                        .or(b -> b.eq(Message::getFromUserId, otherUserId).eq(Message::getToUserId, userId))
                )
                .orderByDesc(Message::getCreateTime);
        messageMapper.selectPage(p, qw);

        return p.getRecords().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public MessageResponse sendMessage(SendMessageRequest req, Long userId) {
        Message msg = new Message();
        msg.setFromUserId(userId);
        msg.setToUserId(req.getToUserId());
        msg.setContent(req.getContent());
        msg.setMsgType(req.getMsgType() != null ? req.getMsgType() : "text");
        msg.setReadStatus(0);
        messageMapper.insert(msg);
        return toResponse(msg);
    }

    @Override
    public void markAsRead(Long fromUserId, Long toUserId) {
        messageMapper.markAsRead(fromUserId, toUserId);
    }

    @Override
    public int getUnreadCount(Long userId) {
        return Math.toIntExact(messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getToUserId, userId)
                .eq(Message::getReadStatus, 0)));
    }

    private MessageResponse toResponse(Message m) {
        MessageResponse r = new MessageResponse();
        r.setId(m.getId());
        r.setFromUserId(m.getFromUserId());
        r.setToUserId(m.getToUserId());
        r.setContent(m.getContent());
        r.setMsgType(m.getMsgType());
        r.setReadStatus(m.getReadStatus());
        r.setCreateTime(m.getCreateTime() != null ? m.getCreateTime().toString() : "");

        User from = userMapper.selectById(m.getFromUserId());
        if (from != null) {
            r.setFromNickname(from.getNickname());
            r.setFromAvatarUrl(from.getAvatarUrl());
        }
        return r;
    }
}

package com.maptrace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maptrace.mapper.MessageMapper;
import com.maptrace.mapper.UserMapper;
import com.maptrace.model.vo.ConversationVO;
import com.maptrace.model.vo.MessageVO;
import com.maptrace.model.dto.SendMessageRequest;
import com.maptrace.model.entity.Message;
import com.maptrace.model.entity.User;
import com.maptrace.monitor.BusinessMetricsCollector;
import com.maptrace.service.MessageService;
import com.maptrace.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final BusinessMetricsCollector metricsCollector;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final ExecutorService wsPushExecutor = Executors.newFixedThreadPool(4);

    @Override
    public List<ConversationVO> getConversations(Long userId) {
        return messageMapper.findConversations(userId);
    }

    @Override
    public List<MessageVO> getChatHistory(Long userId, Long otherUserId, int page, int size) {
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
    public MessageVO sendMessage(SendMessageRequest req, Long userId) {
        Message msg = new Message();
        msg.setFromUserId(userId);
        msg.setToUserId(req.getToUserId());
        msg.setContent(req.getContent());
        msg.setMsgType(req.getMsgType() != null ? req.getMsgType() : "text");
        msg.setReadStatus(0);
        messageMapper.insert(msg);

        metricsCollector.recordMessage(String.valueOf(userId));

        MessageVO vo = toResponse(msg);

        // 异步推送，不阻塞 HTTP 响应
        asyncPushToUser(req.getToUserId(), vo);

        return vo;
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

    /**
     * 异步通过 WebSocket 推送新消息给目标用户
     */
    private void asyncPushToUser(Long toUserId, MessageVO messageVO) {
        wsPushExecutor.submit(() -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "new_message");
                payload.put("data", messageVO);
                String json = objectMapper.writeValueAsString(payload);
                boolean sent = sessionManager.sendToUser(toUserId, json);
                if (!sent) {
                    log.debug("用户 {} 不在线，WebSocket 推送跳过", toUserId);
                }
            } catch (Exception e) {
                log.warn("WebSocket 推送异常: toUserId={}, error={}", toUserId, e.getMessage());
            }
        });
    }

    private MessageVO toResponse(Message m) {
        MessageVO r = new MessageVO();
        r.setId(m.getId());
        r.setFromUserId(m.getFromUserId());
        r.setToUserId(m.getToUserId());
        r.setContent(m.getContent());
        r.setMsgType(m.getMsgType());
        r.setReadStatus(m.getReadStatus());
        // 输出毫秒时间戳，前端不再有时区解析问题
        if (m.getCreateTime() != null) {
            long millis = m.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            r.setCreateTime(String.valueOf(millis));
        } else {
            r.setCreateTime("");
        }

        User from = userMapper.selectById(m.getFromUserId());
        if (from != null) {
            r.setFromNickname(from.getNickname());
            r.setFromAvatarUrl(from.getAvatarUrl());
        }
        return r;
    }
}

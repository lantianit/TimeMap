package com.timemap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.timemap.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.timemap.model.dto.ConversationResponse;
import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 获取用户的会话列表（每个聊天对象的最新一条消息）
     */
    @Select("""
        <script>
        SELECT
            IF(m.from_user_id = #{userId}, m.to_user_id, m.from_user_id) AS userId,
            u.nickname, u.avatar_url,
            m.content AS lastMessage,
            m.create_time AS lastTime,
            IFNULL(unread.cnt, 0) AS unreadCount
        FROM t_message m
        JOIN (
            SELECT MAX(id) AS max_id
            FROM t_message
            WHERE deleted = 0 AND (from_user_id = #{userId} OR to_user_id = #{userId})
            GROUP BY IF(from_user_id = #{userId}, to_user_id, from_user_id)
        ) latest ON m.id = latest.max_id
        LEFT JOIN t_user u ON u.id = IF(m.from_user_id = #{userId}, m.to_user_id, m.from_user_id)
        LEFT JOIN (
            SELECT from_user_id, COUNT(*) AS cnt
            FROM t_message
            WHERE deleted = 0 AND to_user_id = #{userId} AND read_status = 0
            GROUP BY from_user_id
        ) unread ON unread.from_user_id = IF(m.from_user_id = #{userId}, m.to_user_id, m.from_user_id)
        ORDER BY m.create_time DESC
        </script>
    """)
    List<ConversationResponse> findConversations(@Param("userId") Long userId);

    /**
     * 将某个用户发给我的所有消息标记为已读
     */
    @Update("UPDATE t_message SET read_status = 1 WHERE from_user_id = #{fromUserId} AND to_user_id = #{toUserId} AND read_status = 0 AND deleted = 0")
    int markAsRead(@Param("fromUserId") Long fromUserId, @Param("toUserId") Long toUserId);
}

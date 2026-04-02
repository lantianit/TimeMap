package com.maptrace.service;

import com.maptrace.model.vo.FollowUserVO;
import java.util.List;
import java.util.Map;

public interface FollowService {

    /** 关注/取消关注切换 */
    Map<String, Object> toggle(Long userId, Long targetUserId);

    /** 查询与某用户的关注关系 */
    Map<String, Object> getStatus(Long userId, Long targetUserId);

    /** 我的关注列表 */
    Map<String, Object> getFollowing(Long userId, int page, int size);

    /** 我的粉丝列表 */
    Map<String, Object> getFollowers(Long userId, int page, int size);

    /** 关注/粉丝/互关计数 */
    Map<String, Object> getCount(Long userId);

    /** 判断两个用户是否互关 */
    boolean isMutual(Long userId, Long targetUserId);

    /** 获取与 userId 互关的所有用户ID */
    List<Long> getMutualUserIds(Long userId);
}

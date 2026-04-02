package com.maptrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maptrace.model.entity.Follow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    /** 判断是否互关：双方都有关注记录 */
    @Select("""
        SELECT COUNT(*) FROM t_follow f1
        INNER JOIN t_follow f2
          ON f1.user_id = f2.target_user_id
          AND f1.target_user_id = f2.user_id
        WHERE f1.user_id = #{userId} AND f1.target_user_id = #{targetUserId}
    """)
    long countMutual(@Param("userId") Long userId, @Param("targetUserId") Long targetUserId);

    /** 批量获取与 viewerUserId 互关的用户ID列表（用于照片可见性过滤） */
    @Select("""
        SELECT f1.target_user_id FROM t_follow f1
        INNER JOIN t_follow f2
          ON f1.user_id = f2.target_user_id
          AND f1.target_user_id = f2.user_id
        WHERE f1.user_id = #{userId}
    """)
    List<Long> findMutualUserIds(@Param("userId") Long userId);

    /** 关注数 */
    @Select("SELECT COUNT(*) FROM t_follow WHERE user_id = #{userId}")
    long countFollowing(@Param("userId") Long userId);

    /** 粉丝数 */
    @Select("SELECT COUNT(*) FROM t_follow WHERE target_user_id = #{userId}")
    long countFollowers(@Param("userId") Long userId);

    /** 互关数 */
    @Select("""
        SELECT COUNT(*) FROM t_follow f1
        INNER JOIN t_follow f2
          ON f1.user_id = f2.target_user_id
          AND f1.target_user_id = f2.user_id
        WHERE f1.user_id = #{userId}
    """)
    long countMutuals(@Param("userId") Long userId);
}

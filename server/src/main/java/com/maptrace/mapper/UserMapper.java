package com.maptrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maptrace.model.entity.User;
import com.maptrace.model.vo.DashboardStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
        SELECT DATE_FORMAT(create_time, '%m-%d') AS date, COUNT(*) AS count
        FROM t_user
        WHERE create_time >= #{since}
        GROUP BY DATE_FORMAT(create_time, '%m-%d')
        ORDER BY date
    """)
    List<DashboardStatsVO.TrendItem> countDailyUsers(@Param("since") String since);
}

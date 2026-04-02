package com.maptrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maptrace.model.entity.Report;
import com.maptrace.model.vo.DashboardStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    @Select("""
        SELECT DATE_FORMAT(create_time, '%m-%d') AS date, COUNT(*) AS count
        FROM t_report
        WHERE create_time >= #{since}
        GROUP BY DATE_FORMAT(create_time, '%m-%d')
        ORDER BY date
    """)
    List<DashboardStatsVO.TrendItem> countDailyReports(@Param("since") String since);

    @Select("""
        SELECT IFNULL(reason, '未知') AS name, COUNT(*) AS value
        FROM t_report
        WHERE create_time >= #{since}
        GROUP BY reason
    """)
    List<DashboardStatsVO.DistributionItem> countReasonDistribution(@Param("since") String since);

    @Select("""
        SELECT AVG(TIMESTAMPDIFF(MINUTE, create_time, handled_time)) / 60.0
        FROM t_report
        WHERE status IN (1, 2) AND handled_time IS NOT NULL AND create_time >= #{since}
    """)
    Double avgHandleTimeHours(@Param("since") String since);

    @Select("SELECT COUNT(*) FROM t_report WHERE status = 1 AND handled_time IS NOT NULL AND create_time >= #{since}")
    long countResolved(@Param("since") String since);

    @Select("SELECT COUNT(*) FROM t_report WHERE status = 2 AND handled_time IS NOT NULL AND create_time >= #{since}")
    long countRejected(@Param("since") String since);
}

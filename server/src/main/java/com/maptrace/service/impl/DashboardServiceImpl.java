package com.maptrace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maptrace.mapper.*;
import com.maptrace.model.vo.DashboardStatsVO;
import com.maptrace.model.entity.*;
import com.maptrace.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ReportMapper reportMapper;
    private final AppealMapper appealMapper;
    private final UserMapper userMapper;
    private final PhotoMapper photoMapper;

    // Simple in-memory cache
    private DashboardStatsVO cachedStats;
    private long cacheTimestamp = 0;
    private static final long CACHE_TTL = 10 * 60 * 1000L; // 10 minutes

    @Override
    public DashboardStatsVO getStats() {
        long now = System.currentTimeMillis();
        if (cachedStats != null && (now - cacheTimestamp) < CACHE_TTL) {
            return cachedStats;
        }
        DashboardStatsVO stats = buildStats();
        cachedStats = stats;
        cacheTimestamp = now;
        return stats;
    }

    private DashboardStatsVO buildStats() {
        DashboardStatsVO s = new DashboardStatsVO();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        String thirtyDaysAgo = LocalDate.now().minusDays(30).atStartOfDay()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // Counts — 使用 selectCount 只返回数字，不拉全量数据
        s.setTodayReports(reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .ge(Report::getCreateTime, todayStart)));
        s.setPendingReports(reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getStatus, 0)));
        s.setPendingAppeals(appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getStatus, 0)));
        s.setTodayUsers(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, todayStart)));
        s.setTotalUsers(userMapper.selectCount(new LambdaQueryWrapper<User>()));
        s.setTotalPhotos(photoMapper.selectCount(new LambdaQueryWrapper<Photo>()));

        // 趋势数据 — SQL GROUP BY 聚合，不拉全量记录
        List<DashboardStatsVO.TrendItem> reportTrend = reportMapper.countDailyReports(thirtyDaysAgo);
        s.setReportTrend(fillMissingDays(reportTrend, 30));

        s.setReasonDistribution(reportMapper.countReasonDistribution(thirtyDaysAgo));

        List<DashboardStatsVO.TrendItem> userTrend = userMapper.countDailyUsers(thirtyDaysAgo);
        s.setUserGrowthTrend(fillMissingDays(userTrend, 30));

        List<DashboardStatsVO.TrendItem> photoTrend = photoMapper.countDailyPhotos(thirtyDaysAgo);
        s.setPhotoUploadTrend(fillMissingDays(photoTrend, 30));

        // 处理效率 — SQL 聚合
        Double avgHours = reportMapper.avgHandleTimeHours(thirtyDaysAgo);
        s.setAvgHandleTimeHours(avgHours != null ? Math.round(avgHours * 10) / 10.0 : 0);
        long resolved = reportMapper.countResolved(thirtyDaysAgo);
        long rejected = reportMapper.countRejected(thirtyDaysAgo);
        long total = resolved + rejected;
        s.setResolveRate(total > 0 ? Math.round(resolved * 1000.0 / total) / 10.0 : 0);
        s.setRejectRate(total > 0 ? Math.round(rejected * 1000.0 / total) / 10.0 : 0);

        return s;
    }

    /** 补齐 SQL 结果中缺失的日期（count=0 的天不会出现在 GROUP BY 结果中） */
    private List<DashboardStatsVO.TrendItem> fillMissingDays(List<DashboardStatsVO.TrendItem> sqlResult, int days) {
        Map<String, Long> countMap = new HashMap<>();
        for (DashboardStatsVO.TrendItem item : sqlResult) {
            countMap.put(item.getDate(), item.getCount());
        }
        List<DashboardStatsVO.TrendItem> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            DashboardStatsVO.TrendItem item = new DashboardStatsVO.TrendItem();
            item.setDate(date.format(fmt));
            item.setCount(countMap.getOrDefault(date.format(fmt), 0L));
            trend.add(item);
        }
        return trend;
    }
}

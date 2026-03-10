package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.timemap.mapper.*;
import com.timemap.model.dto.DashboardStatsResponse;
import com.timemap.model.entity.*;
import com.timemap.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ReportMapper reportMapper;
    private final AppealMapper appealMapper;
    private final UserMapper userMapper;
    private final PhotoMapper photoMapper;

    // Simple in-memory cache
    private DashboardStatsResponse cachedStats;
    private long cacheTimestamp = 0;
    private static final long CACHE_TTL = 10 * 60 * 1000L; // 10 minutes

    @Override
    public DashboardStatsResponse getStats() {
        long now = System.currentTimeMillis();
        if (cachedStats != null && (now - cacheTimestamp) < CACHE_TTL) {
            return cachedStats;
        }
        DashboardStatsResponse stats = buildStats();
        cachedStats = stats;
        cacheTimestamp = now;
        return stats;
    }

    private DashboardStatsResponse buildStats() {
        DashboardStatsResponse s = new DashboardStatsResponse();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime thirtyDaysAgo = LocalDate.now().minusDays(30).atStartOfDay();

        // Counts
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

        // Report trend (30 days)
        List<Report> recentReports = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .ge(Report::getCreateTime, thirtyDaysAgo)
                .orderByAsc(Report::getCreateTime));
        s.setReportTrend(buildDailyTrend(recentReports.stream()
                .map(r -> r.getCreateTime().toLocalDate()).collect(Collectors.toList()), 30));

        // Reason distribution
        Map<String, Long> reasonMap = recentReports.stream()
                .collect(Collectors.groupingBy(r -> r.getReason() != null ? r.getReason() : "未知", Collectors.counting()));
        s.setReasonDistribution(reasonMap.entrySet().stream().map(e -> {
            DashboardStatsResponse.DistributionItem item = new DashboardStatsResponse.DistributionItem();
            item.setName(e.getKey());
            item.setValue(e.getValue());
            return item;
        }).collect(Collectors.toList()));

        // User growth trend
        List<User> recentUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, thirtyDaysAgo)
                .orderByAsc(User::getCreateTime));
        s.setUserGrowthTrend(buildDailyTrend(recentUsers.stream()
                .map(u -> u.getCreateTime().toLocalDate()).collect(Collectors.toList()), 30));

        // Photo upload trend
        List<Photo> recentPhotos = photoMapper.selectList(new LambdaQueryWrapper<Photo>()
                .ge(Photo::getCreateTime, thirtyDaysAgo)
                .orderByAsc(Photo::getCreateTime));
        s.setPhotoUploadTrend(buildDailyTrend(recentPhotos.stream()
                .map(p -> p.getCreateTime().toLocalDate()).collect(Collectors.toList()), 30));

        // Avg handle time, resolve rate, reject rate
        List<Report> handledReports = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .in(Report::getStatus, 1, 2)
                .isNotNull(Report::getHandledTime)
                .ge(Report::getCreateTime, thirtyDaysAgo));
        if (!handledReports.isEmpty()) {
            double totalHours = handledReports.stream()
                    .filter(r -> r.getHandledTime() != null && r.getCreateTime() != null)
                    .mapToDouble(r -> java.time.Duration.between(r.getCreateTime(), r.getHandledTime()).toMinutes() / 60.0)
                    .average().orElse(0);
            s.setAvgHandleTimeHours(Math.round(totalHours * 10) / 10.0);
            long resolved = handledReports.stream().filter(r -> r.getStatus() == 1).count();
            long rejected = handledReports.stream().filter(r -> r.getStatus() == 2).count();
            long total = resolved + rejected;
            s.setResolveRate(total > 0 ? Math.round(resolved * 1000.0 / total) / 10.0 : 0);
            s.setRejectRate(total > 0 ? Math.round(rejected * 1000.0 / total) / 10.0 : 0);
        }

        return s;
    }

    private List<DashboardStatsResponse.TrendItem> buildDailyTrend(List<LocalDate> dates, int days) {
        Map<LocalDate, Long> countMap = dates.stream()
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));
        List<DashboardStatsResponse.TrendItem> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            DashboardStatsResponse.TrendItem item = new DashboardStatsResponse.TrendItem();
            item.setDate(date.format(fmt));
            item.setCount(countMap.getOrDefault(date, 0L));
            trend.add(item);
        }
        return trend;
    }
}

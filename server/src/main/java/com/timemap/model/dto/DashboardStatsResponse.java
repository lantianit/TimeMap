package com.timemap.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class DashboardStatsResponse {
    private long todayReports;
    private long pendingReports;
    private long pendingAppeals;
    private long todayUsers;
    private long totalUsers;
    private long totalPhotos;
    private List<TrendItem> reportTrend;
    private List<DistributionItem> reasonDistribution;
    private List<TrendItem> userGrowthTrend;
    private List<TrendItem> photoUploadTrend;
    private double avgHandleTimeHours;
    private double resolveRate;
    private double rejectRate;

    @Data
    public static class TrendItem {
        private String date;
        private long count;
    }

    @Data
    public static class DistributionItem {
        private String name;
        private long value;
    }
}

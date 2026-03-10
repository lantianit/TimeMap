package com.timemap.service;

import com.timemap.model.dto.*;

import java.util.List;

public interface ReportService {
    ReportSubmitResponse submitReport(String targetType, Long targetId, String reason, String description, Long userId);
    MyReportPageResponse getMyReports(Long userId, int page, int size);
    AdminReportPageResponse getAdminReports(Long adminUserId, Integer status, String targetType, int page, int size);
    AdminReportDetailResponse getAdminReportDetail(Long adminUserId, Long reportId);
    void resolveReport(Long adminUserId, ResolveReportRequest request);
    void rejectReport(Long adminUserId, RejectReportRequest request);
    void batchResolve(Long adminUserId, BatchReportActionRequest request);
    void batchReject(Long adminUserId, BatchReportActionRequest request);
    PendingReportCountResponse getPendingCount(Long adminUserId);
    List<AggregatedReportResponse> getAggregatedReports(Long adminUserId, int page, int size);
    void punishUser(Long adminUserId, PunishUserRequest request);
    UserViolationPageResponse getUserViolations(Long adminUserId, Long userId, int page, int size);
    UserViolationPageResponse getMyViolations(Long userId, int page, int size);
}

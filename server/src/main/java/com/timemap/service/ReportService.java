package com.timemap.service;

import com.timemap.model.dto.*;
import com.timemap.model.vo.*;
import java.util.List;

public interface ReportService {
    ReportSubmitVO submitReport(String targetType, Long targetId, String reason, String description, Long userId);
    MyReportPageVO getMyReports(Long userId, int page, int size);
    AdminReportPageVO getAdminReports(Long adminUserId, Integer status, String targetType, int page, int size);
    AdminReportDetailVO getAdminReportDetail(Long adminUserId, Long reportId);
    void resolveReport(Long adminUserId, ResolveReportRequest request);
    void rejectReport(Long adminUserId, RejectReportRequest request);
    void batchResolve(Long adminUserId, BatchReportActionRequest request);
    void batchReject(Long adminUserId, BatchReportActionRequest request);
    PendingReportCountVO getPendingCount(Long adminUserId);
    List<AggregatedReportVO> getAggregatedReports(Long adminUserId, int page, int size);
    void punishUser(Long adminUserId, PunishUserRequest request);
    UserViolationPageVO getUserViolations(Long adminUserId, Long userId, int page, int size);
    UserViolationPageVO getMyViolations(Long userId, int page, int size);
}

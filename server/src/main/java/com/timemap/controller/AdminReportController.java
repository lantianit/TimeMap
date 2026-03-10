package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.*;
import com.timemap.service.AdminLogService;
import com.timemap.service.AppealService;
import com.timemap.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/report")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;
    private final AppealService appealService;
    private final AdminLogService adminLogService;

    @GetMapping("/list")
    public Result<AdminReportPageResponse> list(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.ok(reportService.getAdminReports(userId, status, targetType, page, size));
    }

    @GetMapping("/detail/{id}")
    public Result<AdminReportDetailResponse> detail(
            @RequestAttribute("userId") Long userId,
            @PathVariable("id") Long id) {
        return Result.ok(reportService.getAdminReportDetail(userId, id));
    }

    @PostMapping("/resolve")
    public Result<Void> resolve(
            @RequestAttribute("userId") Long userId,
            @RequestBody ResolveReportRequest request) {
        reportService.resolveReport(userId, request);
        return Result.ok();
    }

    @PostMapping("/reject")
    public Result<Void> reject(
            @RequestAttribute("userId") Long userId,
            @RequestBody RejectReportRequest request) {
        reportService.rejectReport(userId, request);
        return Result.ok();
    }

    @PostMapping("/batch-resolve")
    public Result<Void> batchResolve(
            @RequestAttribute("userId") Long userId,
            @RequestBody BatchReportActionRequest request) {
        reportService.batchResolve(userId, request);
        return Result.ok();
    }

    @PostMapping("/batch-reject")
    public Result<Void> batchReject(
            @RequestAttribute("userId") Long userId,
            @RequestBody BatchReportActionRequest request) {
        reportService.batchReject(userId, request);
        return Result.ok();
    }

    @GetMapping("/pending-count")
    public Result<PendingReportCountResponse> pendingCount(
            @RequestAttribute("userId") Long userId) {
        PendingReportCountResponse r = reportService.getPendingCount(userId);
        r.setAppealCount(appealService.getPendingCount());
        return Result.ok(r);
    }

    @GetMapping("/aggregated")
    public Result<List<AggregatedReportResponse>> aggregated(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.ok(reportService.getAggregatedReports(userId, page, size));
    }

    @PostMapping("/punish")
    public Result<Void> punishUser(
            @RequestAttribute("userId") Long userId,
            @RequestBody PunishUserRequest request) {
        reportService.punishUser(userId, request);
        return Result.ok();
    }

    @GetMapping("/user-violations")
    public Result<UserViolationPageResponse> userViolations(
            @RequestAttribute("userId") Long userId,
            @RequestParam("targetUserId") Long targetUserId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.ok(reportService.getUserViolations(userId, targetUserId, page, size));
    }

    @GetMapping("/appeals")
    public Result<AppealPageResponse> appeals(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.ok(appealService.getAdminAppeals(userId, status, page, size));
    }

    @GetMapping("/appeal/{id}")
    public Result<AppealResponse> appealDetail(
            @RequestAttribute("userId") Long userId,
            @PathVariable("id") Long id) {
        return Result.ok(appealService.getAppealDetail(userId, id));
    }

    @PostMapping("/appeal/resolve")
    public Result<Void> resolveAppeal(
            @RequestAttribute("userId") Long userId,
            @RequestBody HandleAppealRequest request) {
        appealService.resolveAppeal(userId, request);
        return Result.ok();
    }

    @PostMapping("/appeal/reject")
    public Result<Void> rejectAppeal(
            @RequestAttribute("userId") Long userId,
            @RequestBody HandleAppealRequest request) {
        appealService.rejectAppeal(userId, request);
        return Result.ok();
    }

    @GetMapping("/logs")
    public Result<AdminLogPageResponse> logs(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.ok(adminLogService.getLogs(userId, page, size));
    }
}

package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.*;
import com.timemap.service.AppealService;
import com.timemap.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final AppealService appealService;

    @PostMapping("/submit")
    public Result<ReportSubmitResponse> submit(
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") Long targetId,
            @RequestParam("reason") String reason,
            @RequestParam(value = "description", required = false) String description,
            @RequestAttribute("userId") Long userId) {
        return Result.ok(reportService.submitReport(targetType, targetId, reason, description, userId));
    }

    @GetMapping("/my")
    public Result<MyReportPageResponse> myReports(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.ok(reportService.getMyReports(userId, page, size));
    }

    @GetMapping("/my-violations")
    public Result<UserViolationPageResponse> myViolations(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.ok(reportService.getMyViolations(userId, page, size));
    }

    @PostMapping("/appeal")
    public Result<Void> submitAppeal(
            @RequestAttribute("userId") Long userId,
            @RequestBody AppealSubmitRequest request) {
        appealService.submitAppeal(userId, request);
        return Result.ok();
    }

    @GetMapping("/my-appeals")
    public Result<AppealPageResponse> myAppeals(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.ok(appealService.getMyAppeals(userId, page, size));
    }
}

package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.*;
import com.timemap.model.vo.*;
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
    public Result<ReportSubmitVO> submit(
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") Long targetId,
            @RequestParam("reason") String reason,
            @RequestParam(value = "description", required = false) String description,
            @RequestAttribute("userId") Long userId) {
        return Result.success(reportService.submitReport(targetType, targetId, reason, description, userId));
    }

    @GetMapping("/my")
    public Result<MyReportPageVO> myReports(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(reportService.getMyReports(userId, page, size));
    }

    @GetMapping("/my-violations")
    public Result<UserViolationPageVO> myViolations(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(reportService.getMyViolations(userId, page, size));
    }

    @PostMapping("/appeal")
    public Result<Void> submitAppeal(
            @RequestAttribute("userId") Long userId,
            @RequestBody AppealSubmitRequest request) {
        appealService.submitAppeal(userId, request);
        return Result.success();
    }

    @GetMapping("/my-appeals")
    public Result<AppealPageVO> myAppeals(
            @RequestAttribute("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(appealService.getMyAppeals(userId, page, size));
    }
}

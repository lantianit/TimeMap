package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.mapper.ReportMapper;
import com.timemap.model.entity.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportMapper reportMapper;

    @PostMapping("/submit")
    public Result<Void> submit(
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") Long targetId,
            @RequestParam("reason") String reason,
            @RequestParam(value = "description", required = false) String description,
            @RequestAttribute("userId") Long userId) {
        Report report = new Report();
        report.setUserId(userId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(reason);
        report.setDescription(description != null ? description : "");
        report.setStatus(0);
        reportMapper.insert(report);
        return Result.ok();
    }
}

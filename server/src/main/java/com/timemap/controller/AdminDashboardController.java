package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.DashboardStatsResponse;
import com.timemap.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public Result<DashboardStatsResponse> stats() {
        return Result.ok(dashboardService.getStats());
    }
}

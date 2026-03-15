package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.*;
import com.timemap.model.vo.*;
import com.timemap.service.AdminAccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAccountService adminAccountService;

    @PostMapping("/login")
    public Result<AdminLoginVO> login(@RequestBody AdminLoginRequest request,
                                            HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        return Result.success(adminAccountService.login(request, ip, ua));
    }

    @GetMapping("/info")
    public Result<AdminAccountVO> info(@RequestAttribute("adminAccountId") Long adminId) {
        return Result.success(adminAccountService.getInfo(adminId));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestAttribute("adminAccountId") Long adminId,
                                       @RequestBody AdminChangePasswordRequest request,
                                       HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        adminAccountService.changePassword(adminId, request, ip, ua);
        return Result.success();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String rip = request.getHeader("X-Real-IP");
        if (rip != null && !rip.isBlank()) return rip;
        return request.getRemoteAddr();
    }
}

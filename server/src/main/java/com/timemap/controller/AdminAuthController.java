package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.*;
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
    public Result<AdminLoginResponse> login(@RequestBody AdminLoginRequest request,
                                            HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        return Result.ok(adminAccountService.login(request, ip, ua));
    }

    @GetMapping("/info")
    public Result<AdminAccountResponse> info(@RequestAttribute("adminAccountId") Long adminId) {
        return Result.ok(adminAccountService.getInfo(adminId));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestAttribute("adminAccountId") Long adminId,
                                       @RequestBody AdminChangePasswordRequest request,
                                       HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String ua = httpRequest.getHeader("User-Agent");
        adminAccountService.changePassword(adminId, request, ip, ua);
        return Result.ok();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String rip = request.getHeader("X-Real-IP");
        if (rip != null && !rip.isBlank()) return rip;
        return request.getRemoteAddr();
    }
}

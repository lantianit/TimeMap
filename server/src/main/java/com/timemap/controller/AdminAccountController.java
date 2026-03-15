package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.*;
import com.timemap.model.vo.*;
import com.timemap.service.AdminAccountService;
import com.timemap.service.AdminLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/account")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;
    private final AdminLogService adminLogService;

    @GetMapping("/list")
    public Result<List<AdminAccountVO>> list(@RequestAttribute("adminAccountId") Long adminId) {
        return Result.success(adminAccountService.listAccounts(adminId));
    }

    @PostMapping("/create")
    public Result<AdminAccountVO> create(@RequestAttribute("adminAccountId") Long adminId,
                                               @RequestBody CreateAdminAccountRequest request) {
        AdminAccountVO resp = adminAccountService.createAccount(adminId, request);
        adminLogService.log(adminId, "create_admin", "admin_account", resp.getId(), "创建管理员: " + request.getUsername());
        return Result.success(resp);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@RequestAttribute("adminAccountId") Long adminId,
                               @PathVariable("id") Long targetId,
                               @RequestBody CreateAdminAccountRequest request) {
        adminAccountService.updateAccount(adminId, targetId, request);
        adminLogService.log(adminId, "update_admin", "admin_account", targetId, "更新管理员信息");
        return Result.success();
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@RequestAttribute("adminAccountId") Long adminId,
                                      @PathVariable("id") Long targetId) {
        adminAccountService.resetPassword(adminId, targetId);
        adminLogService.log(adminId, "reset_password", "admin_account", targetId, "重置管理员密码");
        return Result.success();
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@RequestAttribute("adminAccountId") Long adminId,
                               @PathVariable("id") Long targetId) {
        adminAccountService.toggleAccount(adminId, targetId);
        adminLogService.log(adminId, "toggle_admin", "admin_account", targetId, "切换管理员状态");
        return Result.success();
    }
}

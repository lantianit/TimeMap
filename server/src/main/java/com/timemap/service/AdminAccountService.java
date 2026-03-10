package com.timemap.service;

import com.timemap.model.dto.*;

import java.util.List;

public interface AdminAccountService {
    AdminLoginResponse login(AdminLoginRequest request, String ip, String userAgent);
    AdminAccountResponse getInfo(Long adminId);
    void changePassword(Long adminId, AdminChangePasswordRequest request, String ip, String userAgent);
    List<AdminAccountResponse> listAccounts(Long adminId);
    AdminAccountResponse createAccount(Long adminId, CreateAdminAccountRequest request);
    void updateAccount(Long adminId, Long targetId, CreateAdminAccountRequest request);
    void resetPassword(Long adminId, Long targetId);
    void toggleAccount(Long adminId, Long targetId);
}

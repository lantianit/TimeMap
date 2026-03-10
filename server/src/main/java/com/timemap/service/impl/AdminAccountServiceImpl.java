package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timemap.mapper.AdminAccountMapper;
import com.timemap.mapper.AdminLoginLogMapper;
import com.timemap.model.dto.*;
import com.timemap.model.entity.AdminAccount;
import com.timemap.model.entity.AdminLoginLog;
import com.timemap.service.AdminAccountService;
import com.timemap.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAccountServiceImpl implements AdminAccountService {

    private final AdminAccountMapper adminAccountMapper;
    private final AdminLoginLogMapper adminLoginLogMapper;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String DEFAULT_PASSWORD = "Admin@2026";
    private static final int MAX_FAIL_COUNT = 5;
    private static final int LOCK_MINUTES = 15;
    private static final int PASSWORD_EXPIRE_DAYS = 90;

    @Override
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request, String ip, String userAgent) {
        AdminAccount account = adminAccountMapper.selectOne(
                new LambdaQueryWrapper<AdminAccount>().eq(AdminAccount::getUsername, request.getUsername()));
        if (account == null) {
            logLogin(0L, "login_fail", ip, userAgent, "账号不存在: " + request.getUsername());
            throw new RuntimeException("账号或密码错误");
        }
        if (account.getIsEnabled() == 0) {
            logLogin(account.getId(), "login_fail", ip, userAgent, "账号已禁用");
            throw new RuntimeException("账号已被禁用");
        }
        if (account.getLockUntil() != null && account.getLockUntil().isAfter(LocalDateTime.now())) {
            logLogin(account.getId(), "login_fail", ip, userAgent, "账号锁定中");
            throw new RuntimeException("账号已锁定，请" + LOCK_MINUTES + "分钟后再试");
        }
        if (!ENCODER.matches(request.getPassword(), account.getPasswordHash())) {
            int failCount = (account.getLoginFailCount() != null ? account.getLoginFailCount() : 0) + 1;
            account.setLoginFailCount(failCount);
            if (failCount >= MAX_FAIL_COUNT) {
                account.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                account.setLoginFailCount(0);
            }
            adminAccountMapper.updateById(account);
            logLogin(account.getId(), "login_fail", ip, userAgent, "密码错误，第" + failCount + "次");
            throw new RuntimeException("账号或密码错误");
        }
        // Login success
        account.setLoginFailCount(0);
        account.setLockUntil(null);
        account.setLastLoginTime(LocalDateTime.now());
        account.setLastLoginIp(ip);
        adminAccountMapper.updateById(account);
        logLogin(account.getId(), "login_success", ip, userAgent, "");

        boolean mustChange = account.getMustChangePassword() != null && account.getMustChangePassword() == 1;
        // Check password expiry
        if (!mustChange && account.getPasswordChangedAt() != null) {
            if (account.getPasswordChangedAt().plusDays(PASSWORD_EXPIRE_DAYS).isBefore(LocalDateTime.now())) {
                mustChange = true;
            }
        }

        boolean rememberMe = request.getRememberMe() != null && request.getRememberMe();
        String token = jwtUtil.generateAdminToken(account.getId(), account.getRole(), rememberMe);

        AdminLoginResponse resp = new AdminLoginResponse();
        resp.setToken(token);
        resp.setRole(account.getRole());
        resp.setNickname(account.getNickname());
        resp.setAdminId(account.getId());
        resp.setMustChangePassword(mustChange);
        return resp;
    }

    @Override
    public AdminAccountResponse getInfo(Long adminId) {
        AdminAccount account = adminAccountMapper.selectById(adminId);
        if (account == null) throw new RuntimeException("管理员不存在");
        return toResponse(account);
    }

    @Override
    @Transactional
    public void changePassword(Long adminId, AdminChangePasswordRequest request, String ip, String userAgent) {
        AdminAccount account = adminAccountMapper.selectById(adminId);
        if (account == null) throw new RuntimeException("管理员不存在");
        if (!ENCODER.matches(request.getOldPassword(), account.getPasswordHash())) {
            throw new RuntimeException("原密码错误");
        }
        validatePasswordComplexity(request.getNewPassword());
        // Check password history
        List<String> history = parseHistory(account.getPasswordHistory());
        for (String h : history) {
            if (ENCODER.matches(request.getNewPassword(), h)) {
                throw new RuntimeException("不能使用最近3次使用过的密码");
            }
        }
        String newHash = ENCODER.encode(request.getNewPassword());
        history.add(newHash);
        if (history.size() > 3) history = history.subList(history.size() - 3, history.size());

        account.setPasswordHash(newHash);
        account.setPasswordHistory(toJson(history));
        account.setPasswordChangedAt(LocalDateTime.now());
        account.setMustChangePassword(0);
        adminAccountMapper.updateById(account);
        logLogin(adminId, "password_change", ip, userAgent, "修改密码");
    }

    @Override
    public List<AdminAccountResponse> listAccounts(Long adminId) {
        requireRole(adminId, "super_admin");
        List<AdminAccount> accounts = adminAccountMapper.selectList(
                new LambdaQueryWrapper<AdminAccount>().orderByDesc(AdminAccount::getCreateTime));
        return accounts.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AdminAccountResponse createAccount(Long adminId, CreateAdminAccountRequest request) {
        requireRole(adminId, "super_admin");
        long exists = adminAccountMapper.selectCount(
                new LambdaQueryWrapper<AdminAccount>().eq(AdminAccount::getUsername, request.getUsername()));
        if (exists > 0) throw new RuntimeException("账号已存在");
        validatePasswordComplexity(request.getPassword());

        AdminAccount account = new AdminAccount();
        account.setUsername(request.getUsername());
        account.setPasswordHash(ENCODER.encode(request.getPassword()));
        account.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        account.setRole(request.getRole() != null ? request.getRole() : "moderator");
        account.setLinkedUserId(request.getLinkedUserId());
        account.setIsEnabled(1);
        account.setMustChangePassword(1);
        account.setLoginFailCount(0);
        account.setPasswordHistory(toJson(List.of(account.getPasswordHash())));
        adminAccountMapper.insert(account);
        return toResponse(account);
    }

    @Override
    @Transactional
    public void updateAccount(Long adminId, Long targetId, CreateAdminAccountRequest request) {
        requireRole(adminId, "super_admin");
        AdminAccount account = adminAccountMapper.selectById(targetId);
        if (account == null) throw new RuntimeException("管理员不存在");
        if (request.getNickname() != null) account.setNickname(request.getNickname());
        if (request.getRole() != null) account.setRole(request.getRole());
        if (request.getLinkedUserId() != null) account.setLinkedUserId(request.getLinkedUserId());
        adminAccountMapper.updateById(account);
    }

    @Override
    @Transactional
    public void resetPassword(Long adminId, Long targetId) {
        requireRole(adminId, "super_admin");
        AdminAccount account = adminAccountMapper.selectById(targetId);
        if (account == null) throw new RuntimeException("管理员不存在");
        account.setPasswordHash(ENCODER.encode(DEFAULT_PASSWORD));
        account.setMustChangePassword(1);
        account.setLoginFailCount(0);
        account.setLockUntil(null);
        adminAccountMapper.updateById(account);
    }

    @Override
    @Transactional
    public void toggleAccount(Long adminId, Long targetId) {
        requireRole(adminId, "super_admin");
        AdminAccount account = adminAccountMapper.selectById(targetId);
        if (account == null) throw new RuntimeException("管理员不存在");
        if (account.getId().equals(adminId)) throw new RuntimeException("不能禁用自己的账号");
        account.setIsEnabled(account.getIsEnabled() == 1 ? 0 : 1);
        adminAccountMapper.updateById(account);
    }

    // ---- helpers ----

    private void requireRole(Long adminId, String... roles) {
        AdminAccount account = adminAccountMapper.selectById(adminId);
        if (account == null) throw new RuntimeException("管理员不存在");
        for (String role : roles) {
            if (role.equals(account.getRole())) return;
        }
        throw new RuntimeException("权限不足");
    }

    private void validatePasswordComplexity(String password) {
        if (password == null || password.length() < 8) {
            throw new RuntimeException("密码长度至少8位");
        }
        if (!password.matches(".*[a-z].*") || !password.matches(".*[A-Z].*") || !password.matches(".*\\d.*")) {
            throw new RuntimeException("密码必须包含大小写字母和数字");
        }
    }

    private void logLogin(Long adminId, String action, String ip, String userAgent, String detail) {
        AdminLoginLog log = new AdminLoginLog();
        log.setAdminAccountId(adminId);
        log.setAction(action);
        log.setIp(ip != null ? ip : "");
        log.setUserAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : (userAgent != null ? userAgent : ""));
        log.setDetail(detail != null ? detail : "");
        adminLoginLogMapper.insert(log);
    }

    private AdminAccountResponse toResponse(AdminAccount a) {
        AdminAccountResponse r = new AdminAccountResponse();
        r.setId(a.getId());
        r.setUsername(a.getUsername());
        r.setNickname(a.getNickname());
        r.setRole(a.getRole());
        r.setLinkedUserId(a.getLinkedUserId());
        r.setIsEnabled(a.getIsEnabled() != null && a.getIsEnabled() == 1);
        r.setLastLoginTime(a.getLastLoginTime() != null ? a.getLastLoginTime().toString() : "");
        r.setLastLoginIp(a.getLastLoginIp());
        r.setCreateTime(a.getCreateTime() != null ? a.getCreateTime().toString() : "");
        return r;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseHistory(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}

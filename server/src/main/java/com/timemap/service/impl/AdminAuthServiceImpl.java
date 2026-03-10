package com.timemap.service.impl;

import com.timemap.config.AdminProperties;
import com.timemap.mapper.AdminAccountMapper;
import com.timemap.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminProperties adminProperties;
    private final AdminAccountMapper adminAccountMapper;

    @Override
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        // Check config-based admin list (miniprogram)
        boolean configAdmin = Arrays.stream(adminProperties.getUserIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equals(String.valueOf(userId)));
        if (configAdmin) return true;
        // Also check if it's a web admin account ID
        return isWebAdmin(userId);
    }

    @Override
    public void requireAdmin(Long userId) {
        if (!isAdmin(userId)) {
            throw new RuntimeException("无管理员权限");
        }
    }

    @Override
    public boolean isWebAdmin(Long adminAccountId) {
        if (adminAccountId == null) return false;
        try {
            return adminAccountMapper.selectById(adminAccountId) != null;
        } catch (Exception e) {
            return false;
        }
    }
}

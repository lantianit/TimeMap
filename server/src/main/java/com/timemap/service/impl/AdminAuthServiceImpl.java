package com.timemap.service.impl;

import com.timemap.config.AdminProperties;
import com.timemap.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminProperties adminProperties;

    @Override
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return Arrays.stream(adminProperties.getUserIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(s -> s.equals(String.valueOf(userId)));
    }

    @Override
    public void requireAdmin(Long userId) {
        if (!isAdmin(userId)) {
            throw new RuntimeException("无管理员权限");
        }
    }
}

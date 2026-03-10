package com.timemap.service;

public interface AdminAuthService {
    boolean isAdmin(Long userId);
    void requireAdmin(Long userId);
}

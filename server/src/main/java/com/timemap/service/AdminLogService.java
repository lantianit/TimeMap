package com.timemap.service;

import com.timemap.model.dto.AdminLogPageResponse;

public interface AdminLogService {
    void log(Long adminUserId, String action, String targetType, Long targetId, String detail);
    AdminLogPageResponse getLogs(Long adminUserId, int page, int size);
}

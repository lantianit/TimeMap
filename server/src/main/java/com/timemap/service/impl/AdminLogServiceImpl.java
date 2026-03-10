package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.timemap.mapper.AdminLogMapper;
import com.timemap.mapper.UserMapper;
import com.timemap.model.dto.AdminLogPageResponse;
import com.timemap.model.dto.AdminLogResponse;
import com.timemap.model.entity.AdminLog;
import com.timemap.model.entity.User;
import com.timemap.service.AdminLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminLogServiceImpl implements AdminLogService {

    private final AdminLogMapper adminLogMapper;
    private final UserMapper userMapper;

    @Override
    public void log(Long adminUserId, String action, String targetType, Long targetId, String detail) {
        AdminLog log = new AdminLog();
        log.setAdminUserId(adminUserId);
        log.setAction(action);
        log.setTargetType(targetType != null ? targetType : "");
        log.setTargetId(targetId);
        log.setDetail(detail != null && detail.length() > 2000 ? detail.substring(0, 2000) : detail);
        adminLogMapper.insert(log);
    }

    @Override
    public AdminLogPageResponse getLogs(Long adminUserId, int page, int size) {
        Page<AdminLog> p = new Page<>(page, size);
        LambdaQueryWrapper<AdminLog> qw = new LambdaQueryWrapper<AdminLog>()
                .orderByDesc(AdminLog::getCreateTime);
        adminLogMapper.selectPage(p, qw);

        AdminLogPageResponse response = new AdminLogPageResponse();
        response.setList(p.getRecords().stream().map(this::toResponse).collect(Collectors.toList()));
        response.setTotal(p.getTotal());
        response.setHasMore((long) page * size < p.getTotal());
        return response;
    }

    private AdminLogResponse toResponse(AdminLog log) {
        AdminLogResponse r = new AdminLogResponse();
        r.setId(log.getId());
        r.setAdminUserId(log.getAdminUserId());
        r.setAction(log.getAction());
        r.setTargetType(log.getTargetType());
        r.setTargetId(log.getTargetId());
        r.setDetail(log.getDetail());
        r.setCreateTime(log.getCreateTime() != null ? log.getCreateTime().toString() : "");
        User admin = userMapper.selectById(log.getAdminUserId());
        if (admin != null) {
            r.setAdminNickname(admin.getNickname());
        }
        return r;
    }
}

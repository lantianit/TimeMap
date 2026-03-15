package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.timemap.mapper.AppealMapper;
import com.timemap.mapper.ReportMapper;
import com.timemap.mapper.UserMapper;
import com.timemap.mapper.PhotoMapper;
import com.timemap.mapper.CommentMapper;
import com.timemap.mapper.CosDeleteRecordMapper;
import com.timemap.model.dto.*;
import com.timemap.model.vo.*;
import com.timemap.model.entity.*;
import com.timemap.service.AdminAuthService;
import com.timemap.service.AdminLogService;
import com.timemap.service.AppealService;
import com.timemap.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppealServiceImpl implements AppealService {

    private final AppealMapper appealMapper;
    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final PhotoMapper photoMapper;
    private final CommentMapper commentMapper;
    private final CosDeleteRecordMapper cosDeleteRecordMapper;
    private final AdminAuthService adminAuthService;
    private final NotificationService notificationService;
    private final AdminLogService adminLogService;

    @Override
    @Transactional
    public void submitAppeal(Long userId, AppealSubmitRequest request) {
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new RuntimeException("申诉原因不能为空");
        }
        if (request.getReportId() == null) {
            throw new RuntimeException("关联举报ID不能为空");
        }

        Report report = reportMapper.selectById(request.getReportId());
        if (report == null) {
            throw new RuntimeException("关联举报不存在");
        }

        String type = request.getType();
        if ("content_removed".equals(type)) {
            if (!report.getStatus().equals(1)) {
                throw new RuntimeException("该举报尚未被采纳，无法申诉");
            }
        } else if ("report_rejected".equals(type)) {
            if (!report.getUserId().equals(userId)) {
                throw new RuntimeException("只能对自己的举报发起申诉");
            }
            if (!report.getStatus().equals(2)) {
                throw new RuntimeException("该举报尚未被驳回，无法申诉");
            }
        } else {
            throw new RuntimeException("不支持的申诉类型");
        }

        long existing = appealMapper.selectCount(new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getUserId, userId)
                .eq(Appeal::getReportId, request.getReportId())
                .eq(Appeal::getType, type)
                .eq(Appeal::getStatus, 0));
        if (existing > 0) {
            throw new RuntimeException("已提交过申诉，请等待处理");
        }

        Appeal appeal = new Appeal();
        appeal.setUserId(userId);
        appeal.setType(type);
        appeal.setReportId(request.getReportId());
        appeal.setReason(request.getReason().trim().length() > 1000
                ? request.getReason().trim().substring(0, 1000)
                : request.getReason().trim());
        appeal.setStatus(0);
        appealMapper.insert(appeal);
    }

    @Override
    public AppealPageVO getMyAppeals(Long userId, int page, int size) {
        Page<Appeal> p = new Page<>(page, size);
        LambdaQueryWrapper<Appeal> qw = new LambdaQueryWrapper<Appeal>()
                .eq(Appeal::getUserId, userId)
                .orderByDesc(Appeal::getCreateTime);
        appealMapper.selectPage(p, qw);

        AppealPageVO response = new AppealPageVO();
        response.setList(p.getRecords().stream().map(this::toResponse).collect(Collectors.toList()));
        response.setTotal(p.getTotal());
        response.setHasMore((long) page * size < p.getTotal());
        return response;
    }

    @Override
    public AppealPageVO getAdminAppeals(Long adminUserId, Integer status, int page, int size) {
        adminAuthService.requireAdmin(adminUserId);

        Page<Appeal> p = new Page<>(page, size);
        LambdaQueryWrapper<Appeal> qw = new LambdaQueryWrapper<Appeal>()
                .orderByAsc(Appeal::getStatus)
                .orderByDesc(Appeal::getCreateTime);
        if (status != null) {
            qw.eq(Appeal::getStatus, status);
        }
        appealMapper.selectPage(p, qw);

        AppealPageVO response = new AppealPageVO();
        response.setList(p.getRecords().stream().map(this::toResponse).collect(Collectors.toList()));
        response.setTotal(p.getTotal());
        response.setHasMore((long) page * size < p.getTotal());
        return response;
    }

    @Override
    public AppealVO getAppealDetail(Long adminUserId, Long appealId) {
        adminAuthService.requireAdmin(adminUserId);
        Appeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new RuntimeException("申诉记录不存在");
        }
        return toResponse(appeal);
    }

    @Override
    @Transactional
    public void resolveAppeal(Long adminUserId, HandleAppealRequest request) {
        adminAuthService.requireAdmin(adminUserId);
        Appeal appeal = getPendingAppeal(request.getAppealId());

        appeal.setStatus(1);
        appeal.setHandleResult(request.getHandleResult() != null ? request.getHandleResult().trim() : "申诉已采纳");
        appeal.setHandledBy(adminUserId);
        appeal.setHandledTime(LocalDateTime.now());
        appealMapper.updateById(appeal);

        // 如果是内容移除类申诉，尝试恢复被删除的内容
        if ("content_removed".equals(appeal.getType())) {
            restoreContent(appeal);
        }

        notificationService.createNotification(appeal.getUserId(), adminUserId,
                "appeal_result", null, null,
                "你的申诉已被采纳：" + appeal.getHandleResult());

        adminLogService.log(adminUserId, "resolve_appeal", "appeal", appeal.getId(),
                "采纳申诉，原因：" + appeal.getHandleResult());
    }

    @Override
    @Transactional
    public void rejectAppeal(Long adminUserId, HandleAppealRequest request) {
        adminAuthService.requireAdmin(adminUserId);
        Appeal appeal = getPendingAppeal(request.getAppealId());

        if (request.getHandleResult() == null || request.getHandleResult().trim().isEmpty()) {
            throw new RuntimeException("驳回原因不能为空");
        }

        appeal.setStatus(2);
        appeal.setHandleResult(request.getHandleResult().trim());
        appeal.setHandledBy(adminUserId);
        appeal.setHandledTime(LocalDateTime.now());
        appealMapper.updateById(appeal);

        notificationService.createNotification(appeal.getUserId(), adminUserId,
                "appeal_result", null, null,
                "你的申诉未通过：" + appeal.getHandleResult());

        adminLogService.log(adminUserId, "reject_appeal", "appeal", appeal.getId(),
                "驳回申诉，原因：" + appeal.getHandleResult());
    }

    @Override
    public long getPendingCount() {
        return appealMapper.selectCount(new LambdaQueryWrapper<Appeal>().eq(Appeal::getStatus, 0));
    }

    private Appeal getPendingAppeal(Long appealId) {
        if (appealId == null) {
            throw new RuntimeException("申诉ID不能为空");
        }
        Appeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new RuntimeException("申诉记录不存在");
        }
        if (appeal.getStatus() != 0) {
            throw new RuntimeException("该申诉已处理");
        }
        return appeal;
    }

    /**
     * 恢复被举报删除的内容（照片或评论）
     * 仅在 COS 文件尚未被物理删除（30天保留期内）时可恢复
     */
    private void restoreContent(Appeal appeal) {
        Report report = reportMapper.selectById(appeal.getReportId());
        if (report == null) return;

        String targetType = report.getTargetType();
        Long targetId = report.getTargetId();

        if ("photo".equals(targetType)) {
            // 检查 COS 文件是否已被物理删除
            List<CosDeleteRecord> cosRecords = cosDeleteRecordMapper.selectList(
                    new LambdaQueryWrapper<CosDeleteRecord>()
                            .eq(CosDeleteRecord::getContentType, "photo")
                            .eq(CosDeleteRecord::getContentId, targetId)
                            .eq(CosDeleteRecord::getIsDeleted, 1));
            if (!cosRecords.isEmpty()) {
                throw new RuntimeException("内容已过保留期（COS 文件已清理），无法恢复");
            }

            // 恢复照片：直接更新 deleted=0（绕过 MyBatis-Plus 逻辑删除过滤）
            photoMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Photo>()
                    .eq(Photo::getId, targetId)
                    .set(Photo::getDeleted, 0));

            // 取消 COS 延迟删除记录
            cosDeleteRecordMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CosDeleteRecord>()
                    .eq(CosDeleteRecord::getContentType, "photo")
                    .eq(CosDeleteRecord::getContentId, targetId)
                    .eq(CosDeleteRecord::getIsDeleted, 0)
                    .set(CosDeleteRecord::getIsDeleted, 2)); // 2 = 已取消

        } else if ("comment".equals(targetType)) {
            // 恢复评论
            commentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, targetId)
                    .set(Comment::getDeleted, 0));
        }
    }

    private AppealVO toResponse(Appeal appeal) {
        AppealVO r = new AppealVO();
        r.setId(appeal.getId());
        r.setUserId(appeal.getUserId());
        r.setType(appeal.getType());
        r.setReportId(appeal.getReportId());
        r.setReason(appeal.getReason());
        r.setStatus(appeal.getStatus());
        r.setHandleResult(appeal.getHandleResult());
        r.setCreateTime(appeal.getCreateTime() != null ? appeal.getCreateTime().toString() : "");
        r.setHandledTime(appeal.getHandledTime() != null ? appeal.getHandledTime().toString() : "");
        r.setHandledBy(appeal.getHandledBy());

        User user = userMapper.selectById(appeal.getUserId());
        if (user != null) {
            r.setUserNickname(user.getNickname());
            r.setUserAvatarUrl(user.getAvatarUrl());
        }

        if (appeal.getHandledBy() != null) {
            User handler = userMapper.selectById(appeal.getHandledBy());
            if (handler != null) {
                r.setHandledByNickname(handler.getNickname());
            }
        }

        if (appeal.getReportId() != null) {
            Report report = reportMapper.selectById(appeal.getReportId());
            if (report != null) {
                r.setReportReason(report.getReason());
                r.setReportTargetType(report.getTargetType());
            }
        }

        return r;
    }
}

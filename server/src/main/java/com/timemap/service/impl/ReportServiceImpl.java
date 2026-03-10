package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.timemap.mapper.*;
import com.timemap.model.dto.*;
import com.timemap.model.entity.*;
import com.timemap.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final String TARGET_TYPE_PHOTO = "photo";
    private static final String TARGET_TYPE_COMMENT = "comment";
    private static final String TARGET_TYPE_MESSAGE = "message";
    private static final String ACTION_REMOVE_CONTENT = "REMOVE_CONTENT";
    private static final int RATE_LIMIT_PER_HOUR = 10;

    private final ReportMapper reportMapper;
    private final PhotoMapper photoMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final MessageMapper messageMapper;
    private final UserViolationMapper userViolationMapper;
    private final CommentService commentService;
    private final NotificationService notificationService;
    private final AdminAuthService adminAuthService;
    private final AdminLogService adminLogService;
    private final CosService cosService;

    // ==================== 7.1 举报频率限制 ====================

    private void checkRateLimit(Long userId) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentCount = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getUserId, userId)
                .ge(Report::getCreateTime, oneHourAgo));
        if (recentCount >= RATE_LIMIT_PER_HOUR) {
            throw new RuntimeException("举报过于频繁，请稍后再试（每小时最多" + RATE_LIMIT_PER_HOUR + "条）");
        }
    }

    // ==================== 7.2 恶意举报检测 ====================

    private void checkMaliciousReporter(Long userId) {
        long totalRejected = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getUserId, userId)
                .eq(Report::getStatus, 2));
        if (totalRejected >= 20) {
            throw new RuntimeException("你的举报权限已受限，因多次举报未通过审核");
        }
    }

    // ==================== 1.5 防重复举报 + 1.7 消息举报 + 7.x 安全 ====================

    @Override
    @Transactional
    public ReportSubmitResponse submitReport(String targetType, Long targetId, String reason, String description, Long userId) {
        if (targetId == null) {
            throw new RuntimeException("举报对象不能为空");
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new RuntimeException("举报原因不能为空");
        }

        checkRateLimit(userId);
        checkMaliciousReporter(userId);

        TargetInfo targetInfo = loadTargetInfo(normalizeTargetType(targetType), targetId);
        if (targetInfo.ownerUserId != null && targetInfo.ownerUserId.equals(userId)) {
            throw new RuntimeException("不能举报自己发布的内容");
        }

        // 1.5: 检查 status=0（待处理）和 status=1（已采纳，内容已删）
        long existingActive = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getUserId, userId)
                .eq(Report::getTargetType, targetInfo.targetType)
                .eq(Report::getTargetId, targetId)
                .in(Report::getStatus, 0, 1));
        if (existingActive > 0) {
            throw new RuntimeException("该内容已被举报处理或正在处理中");
        }

        Report report = new Report();
        report.setUserId(userId);
        report.setTargetType(targetInfo.targetType);
        report.setTargetId(targetId);
        report.setReason(reason.trim());
        report.setDescription(limit(description, 500));
        report.setStatus(0);
        reportMapper.insert(report);

        ReportSubmitResponse response = new ReportSubmitResponse();
        response.setReportId(report.getId());
        response.setStatus(report.getStatus());
        return response;
    }

    // ==================== 2.3 举报列表含缩略图 ====================

    @Override
    public MyReportPageResponse getMyReports(Long userId, int page, int size) {
        Page<Report> reportPage = new Page<>(page, size);
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<Report>()
                .eq(Report::getUserId, userId)
                .orderByDesc(Report::getCreateTime);
        reportMapper.selectPage(reportPage, queryWrapper);

        MyReportPageResponse response = new MyReportPageResponse();
        response.setList(reportPage.getRecords().stream().map(this::toMyReportItem).collect(Collectors.toList()));
        response.setTotal(reportPage.getTotal());
        response.setHasMore((long) page * size < reportPage.getTotal());
        return response;
    }

    @Override
    public AdminReportPageResponse getAdminReports(Long adminUserId, Integer status, String targetType, int page, int size) {
        adminAuthService.requireAdmin(adminUserId);

        Page<Report> reportPage = new Page<>(page, size);
        LambdaQueryWrapper<Report> queryWrapper = new LambdaQueryWrapper<Report>()
                .orderByAsc(Report::getStatus)
                .orderByDesc(Report::getCreateTime);
        if (status != null) {
            queryWrapper.eq(Report::getStatus, status);
        }
        if (targetType != null && !targetType.isBlank()) {
            queryWrapper.eq(Report::getTargetType, normalizeTargetType(targetType));
        }
        reportMapper.selectPage(reportPage, queryWrapper);

        AdminReportPageResponse response = new AdminReportPageResponse();
        response.setList(reportPage.getRecords().stream().map(this::toAdminListItem).collect(Collectors.toList()));
        response.setTotal(reportPage.getTotal());
        response.setHasMore((long) page * size < reportPage.getTotal());
        return response;
    }

    @Override
    public AdminReportDetailResponse getAdminReportDetail(Long adminUserId, Long reportId) {
        adminAuthService.requireAdmin(adminUserId);
        Report report = getReport(reportId);
        return toAdminDetail(report);
    }

    // ==================== 3.7 通知作者 + 4.3 关联举报自动关闭 + 4.4 COS清理 + 5.x 处罚 ====================

    @Override
    @Transactional
    public void resolveReport(Long adminUserId, ResolveReportRequest request) {
        adminAuthService.requireAdmin(adminUserId);
        if (request == null || request.getReportId() == null) {
            throw new RuntimeException("举报ID不能为空");
        }
        if (request.getHandleResult() == null || request.getHandleResult().trim().isEmpty()) {
            throw new RuntimeException("处理结果不能为空");
        }
        String action = request.getAction();
        if (action == null || action.isBlank()) {
            action = ACTION_REMOVE_CONTENT;
        }
        if (!ACTION_REMOVE_CONTENT.equals(action)) {
            throw new RuntimeException("暂不支持的处理动作");
        }

        Report report = getPendingReport(request.getReportId());
        Long contentOwnerUserId = executeContentAction(report);

        report.setStatus(1);
        report.setHandleResult(limit(request.getHandleResult(), 500));
        report.setHandledBy(adminUserId);
        report.setHandledTime(LocalDateTime.now());
        reportMapper.updateById(report);

        // 3.7: 通知举报人
        sendReportResultNotification(report, adminUserId, true);

        // 3.7: 通知内容作者
        if (contentOwnerUserId != null) {
            sendContentRemovedNotification(report, contentOwnerUserId, adminUserId);
        }

        // 4.3: 自动关闭同一内容的其他待处理举报
        autoCloseRelatedReports(report, adminUserId);

        // 记录违规
        if (contentOwnerUserId != null) {
            recordViolation(contentOwnerUserId, report, "content_removed",
                    report.getReason(), null, 0, adminUserId);
        }

        // 5.x: 处罚用户
        if (contentOwnerUserId != null && request.getPunishmentType() != null
                && !request.getPunishmentType().isBlank()) {
            applyPunishment(contentOwnerUserId, report.getId(), request.getPunishmentType(),
                    request.getPunishmentDays() != null ? request.getPunishmentDays() : 0,
                    report.getReason(), adminUserId);
        }

        // 7.3: 操作日志
        adminLogService.log(adminUserId, "resolve_report", "report", report.getId(),
                "采纳举报，类型=" + report.getTargetType() + "，目标=" + report.getTargetId()
                        + "，结果=" + report.getHandleResult());
    }

    @Override
    @Transactional
    public void rejectReport(Long adminUserId, RejectReportRequest request) {
        adminAuthService.requireAdmin(adminUserId);
        if (request == null || request.getReportId() == null) {
            throw new RuntimeException("举报ID不能为空");
        }
        if (request.getHandleResult() == null || request.getHandleResult().trim().isEmpty()) {
            throw new RuntimeException("驳回原因不能为空");
        }

        Report report = getPendingReport(request.getReportId());
        report.setStatus(2);
        report.setHandleResult(limit(request.getHandleResult(), 500));
        report.setHandledBy(adminUserId);
        report.setHandledTime(LocalDateTime.now());
        reportMapper.updateById(report);

        sendReportResultNotification(report, adminUserId, false);

        adminLogService.log(adminUserId, "reject_report", "report", report.getId(),
                "驳回举报，原因=" + report.getHandleResult());
    }

    // ==================== 3.10 批量处理 ====================

    @Override
    @Transactional
    public void batchResolve(Long adminUserId, BatchReportActionRequest request) {
        adminAuthService.requireAdmin(adminUserId);
        if (request.getReportIds() == null || request.getReportIds().isEmpty()) {
            throw new RuntimeException("请选择要处理的举报");
        }
        if (request.getHandleResult() == null || request.getHandleResult().trim().isEmpty()) {
            throw new RuntimeException("处理结果不能为空");
        }
        for (Long reportId : request.getReportIds()) {
            try {
                ResolveReportRequest rr = new ResolveReportRequest();
                rr.setReportId(reportId);
                rr.setAction(ACTION_REMOVE_CONTENT);
                rr.setHandleResult(request.getHandleResult());
                resolveReport(adminUserId, rr);
            } catch (RuntimeException ignored) {
                // 跳过已处理的举报
            }
        }
    }

    @Override
    @Transactional
    public void batchReject(Long adminUserId, BatchReportActionRequest request) {
        adminAuthService.requireAdmin(adminUserId);
        if (request.getReportIds() == null || request.getReportIds().isEmpty()) {
            throw new RuntimeException("请选择要处理的举报");
        }
        if (request.getHandleResult() == null || request.getHandleResult().trim().isEmpty()) {
            throw new RuntimeException("驳回原因不能为空");
        }
        for (Long reportId : request.getReportIds()) {
            try {
                RejectReportRequest rr = new RejectReportRequest();
                rr.setReportId(reportId);
                rr.setHandleResult(request.getHandleResult());
                rejectReport(adminUserId, rr);
            } catch (RuntimeException ignored) {
            }
        }
    }

    // ==================== 3.9 待处理数量 ====================

    @Override
    public PendingReportCountResponse getPendingCount(Long adminUserId) {
        adminAuthService.requireAdmin(adminUserId);
        PendingReportCountResponse r = new PendingReportCountResponse();
        r.setReportCount(reportMapper.selectCount(
                new LambdaQueryWrapper<Report>().eq(Report::getStatus, 0)));
        return r;
    }

    // ==================== 3.8 举报聚合 ====================

    @Override
    public List<AggregatedReportResponse> getAggregatedReports(Long adminUserId, int page, int size) {
        adminAuthService.requireAdmin(adminUserId);

        List<Report> pendingReports = reportMapper.selectList(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getStatus, 0)
                        .orderByDesc(Report::getCreateTime));

        Map<String, List<Report>> grouped = pendingReports.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getTargetType() + ":" + r.getTargetId(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<AggregatedReportResponse> result = new ArrayList<>();
        for (Map.Entry<String, List<Report>> entry : grouped.entrySet()) {
            List<Report> reports = entry.getValue();
            Report first = reports.get(0);

            AggregatedReportResponse agg = new AggregatedReportResponse();
            agg.setTargetType(first.getTargetType());
            agg.setTargetId(first.getTargetId());
            agg.setReportCount(reports.size());
            agg.setReasons(reports.stream().map(Report::getReason).distinct().collect(Collectors.toList()));
            agg.setReportIds(reports.stream().map(Report::getId).collect(Collectors.toList()));
            agg.setEarliestTime(toTime(reports.stream()
                    .map(Report::getCreateTime).filter(Objects::nonNull)
                    .min(Comparator.naturalOrder()).orElse(null)));
            agg.setLatestTime(toTime(reports.stream()
                    .map(Report::getCreateTime).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null)));

            try {
                TargetInfo info = loadTargetInfo(first.getTargetType(), first.getTargetId());
                agg.setTargetPreview(info.preview);
                agg.setTargetImageUrl(info.imageUrl);
                agg.setTargetOwnerUserId(info.ownerUserId);
                if (info.ownerUserId != null) {
                    User owner = userMapper.selectById(info.ownerUserId);
                    if (owner != null) agg.setTargetOwnerNickname(owner.getNickname());
                }
            } catch (RuntimeException e) {
                agg.setTargetPreview("内容已删除");
            }

            result.add(agg);
        }

        int fromIndex = Math.min((page - 1) * size, result.size());
        int toIndex = Math.min(fromIndex + size, result.size());
        return result.subList(fromIndex, toIndex);
    }

    // ==================== 5.x 用户处罚 ====================

    @Override
    @Transactional
    public void punishUser(Long adminUserId, PunishUserRequest request) {
        adminAuthService.requireAdmin(adminUserId);
        if (request.getUserId() == null) throw new RuntimeException("用户ID不能为空");
        if (request.getPunishmentType() == null || request.getPunishmentType().isBlank()) {
            throw new RuntimeException("处罚类型不能为空");
        }

        applyPunishment(request.getUserId(), request.getReportId(), request.getPunishmentType(),
                request.getPunishmentDays() != null ? request.getPunishmentDays() : 0,
                request.getReason() != null ? request.getReason() : "", adminUserId);

        adminLogService.log(adminUserId, "punish_user", "user", request.getUserId(),
                "处罚用户，类型=" + request.getPunishmentType()
                        + "，天数=" + request.getPunishmentDays()
                        + "，原因=" + request.getReason());
    }

    @Override
    public UserViolationPageResponse getUserViolations(Long adminUserId, Long userId, int page, int size) {
        adminAuthService.requireAdmin(adminUserId);
        return queryViolations(userId, page, size);
    }

    @Override
    public UserViolationPageResponse getMyViolations(Long userId, int page, int size) {
        return queryViolations(userId, page, size);
    }

    // ==================== Internal helpers ====================

    private Long executeContentAction(Report report) {
        if (TARGET_TYPE_PHOTO.equals(report.getTargetType())) {
            Photo photo = photoMapper.selectById(report.getTargetId());
            if (photo != null) {
                // 4.4: COS 文件清理
                cosService.deleteByUrl(photo.getImageUrl());
                if (photo.getThumbnailUrl() != null
                        && !photo.getThumbnailUrl().equals(photo.getImageUrl())) {
                    cosService.deleteByUrl(photo.getThumbnailUrl());
                }
                photoMapper.deleteById(photo.getId());
                return photo.getUserId();
            }
            return null;
        }

        if (TARGET_TYPE_COMMENT.equals(report.getTargetType())) {
            Comment comment = commentMapper.selectById(report.getTargetId());
            if (comment != null) {
                Long ownerUserId = comment.getUserId();
                commentService.deleteCommentByAdmin(comment.getId());
                return ownerUserId;
            }
            return null;
        }

        if (TARGET_TYPE_MESSAGE.equals(report.getTargetType())) {
            Message message = messageMapper.selectById(report.getTargetId());
            if (message != null) {
                Long ownerUserId = message.getFromUserId();
                messageMapper.deleteById(message.getId());
                return ownerUserId;
            }
            return null;
        }

        throw new RuntimeException("不支持的举报对象类型");
    }

    private void autoCloseRelatedReports(Report resolved, Long adminUserId) {
        List<Report> related = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetType, resolved.getTargetType())
                .eq(Report::getTargetId, resolved.getTargetId())
                .eq(Report::getStatus, 0)
                .ne(Report::getId, resolved.getId()));

        for (Report r : related) {
            r.setStatus(1);
            r.setHandleResult("同一内容已被其他举报处理");
            r.setHandledBy(adminUserId);
            r.setHandledTime(LocalDateTime.now());
            reportMapper.updateById(r);
            sendReportResultNotification(r, adminUserId, true);
        }
    }

    private void sendReportResultNotification(Report report, Long adminUserId, boolean resolved) {
        String message = resolved
                ? "你举报的内容经核查已处理：" + report.getHandleResult()
                : "你举报的内容经核查暂不处理：" + report.getHandleResult();
        Long photoId = TARGET_TYPE_PHOTO.equals(report.getTargetType()) ? report.getTargetId() : null;
        Long commentId = TARGET_TYPE_COMMENT.equals(report.getTargetType()) ? report.getTargetId() : null;
        notificationService.createNotification(report.getUserId(), adminUserId, "report_result", photoId, commentId, message);
    }

    private void sendContentRemovedNotification(Report report, Long contentOwnerUserId, Long adminUserId) {
        String targetLabel;
        if (TARGET_TYPE_PHOTO.equals(report.getTargetType())) {
            targetLabel = "照片";
        } else if (TARGET_TYPE_COMMENT.equals(report.getTargetType())) {
            targetLabel = "评论";
        } else if (TARGET_TYPE_MESSAGE.equals(report.getTargetType())) {
            targetLabel = "消息";
        } else {
            targetLabel = "内容";
        }
        String message = "你发布的" + targetLabel + "因[" + report.getReason() + "]已被移除。如有异议可在我的违规中申诉。";
        notificationService.createNotification(contentOwnerUserId, adminUserId,
                "content_removed", null, null, message);
    }

    private void recordViolation(Long userId, Report report, String violationType,
                                 String reason, String punishmentType, int punishmentDays,
                                 Long adminUserId) {
        UserViolation v = new UserViolation();
        v.setUserId(userId);
        v.setReportId(report.getId());
        v.setViolationType(violationType);
        v.setReason(reason);
        v.setTargetType(report.getTargetType());
        v.setTargetId(report.getTargetId());
        v.setPunishmentType(punishmentType != null ? punishmentType : "");
        v.setPunishmentDays(punishmentDays);
        v.setHandledBy(adminUserId);
        userViolationMapper.insert(v);

        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setViolationCount((user.getViolationCount() != null ? user.getViolationCount() : 0) + 1);
            userMapper.updateById(user);
        }
    }

    private void applyPunishment(Long userId, Long reportId, String punishmentType,
                                 int days, String reason, Long adminUserId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");

        switch (punishmentType) {
            case "warning" -> {
                notificationService.createNotification(userId, adminUserId,
                        "warning", null, null,
                        "你因「" + reason + "」收到一次警告。请遵守社区规范，多次违规将受到更严厉处罚。");
            }
            case "mute" -> {
                int d = days > 0 ? days : 7;
                user.setMuteUntil(LocalDateTime.now().plusDays(d));
                userMapper.updateById(user);
                notificationService.createNotification(userId, adminUserId,
                        "punishment", null, null,
                        "你因「" + reason + "」被禁言" + d + "天，禁言期间无法评论和发送私信。");
            }
            case "ban_upload" -> {
                int d = days > 0 ? days : 30;
                user.setBanUploadUntil(LocalDateTime.now().plusDays(d));
                userMapper.updateById(user);
                notificationService.createNotification(userId, adminUserId,
                        "punishment", null, null,
                        "你因「" + reason + "」被禁止上传照片" + d + "天。");
            }
            case "ban_account" -> {
                user.setIsBanned(1);
                userMapper.updateById(user);
                notificationService.createNotification(userId, adminUserId,
                        "punishment", null, null,
                        "你的账号因严重违规已被封禁。");
            }
            default -> throw new RuntimeException("不支持的处罚类型：" + punishmentType);
        }

        if (reportId != null) {
            UserViolation v = new UserViolation();
            v.setUserId(userId);
            v.setReportId(reportId);
            v.setViolationType(punishmentType);
            v.setReason(reason);
            v.setPunishmentType(punishmentType);
            v.setPunishmentDays(days);
            v.setHandledBy(adminUserId);
            userViolationMapper.insert(v);
        }

        adminLogService.log(adminUserId, "punish_user", "user", userId,
                "处罚类型=" + punishmentType + "，天数=" + days + "，原因=" + reason);
    }

    private UserViolationPageResponse queryViolations(Long userId, int page, int size) {
        Page<UserViolation> p = new Page<>(page, size);
        LambdaQueryWrapper<UserViolation> qw = new LambdaQueryWrapper<UserViolation>()
                .eq(UserViolation::getUserId, userId)
                .orderByDesc(UserViolation::getCreateTime);
        userViolationMapper.selectPage(p, qw);

        UserViolationPageResponse resp = new UserViolationPageResponse();
        resp.setList(p.getRecords().stream().map(this::toViolationResponse).collect(Collectors.toList()));
        resp.setTotal(p.getTotal());
        resp.setHasMore((long) page * size < p.getTotal());
        return resp;
    }

    private UserViolationResponse toViolationResponse(UserViolation v) {
        UserViolationResponse r = new UserViolationResponse();
        r.setId(v.getId());
        r.setUserId(v.getUserId());
        r.setReportId(v.getReportId());
        r.setViolationType(v.getViolationType());
        r.setReason(v.getReason());
        r.setTargetType(v.getTargetType());
        r.setTargetId(v.getTargetId());
        r.setPunishmentType(v.getPunishmentType());
        r.setPunishmentDays(v.getPunishmentDays());
        r.setCreateTime(v.getCreateTime() != null ? v.getCreateTime().toString() : "");

        User u = userMapper.selectById(v.getUserId());
        if (u != null) r.setUserNickname(u.getNickname());

        // 判断是否可以申诉（content_removed 类型的违规可申诉）
        r.setCanAppeal("content_removed".equals(v.getViolationType()) && v.getReportId() != null);
        return r;
    }

    private Report getPendingReport(Long reportId) {
        Report report = getReport(reportId);
        if (!Objects.equals(report.getStatus(), 0)) {
            throw new RuntimeException("该举报已处理，请勿重复操作");
        }
        return report;
    }

    private Report getReport(Long reportId) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new RuntimeException("举报记录不存在");
        }
        return report;
    }

    private String normalizeTargetType(String targetType) {
        if (TARGET_TYPE_PHOTO.equals(targetType) || TARGET_TYPE_COMMENT.equals(targetType)
                || TARGET_TYPE_MESSAGE.equals(targetType)) {
            return targetType;
        }
        throw new RuntimeException("不支持的举报对象类型");
    }

    private TargetInfo loadTargetInfo(String targetType, Long targetId) {
        if (TARGET_TYPE_PHOTO.equals(targetType)) {
            Photo photo = photoMapper.selectById(targetId);
            if (photo == null) {
                throw new RuntimeException("照片不存在");
            }
            return new TargetInfo(targetType, photo.getUserId(), photo.getLocationName(),
                    photo.getImageUrl(), photo.getId(), null);
        }

        if (TARGET_TYPE_COMMENT.equals(targetType)) {
            Comment comment = commentMapper.selectById(targetId);
            if (comment == null) {
                throw new RuntimeException("评论不存在");
            }
            return new TargetInfo(targetType, comment.getUserId(), comment.getContent(),
                    null, comment.getPhotoId(), comment.getId());
        }

        if (TARGET_TYPE_MESSAGE.equals(targetType)) {
            Message message = messageMapper.selectById(targetId);
            if (message == null) {
                throw new RuntimeException("消息不存在");
            }
            return new TargetInfo(targetType, message.getFromUserId(), message.getContent(),
                    null, null, null);
        }

        throw new RuntimeException("不支持的举报对象类型");
    }

    private MyReportItemResponse toMyReportItem(Report report) {
        MyReportItemResponse item = new MyReportItemResponse();
        item.setId(report.getId());
        item.setTargetType(report.getTargetType());
        item.setTargetId(report.getTargetId());
        item.setReason(report.getReason());
        item.setStatus(report.getStatus());
        item.setHandleResult(report.getHandleResult());
        item.setCreateTime(toTime(report.getCreateTime()));
        item.setHandledTime(toTime(report.getHandledTime()));

        try {
            TargetInfo info = loadTargetInfo(report.getTargetType(), report.getTargetId());
            item.setTargetPreview(info.preview);
            item.setTargetImageUrl(info.imageUrl);
        } catch (RuntimeException e) {
            item.setTargetPreview("内容已删除");
        }

        return item;
    }

    private AdminReportListItemResponse toAdminListItem(Report report) {
        AdminReportListItemResponse item = new AdminReportListItemResponse();
        item.setId(report.getId());
        item.setTargetType(report.getTargetType());
        item.setTargetId(report.getTargetId());
        item.setReason(report.getReason());
        item.setStatus(report.getStatus());
        item.setCreateTime(toTime(report.getCreateTime()));

        User reporter = userMapper.selectById(report.getUserId());
        if (reporter != null) {
            item.setReporterUserId(reporter.getId());
            item.setReporterNickname(reporter.getNickname());
        }

        try {
            TargetInfo info = loadTargetInfo(report.getTargetType(), report.getTargetId());
            item.setTargetPreview(info.preview);
            item.setTargetImageUrl(info.imageUrl);
        } catch (RuntimeException e) {
            item.setTargetPreview("内容已删除");
        }

        return item;
    }

    private AdminReportDetailResponse toAdminDetail(Report report) {
        AdminReportDetailResponse response = new AdminReportDetailResponse();
        response.setId(report.getId());
        response.setTargetType(report.getTargetType());
        response.setTargetId(report.getTargetId());
        response.setReason(report.getReason());
        response.setDescription(report.getDescription());
        response.setStatus(report.getStatus());
        response.setHandleResult(report.getHandleResult());
        response.setCreateTime(toTime(report.getCreateTime()));
        response.setHandledTime(toTime(report.getHandledTime()));
        response.setHandledBy(report.getHandledBy());

        User reporter = userMapper.selectById(report.getUserId());
        if (reporter != null) {
            response.setReporterUserId(reporter.getId());
            response.setReporterNickname(reporter.getNickname());
            response.setReporterAvatarUrl(reporter.getAvatarUrl());
        }

        if (report.getHandledBy() != null) {
            User handler = userMapper.selectById(report.getHandledBy());
            if (handler != null) {
                response.setHandledByNickname(handler.getNickname());
            }
        }

        TargetInfo targetInfo = null;
        try {
            targetInfo = loadTargetInfo(report.getTargetType(), report.getTargetId());
        } catch (RuntimeException ignored) {
        }

        if (targetInfo != null) {
            response.setTargetOwnerUserId(targetInfo.ownerUserId);
            User owner = targetInfo.ownerUserId != null ? userMapper.selectById(targetInfo.ownerUserId) : null;
            if (owner != null) {
                response.setTargetOwnerNickname(owner.getNickname());
                response.setTargetOwnerAvatarUrl(owner.getAvatarUrl());
            }
            response.setTargetPreview(targetInfo.preview);
            response.setTargetImageUrl(targetInfo.imageUrl);
            response.setPhotoId(targetInfo.photoId);
            response.setCommentId(targetInfo.commentId);
        } else {
            response.setTargetPreview("内容已删除或不存在");
            if (TARGET_TYPE_PHOTO.equals(report.getTargetType())) {
                response.setPhotoId(report.getTargetId());
            } else if (TARGET_TYPE_COMMENT.equals(report.getTargetType())) {
                response.setCommentId(report.getTargetId());
            }
        }

        return response;
    }

    private String toTime(LocalDateTime time) {
        return time != null ? time.toString() : "";
    }

    private String limit(String text, int maxLength) {
        if (text == null) return "";
        String trimmed = text.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private record TargetInfo(
            String targetType,
            Long ownerUserId,
            String preview,
            String imageUrl,
            Long photoId,
            Long commentId
    ) {
    }
}

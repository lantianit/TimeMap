package com.timemap.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 业务指标收集器，负责收集业务数据并转换为 Prometheus 指标。
 * 所有指标按最细粒度的维度分类统计，查询层可灵活聚合分析。
 */
@Component
@Slf4j
public class BusinessMetricsCollector {

    @Resource
    private MeterRegistry meterRegistry;

    private final ConcurrentMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    // ==================== 照片相关 ====================

    /** 记录照片上传 */
    public void recordPhotoUpload(String userId, String district) {
        getCounter("photo_upload_total", "照片上传总数",
                "user_id", userId, "district", district).increment();
    }

    /** 记录照片上传耗时（含 COS 上传） */
    public void recordPhotoUploadDuration(String userId, Duration duration) {
        getTimer("photo_upload_duration_seconds", "照片上传耗时",
                "user_id", userId).record(duration);
    }

    /** 记录照片删除 */
    public void recordPhotoDelete(String userId, String deletedBy) {
        getCounter("photo_delete_total", "照片删除总数",
                "user_id", userId, "deleted_by", deletedBy).increment();
    }

    // ==================== 点赞相关 ====================

    /** 记录点赞操作 */
    public void recordLike(String userId, String targetType, String action) {
        getCounter("like_total", "点赞操作总数",
                "user_id", userId, "target_type", targetType, "action", action).increment();
    }

    // ==================== 评论相关 ====================

    /** 记录评论 */
    public void recordComment(String userId, String type) {
        getCounter("comment_total", "评论总数",
                "user_id", userId, "type", type).increment();
    }

    /** 记录评论删除 */
    public void recordCommentDelete(String deletedBy) {
        getCounter("comment_delete_total", "评论删除总数",
                "deleted_by", deletedBy).increment();
    }

    // ==================== 私信相关 ====================

    /** 记录私信发送 */
    public void recordMessage(String userId) {
        getCounter("message_send_total", "私信发送总数",
                "user_id", userId).increment();
    }

    // ==================== 举报相关 ====================

    /** 记录举报提交 */
    public void recordReport(String reason, String targetType) {
        getCounter("report_submit_total", "举报提交总数",
                "reason", reason, "target_type", targetType).increment();
    }

    /** 记录举报处理 */
    public void recordReportHandle(String result) {
        getCounter("report_handle_total", "举报处理总数",
                "result", result).increment();
    }

    // ==================== 用户认证相关 ====================

    /** 记录微信登录 */
    public void recordWechatLogin(String status, boolean isNewUser) {
        getCounter("wechat_login_total", "微信登录总数",
                "status", status, "is_new_user", String.valueOf(isNewUser)).increment();
    }

    /** 记录管理员登录 */
    public void recordAdminLogin(String status) {
        getCounter("admin_login_total", "管理员登录总数",
                "status", status).increment();
    }

    // ==================== COS 操作相关 ====================

    /** 记录 COS 操作 */
    public void recordCosOperation(String operation, String status) {
        getCounter("cos_operation_total", "COS操作总数",
                "operation", operation, "status", status).increment();
    }

    /** 记录 COS 操作耗时 */
    public void recordCosOperationDuration(String operation, Duration duration) {
        getTimer("cos_operation_duration_seconds", "COS操作耗时",
                "operation", operation).record(duration);
    }

    // ==================== 用户处罚相关 ====================

    /** 记录用户处罚 */
    public void recordPunishment(String punishmentType) {
        getCounter("user_punishment_total", "用户处罚总数",
                "punishment_type", punishmentType).increment();
    }

    // ==================== 接口错误相关 ====================

    /** 记录业务异常 */
    public void recordBusinessError(String errorType) {
        getCounter("business_error_total", "业务异常总数",
                "error_type", errorType).increment();
    }

    // ==================== 内部工具方法 ====================

    private Counter getCounter(String name, String description, String... tags) {
        String key = buildKey(name, tags);
        return counterCache.computeIfAbsent(key, k ->
                Counter.builder(name)
                        .description(description)
                        .tags(tags)
                        .register(meterRegistry));
    }

    private Timer getTimer(String name, String description, String... tags) {
        String key = buildKey(name, tags);
        return timerCache.computeIfAbsent(key, k ->
                Timer.builder(name)
                        .description(description)
                        .tags(tags)
                        .register(meterRegistry));
    }

    private String buildKey(String name, String... tags) {
        StringBuilder sb = new StringBuilder(name);
        for (String tag : tags) {
            sb.append('_').append(tag);
        }
        return sb.toString();
    }
}

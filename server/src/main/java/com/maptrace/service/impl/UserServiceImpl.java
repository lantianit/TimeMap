package com.maptrace.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maptrace.common.BusinessException;
import com.maptrace.common.ErrorCode;
import com.maptrace.mapper.UserMapper;
import com.maptrace.model.dto.UpdateProfileRequest;
import com.maptrace.model.vo.UserInfoVO;
import com.maptrace.model.entity.User;
import com.maptrace.service.AdminAuthService;
import com.maptrace.service.CosService;
import com.maptrace.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final AdminAuthService adminAuthService;
    private final CosService cosService;

    @Override
    public UserInfoVO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            String avatarUrl = request.getAvatarUrl().trim();

            // 拒绝无效的本地临时路径（如 http://tmp/xxx、wxfile://xxx）
            if (cosService.isLocalTempPath(avatarUrl)) {
                // 忽略无效头像，不更新
            } else if (cosService.isWxAvatarUrl(avatarUrl) && !cosService.isCosUrl(avatarUrl)) {
                // 微信头像转存到 COS
                String cosUrl = cosService.uploadFromUrl(avatarUrl, userId);
                if (cosUrl != null) {
                    if (user.getAvatarUrl() != null && cosService.isCosUrl(user.getAvatarUrl())) {
                        cosService.scheduleDelete(user.getAvatarUrl(), "avatar", userId, "用户更换头像");
                    }
                    user.setAvatarUrl(cosUrl);
                }
            } else if (cosService.isCosUrl(avatarUrl) || avatarUrl.startsWith("https://")) {
                // 合法的网络 URL（COS 或其他 https），允许更新
                if (user.getAvatarUrl() != null && cosService.isCosUrl(user.getAvatarUrl())
                        && !avatarUrl.equals(user.getAvatarUrl())) {
                    cosService.scheduleDelete(user.getAvatarUrl(), "avatar", userId, "用户更换头像");
                }
                user.setAvatarUrl(avatarUrl);
            }
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getCountry() != null) {
            user.setCountry(request.getCountry());
        }
        if (request.getProvince() != null) {
            user.setProvince(request.getProvince());
        }
        if (request.getCity() != null) {
            user.setCity(request.getCity());
        }
        user.setProfileCompleted(isProfileComplete(user) ? 1 : 0);

        this.updateById(user);

        return buildResponse(user);
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return buildResponse(user);
    }

    @Override
    public UserInfoVO uploadAvatar(Long userId, MultipartFile file) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 旧头像如果是 COS 的，标记延迟删除
        if (user.getAvatarUrl() != null && cosService.isCosUrl(user.getAvatarUrl())) {
            cosService.scheduleDelete(user.getAvatarUrl(), "avatar", userId, "用户更换头像");
        }
        String avatarUrl = cosService.upload(file);
        user.setAvatarUrl(avatarUrl);
        user.setProfileCompleted(isProfileComplete(user) ? 1 : 0);
        this.updateById(user);
        return buildResponse(user);
    }

    @Override
    public void checkBanned(Long userId) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (user.getIsBanned() != null && user.getIsBanned() == 1) {
            throw new BusinessException(ErrorCode.USER_BANNED, "账号已被封禁，无法执行此操作");
        }
    }

    @Override
    public void checkMuted(Long userId) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (user.getIsBanned() != null && user.getIsBanned() == 1) {
            throw new BusinessException(ErrorCode.USER_BANNED, "账号已被封禁，无法执行此操作");
        }
        if (user.getMuteUntil() != null && user.getMuteUntil().isAfter(LocalDateTime.now())) {
            long days = Duration.between(LocalDateTime.now(), user.getMuteUntil()).toDays() + 1;
            throw new BusinessException(ErrorCode.USER_MUTED, "你已被禁言，剩余 " + days + " 天，期间无法评论和发送私信");
        }
    }

    @Override
    public void checkBanUpload(Long userId) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (user.getIsBanned() != null && user.getIsBanned() == 1) {
            throw new BusinessException(ErrorCode.USER_BANNED, "账号已被封禁，无法执行此操作");
        }
        if (user.getBanUploadUntil() != null && user.getBanUploadUntil().isAfter(LocalDateTime.now())) {
            long days = Duration.between(LocalDateTime.now(), user.getBanUploadUntil()).toDays() + 1;
            throw new BusinessException(ErrorCode.USER_UPLOAD_BANNED, "你已被禁止上传照片，剩余 " + days + " 天");
        }
    }

    private UserInfoVO buildResponse(User user) {
        UserInfoVO response = UserInfoVO.from(user);
        response.setIsAdmin(adminAuthService.isAdmin(user.getId()));
        return response;
    }

    private boolean isProfileComplete(User user) {
        return user.getNickname() != null && !user.getNickname().isBlank()
                && user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank();
    }
}

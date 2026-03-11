package com.timemap.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.timemap.mapper.UserMapper;
import com.timemap.model.dto.UpdateProfileRequest;
import com.timemap.model.dto.UserInfoResponse;
import com.timemap.model.entity.User;
import com.timemap.service.AdminAuthService;
import com.timemap.service.CosService;
import com.timemap.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final AdminAuthService adminAuthService;
    private final CosService cosService;

    @Override
    public UserInfoResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = this.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
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
    public UserInfoResponse getUserInfo(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return buildResponse(user);
    }

    @Override
    public UserInfoResponse uploadAvatar(Long userId, MultipartFile file) {
        User user = this.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String avatarUrl = cosService.upload(file);
        user.setAvatarUrl(avatarUrl);
        user.setProfileCompleted(isProfileComplete(user) ? 1 : 0);
        this.updateById(user);
        return buildResponse(user);
    }

    private UserInfoResponse buildResponse(User user) {
        UserInfoResponse response = UserInfoResponse.from(user);
        response.setIsAdmin(adminAuthService.isAdmin(user.getId()));
        return response;
    }

    private boolean isProfileComplete(User user) {
        return user.getNickname() != null && !user.getNickname().isBlank()
                && user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank();
    }
}

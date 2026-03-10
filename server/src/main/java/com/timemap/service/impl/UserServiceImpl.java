package com.timemap.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.timemap.mapper.UserMapper;
import com.timemap.model.dto.UpdateProfileRequest;
import com.timemap.model.dto.UserInfoResponse;
import com.timemap.model.entity.User;
import com.timemap.service.AdminAuthService;
import com.timemap.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final AdminAuthService adminAuthService;

    @Override
    public UserInfoResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = this.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        user.setNickname(request.getNickname());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setGender(request.getGender());
        user.setCountry(request.getCountry());
        user.setProvince(request.getProvince());
        user.setCity(request.getCity());
        user.setProfileCompleted(1);

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

    private UserInfoResponse buildResponse(User user) {
        UserInfoResponse response = UserInfoResponse.from(user);
        response.setIsAdmin(adminAuthService.isAdmin(user.getId()));
        return response;
    }
}

package com.timemap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.timemap.model.dto.UpdateProfileRequest;
import com.timemap.model.dto.UserInfoResponse;
import com.timemap.model.entity.User;
import org.springframework.web.multipart.MultipartFile;

public interface UserService extends IService<User> {

    UserInfoResponse updateProfile(Long userId, UpdateProfileRequest request);

    UserInfoResponse getUserInfo(Long userId);

    UserInfoResponse uploadAvatar(Long userId, MultipartFile file);
}

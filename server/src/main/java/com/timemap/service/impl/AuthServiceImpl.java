package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.timemap.mapper.UserMapper;
import com.timemap.model.dto.BindPhoneResponse;
import com.timemap.model.dto.LoginRequest;
import com.timemap.model.dto.LoginResponse;
import com.timemap.model.dto.WxPhoneNumberResponse;
import com.timemap.model.dto.WxSessionResponse;
import com.timemap.model.entity.User;
import com.timemap.monitor.BusinessMetricsCollector;
import com.timemap.service.AuthService;
import com.timemap.util.JwtUtil;
import com.timemap.util.WxApiUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final WxApiUtil wxApiUtil;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BusinessMetricsCollector metricsCollector;

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 调用微信接口获取 openid
        WxSessionResponse wxResp = wxApiUtil.code2Session(request.getCode());
        String openid = wxResp.getOpenid();

        // 2. 查询或创建用户
        boolean isNew = false;
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid)
        );

        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            isNew = true;
        }

        // 3. 兼容老请求：允许在登录时一并更新资料
        mergeProfile(user, request);
        user.setProfileCompleted(isProfileComplete(user) ? 1 : 0);

        if (isNew) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }

        // 4. 签发 JWT Token
        String token = jwtUtil.generateToken(user.getId(), openid);

        // 监控埋点
        metricsCollector.recordWechatLogin("success", isNew);

        return LoginResponse.of(token, user.getId(), isNew, isPhoneMissing(user), !isProfileComplete(user));
    }

    @Override
    public BindPhoneResponse bindPhone(Long userId, String code) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        WxPhoneNumberResponse response = wxApiUtil.getPhoneNumber(code);
        WxPhoneNumberResponse.PhoneInfo phoneInfo = response.getPhoneInfo();
        user.setPhone(phoneInfo.getPurePhoneNumber());
        user.setCountryCode(phoneInfo.getCountryCode());
        user.setProfileCompleted(isProfileComplete(user) ? 1 : 0);
        userMapper.updateById(user);

        return new BindPhoneResponse(maskPhone(user.getPhone()));
    }

    private void mergeProfile(User user, LoginRequest request) {
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
    }

    private boolean isPhoneMissing(User user) {
        return user.getPhone() == null || user.getPhone().isBlank();
    }

    private boolean isProfileComplete(User user) {
        return user.getNickname() != null && !user.getNickname().isBlank()
                && user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank() || phone.length() < 7) {
            return phone == null ? "" : phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}

package com.timemap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.timemap.mapper.UserMapper;
import com.timemap.model.dto.LoginRequest;
import com.timemap.model.dto.LoginResponse;
import com.timemap.model.dto.WxSessionResponse;
import com.timemap.model.entity.User;
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

        // 3. 更新用户资料
        if (request.getNickname() != null && !request.getNickname().isEmpty()) {
            user.setNickname(request.getNickname());
            user.setAvatarUrl(request.getAvatarUrl());
            user.setGender(request.getGender());
            user.setCountry(request.getCountry());
            user.setProvince(request.getProvince());
            user.setCity(request.getCity());
            user.setProfileCompleted(1);
        }

        if (isNew) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }

        // 4. 签发 JWT Token
        String token = jwtUtil.generateToken(user.getId(), openid);

        return LoginResponse.of(token, user.getId(), isNew);
    }
}

package com.maptrace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.maptrace.common.BusinessException;
import com.maptrace.common.ErrorCode;
import com.maptrace.mapper.UserMapper;
import com.maptrace.model.vo.BindPhoneVO;
import com.maptrace.model.dto.LoginRequest;
import com.maptrace.model.vo.LoginVO;
import com.maptrace.model.dto.WxPhoneNumberResponse;
import com.maptrace.model.dto.WxSessionResponse;
import com.maptrace.model.entity.User;
import com.maptrace.monitor.BusinessMetricsCollector;
import com.maptrace.service.AuthService;
import com.maptrace.service.CosService;
import com.maptrace.util.JwtUtil;
import com.maptrace.util.WxApiUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final WxApiUtil wxApiUtil;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BusinessMetricsCollector metricsCollector;
    private final CosService cosService;

    @Override
    public LoginVO login(LoginRequest request) {
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

        // 微信头像转存到 COS（登录后异步处理，失败不影响登录）
        transferWxAvatarIfNeeded(user);

        // 4. 签发 JWT Token
        String token = jwtUtil.generateToken(user.getId(), openid);

        // 监控埋点
        metricsCollector.recordWechatLogin("success", isNew);

        return LoginVO.of(token, user.getId(), isNew, isPhoneMissing(user), !isProfileComplete(user));
    }

    @Override
    public BindPhoneVO bindPhone(Long userId, String code) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        WxPhoneNumberResponse response = wxApiUtil.getPhoneNumber(code);
        WxPhoneNumberResponse.PhoneInfo phoneInfo = response.getPhoneInfo();
        user.setPhone(phoneInfo.getPurePhoneNumber());
        user.setCountryCode(phoneInfo.getCountryCode());
        user.setProfileCompleted(isProfileComplete(user) ? 1 : 0);
        userMapper.updateById(user);

        return new BindPhoneVO(maskPhone(user.getPhone()));
    }

    private void mergeProfile(User user, LoginRequest request) {
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            String avatarUrl = request.getAvatarUrl().trim();
            // 拒绝无效的本地临时路径（如 http://tmp/xxx、wxfile://xxx）
            if (!cosService.isLocalTempPath(avatarUrl)) {
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

    /**
     * 如果用户头像是微信临时 URL，转存到 COS
     * 转存失败不影响业务流程
     */
    private void transferWxAvatarIfNeeded(User user) {
        String avatarUrl = user.getAvatarUrl();
        if (avatarUrl == null || avatarUrl.isBlank()) return;
        if (cosService.isCosUrl(avatarUrl)) return; // 已经是 COS 链接
        if (!cosService.isWxAvatarUrl(avatarUrl)) return; // 不是微信头像

        String cosUrl = cosService.uploadFromUrl(avatarUrl, user.getId());
        if (cosUrl != null) {
            user.setAvatarUrl(cosUrl);
            userMapper.updateById(user);
        }
    }
}

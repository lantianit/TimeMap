package com.timemap.service;

import com.timemap.model.dto.LoginRequest;
import com.timemap.model.dto.LoginResponse;
import com.timemap.model.dto.BindPhoneResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    BindPhoneResponse bindPhone(Long userId, String code);
}

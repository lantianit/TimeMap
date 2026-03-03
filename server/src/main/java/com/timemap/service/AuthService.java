package com.timemap.service;

import com.timemap.model.dto.LoginRequest;
import com.timemap.model.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}

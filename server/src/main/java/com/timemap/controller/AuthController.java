package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.LoginRequest;
import com.timemap.model.dto.LoginResponse;
import com.timemap.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return Result.ok(response);
        } catch (Exception e) {
            return Result.fail(401, e.getMessage());
        }
    }
}

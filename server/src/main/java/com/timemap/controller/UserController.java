package com.timemap.controller;

import com.timemap.common.Result;
import com.timemap.model.dto.UpdateProfileRequest;
import com.timemap.model.dto.UserInfoResponse;
import com.timemap.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/profile")
    public Result<UserInfoResponse> updateProfile(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UserInfoResponse response = userService.updateProfile(userId, request);
        return Result.ok(response);
    }

    @GetMapping("/info")
    public Result<UserInfoResponse> getUserInfo(@RequestAttribute("userId") Long userId) {
        UserInfoResponse response = userService.getUserInfo(userId);
        return Result.ok(response);
    }
}

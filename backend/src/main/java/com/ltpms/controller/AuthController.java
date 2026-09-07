package com.ltpms.controller;

import com.ltpms.common.Result;
import com.ltpms.dto.ChangePasswordRequest;
import com.ltpms.dto.LoginRequest;
import com.ltpms.dto.LoginResponse;
import com.ltpms.service.AuthService;
import com.ltpms.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * 认证相关接口，提供用户登录、修改密码以及获取当前登录用户信息等功能。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录接口。
     *
     * @param request 登录请求，包含用户名（工号）和密码
     * @return 登录成功后的用户信息与令牌
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 修改当前登录用户的密码。
     *
     * @param request 修改密码请求，包含原密码与新密码
     * @return 操作结果
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(getCurrentUserId(), request);
        return Result.success();
    }

    /**
     * 获取当前登录用户的基本信息。
     *
     * @return 当前登录用户的用户信息
     */
    @GetMapping("/me")
    public Result<LoginResponse.UserInfo> me() {
        return Result.success(authService.getCurrentUser(getCurrentUserId()));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Long) {
            return (Long) authentication.getDetails();
        }
        return null;
    }
}

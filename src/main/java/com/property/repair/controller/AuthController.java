package com.property.repair.controller;

import com.property.repair.common.result.Result;
import com.property.repair.dto.request.LoginRequest;
import com.property.repair.dto.response.LoginResponse;
import com.property.repair.security.LoginUser;
import com.property.repair.security.SecurityUtils;
import com.property.repair.service.AuthService;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@RequestParam String refreshToken) {
        LoginResponse response = authService.refreshToken(refreshToken);
        return Result.success(response);
    }

    @PostMapping("/logout")
    public Result<?> logout(HttpServletRequest request) {
        String token = extractToken(request);
        authService.logout(token);
        return Result.success("退出成功");
    }

    @GetMapping("/me")
    public Result<LoginUser> getCurrentUser() {
        LoginUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error(401, "用户未登录");
        }
        return Result.success(currentUser);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

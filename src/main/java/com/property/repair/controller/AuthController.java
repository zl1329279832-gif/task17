package com.property.repair.controller;

import com.property.repair.common.Result;
import com.property.repair.dto.LoginRequest;
import com.property.repair.dto.LoginResponse;
import com.property.repair.entity.User;
import com.property.repair.mapper.UserMapper;
import com.property.repair.security.JwtTokenProvider;
import com.property.repair.security.SecurityUser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        String accessToken = tokenProvider.generateToken(
                securityUser.getUserId(), securityUser.getUsername(), securityUser.getRole());
        String refreshToken = tokenProvider.generateRefreshToken(
                securityUser.getUserId(), securityUser.getUsername());

        // Update last online
        User user = userMapper.selectById(securityUser.getUserId());
        if (user != null) {
            user.setLastOnlineAt(LocalDateTime.now());
            user.setOnlineStatus(1);
            userMapper.updateById(user);
        }

        // Store token in Redis for validation/revocation
        redisTemplate.opsForValue().set(
                "token:" + securityUser.getUserId(), accessToken, Duration.ofHours(24));

        return Result.ok(LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(securityUser.getUserId())
                .username(securityUser.getUsername())
                .realName(securityUser.getUsername())
                .role(securityUser.getRole())
                .build());
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestParam String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            return Result.unauthorized("Invalid refresh token");
        }

        String username = tokenProvider.getUsernameFromToken(refreshToken);
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        if (user == null) {
            return Result.unauthorized("User not found");
        }

        String newAccessToken = tokenProvider.generateToken(
                user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = tokenProvider.generateRefreshToken(
                user.getId(), user.getUsername());

        redisTemplate.opsForValue().set(
                "token:" + user.getId(), newAccessToken, Duration.ofHours(24));

        return Result.ok(LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .role(user.getRole())
                .build());
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (tokenProvider.validateToken(token)) {
                Long userId = tokenProvider.getUserIdFromToken(token);
                redisTemplate.delete("token:" + userId);
            }
        }
        return Result.ok();
    }

    /**
     * Worker heartbeat endpoint — keeps online status active.
     */
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (tokenProvider.validateToken(token)) {
                Long userId = tokenProvider.getUserIdFromToken(token);
                redisTemplate.opsForValue().set(
                        "worker:heartbeat:" + userId,
                        LocalDateTime.now().toString(),
                        Duration.ofMinutes(10));
            }
        }
        return Result.ok();
    }
}

package com.property.repair.service.impl;

import com.property.repair.common.constants.RedisKeyConstants;
import com.property.repair.common.exception.BusinessException;
import com.property.repair.common.exception.UnauthorizedException;
import com.property.repair.dto.request.LoginRequest;
import com.property.repair.dto.response.LoginResponse;
import com.property.repair.entity.SysUser;
import com.property.repair.mapper.SysUserMapper;
import com.property.repair.security.JwtTokenProvider;
import com.property.repair.service.AuthService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    public AuthServiceImpl(SysUserMapper sysUserMapper,
                           JwtTokenProvider jwtTokenProvider,
                           PasswordEncoder passwordEncoder,
                           RedisTemplate<String, String> redisTemplate) {
        this.sysUserMapper = sysUserMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账户已被禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        // Store token mapping in Redis
        redisTemplate.opsForValue().set(
                RedisKeyConstants.USER_TOKEN + user.getId(),
                accessToken,
                jwtTokenProvider.getAccessTokenExpiration(),
                TimeUnit.MILLISECONDS
        );

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole().name());
        return response;
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("refreshToken已过期或无效");
        }

        String blacklistKey = RedisKeyConstants.JWT_BLACKLIST + refreshToken;
        Boolean isBlacklisted = redisTemplate.hasKey(blacklistKey);
        if (Boolean.TRUE.equals(isBlacklisted)) {
            throw new UnauthorizedException("refreshToken已失效");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new UnauthorizedException("用户不存在或已被禁用");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());

        redisTemplate.opsForValue().set(
                RedisKeyConstants.USER_TOKEN + user.getId(),
                newAccessToken,
                jwtTokenProvider.getAccessTokenExpiration(),
                TimeUnit.MILLISECONDS
        );

        LoginResponse response = new LoginResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole().name());
        return response;
    }

    @Override
    public void logout(String token) {
        if (token != null && jwtTokenProvider.validateToken(token)) {
            long expiration = jwtTokenProvider.getExpirationFromToken(token);
            long now = System.currentTimeMillis();
            long ttl = expiration - now;
            if (ttl > 0) {
                redisTemplate.opsForValue().set(
                        RedisKeyConstants.JWT_BLACKLIST + token,
                        "1",
                        ttl,
                        TimeUnit.MILLISECONDS
                );
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            if (userId != null) {
                redisTemplate.delete(RedisKeyConstants.USER_TOKEN + userId);
            }
        }
    }
}

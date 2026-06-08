package com.property.repair.service;

import com.property.repair.dto.request.LoginRequest;
import com.property.repair.dto.response.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse refreshToken(String refreshToken);

    void logout(String token);
}

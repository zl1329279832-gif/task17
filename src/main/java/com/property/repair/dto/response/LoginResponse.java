package com.property.repair.dto.response;

import lombok.Data;

@Data
public class LoginResponse {

    private String accessToken;

    private String refreshToken;

    private String tokenType = "Bearer";

    private Long expiresIn;

    private Long userId;

    private String username;

    private String role;
}

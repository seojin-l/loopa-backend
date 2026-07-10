package com.example.loopa.domain.auth.dto.response;

public record TokenRefreshResponse(
        String tokenType,
        String accessToken,
        String refreshToken
) {
}
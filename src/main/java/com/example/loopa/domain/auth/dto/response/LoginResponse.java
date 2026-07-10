package com.example.loopa.domain.auth.dto.response;

public record LoginResponse(
        Long userId,
        String email,
        String tokenType,
        String accessToken,
        String refreshToken
) {
}
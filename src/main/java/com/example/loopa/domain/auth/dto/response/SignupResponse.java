package com.example.loopa.domain.auth.dto.response;

import java.time.LocalDateTime;

public record SignupResponse(
        Long userId,
        String email,
        Integer tokenBalance,
        LocalDateTime createdAt
) {
}
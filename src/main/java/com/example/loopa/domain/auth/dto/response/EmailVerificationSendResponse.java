package com.example.loopa.domain.auth.dto.response;

import com.example.loopa.domain.auth.entity.Purpose;

import java.time.LocalDateTime;

public record EmailVerificationSendResponse(
        String email,
        Purpose purpose,
        LocalDateTime expiresAt
) {
}
package com.example.loopa.domain.auth.dto.response;

import com.example.loopa.domain.auth.entity.Purpose;

public record EmailVerificationVerifyResponse(
        String email,
        Purpose purpose,
        Boolean verified
) {
}

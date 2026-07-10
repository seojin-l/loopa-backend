package com.example.loopa.domain.auth.service;

public record VerificationCodeCreatedEvent(
        String email,
        String code
) {
}

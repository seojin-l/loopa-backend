package com.example.loopa.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EmailVerificationVerifyRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "인증번호를 입력해주세요.")
        String code,

        @NotNull(message = "인증 목적을 입력해주세요.")
        String purpose
) {
}

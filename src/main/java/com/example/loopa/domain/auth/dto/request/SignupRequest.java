package com.example.loopa.domain.auth.dto.request;

import com.example.loopa.domain.user.entity.Gender;
import com.example.loopa.domain.user.entity.Job;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignupRequest(

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password,

        @NotBlank(message = "성별을 선택해주세요.")
        Gender gender,

        @NotNull(message = "나이를 입력해주세요.")
        @Min(value = 1, message = "나이는 1 이상이어야 합니다.")
        Integer age,

        @NotBlank(message = "직업을 선택해주세요.")
        Job job
) {
}

package com.example.loopa.domain.user.dto.response;

import com.example.loopa.domain.user.entity.Gender;
import com.example.loopa.domain.user.entity.Job;
import com.example.loopa.domain.user.entity.User;

public record UserMeResponse(
        Long id,
        String email,
        Gender gender,
        Integer age,
        Job job,
        Integer tokenBalance
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getGender(),
                user.getAge(),
                user.getJob(),
                user.getTokenBalance()
        );
    }
}
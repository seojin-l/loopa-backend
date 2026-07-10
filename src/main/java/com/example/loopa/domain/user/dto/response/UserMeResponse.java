package com.example.loopa.domain.user.dto.response;

import com.example.loopa.domain.user.entity.Gender;
import com.example.loopa.domain.user.entity.Job;
import com.example.loopa.domain.user.entity.User;

import java.time.LocalDateTime;

public record UserMeResponse(
        Long userId,
        String email,
        Integer tokenBalance,
        Gender gender,
        Integer age,
        Job job,
        LocalDateTime createdAt
) {
    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getTokenBalance(),
                user.getGender(),
                user.getAge(),
                user.getJob(),
                user.getCreatedAt()
        );
    }
}
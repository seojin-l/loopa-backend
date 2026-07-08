package com.example.loopa.domain.response.dto.response;

import java.time.LocalDateTime;

public record ResponseSubmitResponse(
        Long responseId,
        Long surveyId,
        Boolean isGuest,
        TokenRewardDto tokenReward,
        Integer tokenBalanceAfter,
        String submittedAt
) {

    public record TokenRewardDto(
            Integer maxToken,
            SkippedDto skipped,
            Integer earnedToken
    ) {
    }

    public record SkippedDto(
            Integer multipleChoice,
            Integer subjective
    ) {
    }
}

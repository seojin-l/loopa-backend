package com.example.loopa.domain.survey.dto.response;

public record SurveyCreateResponse(
        Long surveyId,
        String title,
        SurveyDetailResponse.QuestionCountDto questionCount,
        Integer tokenCost,
        Integer tokenBalanceAfter,
        String status,
        String createdAt
) {
}

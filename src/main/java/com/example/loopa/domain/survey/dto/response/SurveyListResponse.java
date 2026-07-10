package com.example.loopa.domain.survey.dto.response;

public record SurveyListResponse(
        Long surveyId,
        String title,
        String category,
        String target,
        Integer estimatedMinutes,
        Integer maxToken,
        String createdAt
) {
}

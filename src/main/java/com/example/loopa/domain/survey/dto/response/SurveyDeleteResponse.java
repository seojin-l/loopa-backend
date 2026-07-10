package com.example.loopa.domain.survey.dto.response;

public record SurveyDeleteResponse(
        Long surveyId,
        Boolean isDeleted
) {
}

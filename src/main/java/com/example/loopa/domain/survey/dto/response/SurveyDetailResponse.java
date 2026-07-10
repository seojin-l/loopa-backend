package com.example.loopa.domain.survey.dto.response;

public record SurveyDetailResponse(
        Long surveyId,
        String title,
        String category,
        String target,
        String description,
        Integer estimatedMinutes,
        String startDate,
        String endDate,
        String status,
        QuestionCountDto questionCount,
        Integer maxToken,
        Long respondentCount,
        String createdAt
) {

    public record QuestionCountDto(
            int multipleChoice,
            int subjective,
            int total
    ) {
    }
}

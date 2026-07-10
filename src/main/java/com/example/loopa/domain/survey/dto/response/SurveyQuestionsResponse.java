package com.example.loopa.domain.survey.dto.response;

import java.util.List;

public record SurveyQuestionsResponse(
        Long surveyId,
        List<QuestionDto> questions
) {

    public record QuestionDto(
            Long questionId,
            Integer order,
            String type,
            String content,
            Boolean isRequired,
            Boolean allowMultiple,
            List<OptionDto> options
    ) {
    }

    public record OptionDto(
            Long optionId,
            Integer order,
            String content
    ) {
    }
}

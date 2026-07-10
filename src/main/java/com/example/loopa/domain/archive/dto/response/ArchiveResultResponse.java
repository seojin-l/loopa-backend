package com.example.loopa.domain.archive.dto.response;

import com.example.loopa.domain.survey.dto.response.SurveyDetailResponse.QuestionCountDto;

import java.util.List;

public record ArchiveResultResponse(
        SurveyInfoDto surveyInfo,
        List<Long> appliedFilters,
        long filteredRespondentCount,
        List<QuestionResultDto> results
) {

    public record SurveyInfoDto(
            Long surveyId,
            String title,
            String category,
            String target,
            String description,
            String startDate,
            String endDate,
            long respondentCount,
            QuestionCountDto questionCount,
            String createdAt
    ) {
    }

    public record QuestionResultDto(
            Long questionId,
            int order,
            String type,
            String content,
            List<OptionResultDto> options,
            List<String> answers
    ) {
    }

    public record OptionResultDto(
            Long optionId,
            String content,
            long count,
            double percentage
    ) {
    }
}

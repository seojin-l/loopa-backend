package com.example.loopa.domain.archive.dto.response;

import com.example.loopa.domain.survey.dto.response.SurveyDetailResponse.QuestionCountDto;

public record ArchiveViewInfoResponse(
        Long surveyId,
        String title,
        String category,
        String target,
        String description,
        String startDate,
        String endDate,
        long respondentCount,
        QuestionCountDto questionCount,
        String createdAt,
        int viewCost,
        boolean alreadyViewed,
        int tokenBalance
) {
}

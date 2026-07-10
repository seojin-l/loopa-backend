package com.example.loopa.domain.user.dto.response;

import com.example.loopa.domain.archive.entity.ArchiveView;
import com.example.loopa.domain.survey.entity.Category;

import java.time.LocalDateTime;

public record UserViewedSurveyResponse(
        Long surveyId,
        String title,
        Category category,
        String target,
        Long respondentCount,
        LocalDateTime viewedAt,
        String createdAt
) {
    public static UserViewedSurveyResponse from(ArchiveView archiveView, Long respondentCount) {
        return new UserViewedSurveyResponse(
                archiveView.getSurvey().getId(),
                archiveView.getSurvey().getTitle(),
                archiveView.getSurvey().getCategory(),
                archiveView.getSurvey().getTarget(),
                respondentCount,
                archiveView.getViewedAt(),
                archiveView.getSurvey().getCreatedAt().toLocalDate().toString()
        );
    }
}

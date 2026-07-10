package com.example.loopa.domain.user.dto.response;

import com.example.loopa.domain.archive.entity.ArchiveView;


import java.time.LocalDate;

public record UserViewedSurveyResponse(
        Long surveyId,
        String title,
        LocalDate createdDate,
        Long respondentCount
) {
    public static UserViewedSurveyResponse from(ArchiveView archiveView, Long respondentCount) {
        return new UserViewedSurveyResponse(
                archiveView.getSurvey().getId(),
                archiveView.getSurvey().getTitle(),
                archiveView.getSurvey().getCreatedAt().toLocalDate(),
                respondentCount
        );
    }
}

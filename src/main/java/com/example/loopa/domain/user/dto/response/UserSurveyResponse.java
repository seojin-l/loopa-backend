package com.example.loopa.domain.user.dto.response;

import com.example.loopa.domain.survey.entity.Category;
import com.example.loopa.domain.survey.entity.Survey;

import java.time.LocalDate;

public record UserSurveyResponse(
        Long surveyId,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        String target,
        Category category,
        String status,
        Boolean sharedToArchive,
        Boolean canDelete,
        Long respondentCount
) {
    public static UserSurveyResponse from(Survey survey, Long respondentCount) {
        boolean sharedToArchive = Boolean.TRUE.equals(survey.getSharedToArchive());

        return new UserSurveyResponse(
                survey.getId(),
                survey.getTitle(),
                survey.getStartDate(),
                survey.getEndDate(),
                survey.getTarget(),
                survey.getCategory(),
                survey.isClosed() ? "종료" : "진행 중",
                sharedToArchive,
                !sharedToArchive,
                respondentCount
        );
    }
}
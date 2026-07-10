package com.example.loopa.domain.response.repository;

import com.example.loopa.domain.response.entity.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    long countBySurveyId(Long surveyId);

    boolean existsBySurveyIdAndRespondentId(Long surveyId, Long respondentId);

    boolean existsBySurveyIdAndGuestKey(Long surveyId, String guestKey);
}

package com.example.loopa.domain.response.repository;

import com.example.loopa.domain.response.entity.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    long countBySurveyId(Long surveyId);
}

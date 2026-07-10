package com.example.loopa.domain.response.repository;

import com.example.loopa.domain.response.entity.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    long countBySurveyId(Long surveyId);

    boolean existsBySurveyIdAndRespondentId(Long surveyId, Long respondentId);

    boolean existsBySurveyIdAndGuestKey(Long surveyId, String guestKey);

    @Query("""
            SELECT sr.survey.id AS surveyId, COUNT(sr.id) AS count
            FROM SurveyResponse sr
            WHERE sr.survey.id IN :surveyIds
            GROUP BY sr.survey.id
            """)
    List<SurveyResponseCount> countBySurveyIds(List<Long> surveyIds);

    interface SurveyResponseCount {
        Long getSurveyId();
        Long getCount();
    }
}

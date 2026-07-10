package com.example.loopa.domain.survey.repository;

import com.example.loopa.domain.survey.entity.Category;
import com.example.loopa.domain.survey.entity.Survey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    Optional<Survey> findByIdAndIsDeletedFalse(Long id);

    @Query("SELECT s FROM Survey s " +
            "WHERE s.isDeleted = false " +
            "AND s.endDate >= :today " +
            "AND (:creatorId IS NULL OR s.creator.id <> :creatorId) " +
            "AND (:category IS NULL OR s.category = :category) " +
            "AND (:keyword IS NULL OR s.title LIKE %:keyword%) " +
            "AND (:cursor IS NULL OR s.id < :cursor) " +
            "ORDER BY s.id DESC")
    List<Survey> findSurveyList(LocalDate today, Long creatorId, Category category,
                                String keyword, Long cursor, int size);

    @Query("SELECT s FROM Survey s " +
            "WHERE s.isDeleted = false " +
            "AND s.sharedToArchive = true " +
            "AND (:category IS NULL OR s.category = :category) " +
            "AND (:keyword IS NULL OR s.title LIKE %:keyword%) " +
            "AND (:cursor IS NULL OR s.id < :cursor) " +
            "ORDER BY s.id DESC")
    List<Survey> findArchiveList(Category category, String keyword, Long cursor, int size);

    @Query("SELECT s FROM Survey s " +
            "WHERE s.isDeleted = false " +
            "AND s.creator.id = :creatorId " +
            "AND s.endDate < :today " +
            "AND (:cursor IS NULL OR s.id < :cursor) " +
            "ORDER BY s.id DESC")
    List<Survey> findShareableSurveys(Long creatorId, LocalDate today, Long cursor, int size);
}

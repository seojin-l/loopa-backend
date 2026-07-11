package com.example.loopa.domain.survey.repository;

import com.example.loopa.domain.survey.entity.Survey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    Optional<Survey> findByIdAndIsDeletedFalse(Long id);

    @Query(value = "SELECT * FROM surveys s " +
            "WHERE s.is_deleted = false " +
            "AND s.end_date >= :today " +
            "AND (:creatorId IS NULL OR s.creator_id <> :creatorId) " +
            "AND (:category IS NULL OR s.category = :category) " +
            "AND (:keyword IS NULL OR s.title LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:cursor IS NULL OR s.id < :cursor) " +
            "ORDER BY s.id DESC " +
            "LIMIT :size", nativeQuery = true)
    List<Survey> findSurveyList(@Param("today") LocalDate today,
                                @Param("creatorId") Long creatorId,
                                @Param("category") String category,
                                @Param("keyword") String keyword,
                                @Param("cursor") Long cursor,
                                @Param("size") int size);

    @Query(value = "SELECT * FROM surveys s " +
            "WHERE s.is_deleted = false " +
            "AND s.shared_to_archive = true " +
            "AND (:category IS NULL OR s.category = :category) " +
            "AND (:keyword IS NULL OR s.title LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:cursor IS NULL OR s.id < :cursor) " +
            "ORDER BY s.id DESC " +
            "LIMIT :size", nativeQuery = true)
    List<Survey> findArchiveList(@Param("category") String category,
                                 @Param("keyword") String keyword,
                                 @Param("cursor") Long cursor,
                                 @Param("size") int size);

    @Query(value = "SELECT * FROM surveys s " +
            "WHERE s.is_deleted = false " +
            "AND s.creator_id = :creatorId " +
            "AND s.end_date < :today " +
            "AND (:cursor IS NULL OR s.id < :cursor) " +
            "ORDER BY s.id DESC " +
            "LIMIT :size", nativeQuery = true)
    List<Survey> findShareableSurveys(@Param("creatorId") Long creatorId,
                                      @Param("today") LocalDate today,
                                      @Param("cursor") Long cursor,
                                      @Param("size") int size);

    @Query(value = "SELECT * FROM surveys s " +
            "WHERE s.is_deleted = false " +
            "AND s.creator_id = :creatorId " +
            "AND (:cursor IS NULL OR s.id < :cursor) " +
            "ORDER BY s.id DESC " +
            "LIMIT :size", nativeQuery = true)
    List<Survey> findMySurveys(@Param("creatorId") Long creatorId,
                               @Param("cursor") Long cursor,
                               @Param("size") int size);

    //메인-직업관련 설문 추천
    @Query(value = "SELECT * FROM surveys s " +
            "WHERE s.is_deleted = false " +
            "AND s.end_date >= :today " +
            "AND (:creatorId IS NULL OR s.creator_id <> :creatorId) " +
            "AND (:keyword IS NULL OR s.title LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:cursor IS NULL OR s.id < :cursor) " +
            "ORDER BY CASE WHEN s.target = :userJob THEN 0 ELSE 1 END, s.id DESC " +
            "LIMIT :size", nativeQuery = true)
    List<Survey> findRecommendedSurveyList(@Param("today") LocalDate today,
                                           @Param("creatorId") Long creatorId,
                                           @Param("userJob") String userJob,
                                           @Param("keyword") String keyword,
                                           @Param("cursor") Long cursor,
                                           @Param("size") int size);
}

package com.example.loopa.domain.archive.repository;

import com.example.loopa.domain.archive.entity.ArchiveView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArchiveViewRepository extends JpaRepository<ArchiveView, Long> {

    boolean existsByViewerIdAndSurveyId(Long viewerId, Long surveyId);

    Optional<ArchiveView> findByViewerIdAndSurveyId(Long viewerId, Long surveyId);

    @Query(value = "SELECT * FROM archive_views av " +
            "WHERE av.viewer_id = :viewerId " +
            "AND (:cursor IS NULL OR av.id < :cursor) " +
            "ORDER BY av.id DESC " +
            "LIMIT :size", nativeQuery = true)
    List<ArchiveView> findViewedSurveysByViewerId(@Param("viewerId") Long viewerId,
                                                   @Param("cursor") Long cursor,
                                                   @Param("size") int size);
}

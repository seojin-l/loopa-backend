package com.example.loopa.domain.archive.repository;

import com.example.loopa.domain.archive.entity.ArchiveView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ArchiveViewRepository extends JpaRepository<ArchiveView, Long> {

    boolean existsByViewerIdAndSurveyId(Long viewerId, Long surveyId);

    Optional<ArchiveView> findByViewerIdAndSurveyId(Long viewerId, Long surveyId);
    @Query("""
            SELECT av
            FROM ArchiveView av
            JOIN FETCH av.survey s
            WHERE av.viewer.id = :viewerId
            ORDER BY av.viewedAt DESC
            """)
    List<ArchiveView> findViewedSurveysByViewerId(Long viewerId);
}

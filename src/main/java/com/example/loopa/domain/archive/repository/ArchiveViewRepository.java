package com.example.loopa.domain.archive.repository;

import com.example.loopa.domain.archive.entity.ArchiveView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArchiveViewRepository extends JpaRepository<ArchiveView, Long> {

    boolean existsByViewerIdAndSurveyId(Long viewerId, Long surveyId);

    Optional<ArchiveView> findByViewerIdAndSurveyId(Long viewerId, Long surveyId);
}

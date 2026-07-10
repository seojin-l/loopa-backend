package com.example.loopa.domain.archive.dto.response;

public record ArchiveListResponse(
        Long surveyId,
        String title,
        String category,
        String target,
        long respondentCount,
        String createdAt
) {
}

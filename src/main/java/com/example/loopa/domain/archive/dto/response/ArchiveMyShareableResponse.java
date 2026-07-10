package com.example.loopa.domain.archive.dto.response;

public record ArchiveMyShareableResponse(
        Long surveyId,
        String title,
        String category,
        String target,
        long respondentCount,
        String status,
        boolean sharedToArchive,
        String createdAt
) {
}

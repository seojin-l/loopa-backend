package com.example.loopa.domain.archive.dto.response;

public record ArchiveViewPurchaseResponse(
        Long viewId,
        Long surveyId,
        int tokenSpent,
        int tokenBalanceBefore,
        int tokenBalanceAfter,
        String viewedAt
) {
}

package com.example.loopa.domain.archive.dto.response;

public record ArchiveShareResponse(
        int sharedCount,
        int rewardTokenPerSurvey,
        int totalRewardToken,
        int tokenBalanceBefore,
        int tokenBalanceAfter
) {
}

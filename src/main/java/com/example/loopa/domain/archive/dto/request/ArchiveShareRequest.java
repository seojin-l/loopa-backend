package com.example.loopa.domain.archive.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ArchiveShareRequest(
        @NotNull @Size(min = 1) List<Long> surveyIds
) {
}

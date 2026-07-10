package com.example.loopa.domain.survey.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SurveyCreateRequest(

        @NotBlank @Size(max = 100)
        String title,

        @Size(max = 1000)
        String description,

        @Size(max = 50)
        String target,

        @NotNull
        String category,

        Integer estimatedMinutes,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @NotNull @Size(min = 1)
        @Valid
        List<QuestionRequest> questions
) {

    public record QuestionRequest(

            @NotNull
            Integer order,

            @NotNull
            String type,

            @NotBlank @Size(max = 200)
            String content,

            @NotNull
            Boolean isRequired,

            Boolean allowMultiple,

            @Valid
            List<OptionRequest> options
    ) {
    }

    public record OptionRequest(

            @NotNull
            Integer order,

            @NotBlank @Size(max = 200)
            String content
    ) {
    }
}

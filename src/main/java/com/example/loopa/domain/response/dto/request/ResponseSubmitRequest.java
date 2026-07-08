package com.example.loopa.domain.response.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ResponseSubmitRequest(

        String guestKey,

        @NotNull @Size(min = 1)
        @Valid
        List<AnswerRequest> answers
) {

    public record AnswerRequest(

            @NotNull
            Long questionId,

            List<Long> selectedOptionIds,

            String answerText
    ) {
    }
}

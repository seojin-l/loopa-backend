package com.example.loopa.domain.response.controller;

import com.example.loopa.domain.response.dto.request.ResponseSubmitRequest;
import com.example.loopa.domain.response.dto.response.ResponseSubmitResponse;
import com.example.loopa.domain.response.service.ResponseService;
import com.example.loopa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/surveys/{surveyId}/responses")
@RequiredArgsConstructor
public class ResponseController {

    private final ResponseService responseService;

    @PostMapping
    public ResponseEntity<ApiResponse<ResponseSubmitResponse>> submit(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long surveyId,
            @Valid @RequestBody ResponseSubmitRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(responseService.submit(userId, surveyId, request)));
    }
}

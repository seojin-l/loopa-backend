package com.example.loopa.domain.survey.controller;

import com.example.loopa.domain.survey.dto.request.SurveyCreateRequest;
import com.example.loopa.domain.survey.dto.response.*;
import com.example.loopa.domain.survey.service.SurveyService;
import com.example.loopa.global.common.CursorPageResponse;
import com.example.loopa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @GetMapping
    public ResponseEntity<ApiResponse<CursorPageResponse<SurveyListResponse>>> getList(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(surveyService.getList(userId, category, keyword, cursor, size)));
    }

    @GetMapping("/{surveyId}")
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> getDetail(@PathVariable Long surveyId) {
        return ResponseEntity.ok(ApiResponse.success(surveyService.getDetail(surveyId)));
    }

    @GetMapping("/{surveyId}/questions")
    public ResponseEntity<ApiResponse<SurveyQuestionsResponse>> getQuestions(@PathVariable Long surveyId) {
        return ResponseEntity.ok(ApiResponse.success(surveyService.getQuestions(surveyId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SurveyCreateResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SurveyCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(surveyService.create(userId, request)));
    }

    @DeleteMapping("/{surveyId}")
    public ResponseEntity<ApiResponse<SurveyDeleteResponse>> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long surveyId) {

        return ResponseEntity.ok(ApiResponse.success(surveyService.delete(userId, surveyId)));
    }
}

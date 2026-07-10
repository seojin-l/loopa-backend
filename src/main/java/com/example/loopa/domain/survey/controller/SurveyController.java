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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    // SURVEY-01 목록 (게스트 허용)
    // TODO: @CurrentUser 적용 후 userId를 Optional로 받기
    @GetMapping
    public ResponseEntity<ApiResponse<CursorPageResponse<SurveyListResponse>>> getList(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(surveyService.getList(userId, category, keyword, cursor, size)));
    }

    // SURVEY-02 상세 (게스트 허용)
    @GetMapping("/{surveyId}")
    public ResponseEntity<ApiResponse<SurveyDetailResponse>> getDetail(@PathVariable Long surveyId) {
        return ResponseEntity.ok(ApiResponse.success(surveyService.getDetail(surveyId)));
    }

    // SURVEY-03 문항 조회 (게스트 허용)
    @GetMapping("/{surveyId}/questions")
    public ResponseEntity<ApiResponse<SurveyQuestionsResponse>> getQuestions(@PathVariable Long surveyId) {
        return ResponseEntity.ok(ApiResponse.success(surveyService.getQuestions(surveyId)));
    }

    // SURVEY-04 생성 (회원 전용)
    // TODO: @CurrentUser 적용 후 교체
    @PostMapping
    public ResponseEntity<ApiResponse<SurveyCreateResponse>> create(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SurveyCreateRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(surveyService.create(userId, request)));
    }

    // SURVEY-05 삭제 (회원 전용)
    // TODO: @CurrentUser 적용 후 교체
    @DeleteMapping("/{surveyId}")
    public ResponseEntity<ApiResponse<SurveyDeleteResponse>> delete(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long surveyId) {

        return ResponseEntity.ok(ApiResponse.success(surveyService.delete(userId, surveyId)));
    }
}

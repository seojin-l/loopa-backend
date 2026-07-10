package com.example.loopa.domain.archive.controller;

import com.example.loopa.domain.archive.dto.request.ArchiveShareRequest;
import com.example.loopa.domain.archive.dto.response.*;
import com.example.loopa.domain.archive.service.ArchiveService;
import com.example.loopa.global.common.CursorPageResponse;
import com.example.loopa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    // ARCHIVE-01 아카이브 설문 목록 (회원 전용)
    // TODO: @CurrentUser 적용 후 교체
    @GetMapping("/surveys")
    public ResponseEntity<ApiResponse<CursorPageResponse<ArchiveListResponse>>> getList(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                archiveService.getList(category, keyword, cursor, size)));
    }

    // ARCHIVE-02 열람 정보 조회 (회원 전용)
    // TODO: @CurrentUser 적용 후 교체
    @GetMapping("/surveys/{surveyId}")
    public ResponseEntity<ApiResponse<ArchiveViewInfoResponse>> getViewInfo(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long surveyId) {

        return ResponseEntity.ok(ApiResponse.success(
                archiveService.getViewInfo(userId, surveyId)));
    }

    // ARCHIVE-03 열람 구매 (회원 전용)
    // TODO: @CurrentUser 적용 후 교체
    @PostMapping("/surveys/{surveyId}/views")
    public ResponseEntity<ApiResponse<ArchiveViewPurchaseResponse>> purchaseView(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long surveyId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(archiveService.purchaseView(userId, surveyId)));
    }

    // ARCHIVE-04 세부 결과 조회 (회원 전용)
    // TODO: @CurrentUser 적용 후 교체
    @GetMapping("/surveys/{surveyId}/results")
    public ResponseEntity<ApiResponse<ArchiveResultResponse>> getResults(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long surveyId,
            @RequestParam(required = false) List<Long> filters) {

        return ResponseEntity.ok(ApiResponse.success(
                archiveService.getResults(userId, surveyId, filters)));
    }

    // ARCHIVE-05 공유 가능한 내 설문 목록 (회원 전용)
    // TODO: @CurrentUser 적용 후 교체
    @GetMapping("/my-surveys")
    public ResponseEntity<ApiResponse<CursorPageResponse<ArchiveMyShareableResponse>>> getMySurveys(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                archiveService.getMyShareableSurveys(userId, cursor, size)));
    }

    // ARCHIVE-06 설문 공유 (회원 전용)
    // TODO: @CurrentUser 적용 후 교체
    @PostMapping("/shares")
    public ResponseEntity<ApiResponse<ArchiveShareResponse>> share(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ArchiveShareRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(archiveService.share(userId, request)));
    }
}

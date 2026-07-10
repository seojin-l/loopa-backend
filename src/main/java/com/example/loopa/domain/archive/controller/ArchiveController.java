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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    @GetMapping("/surveys")
    public ResponseEntity<ApiResponse<CursorPageResponse<ArchiveListResponse>>> getList(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                archiveService.getList(category, keyword, cursor, size)));
    }

    @GetMapping("/surveys/{surveyId}")
    public ResponseEntity<ApiResponse<ArchiveViewInfoResponse>> getViewInfo(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long surveyId) {

        return ResponseEntity.ok(ApiResponse.success(
                archiveService.getViewInfo(userId, surveyId)));
    }

    @PostMapping("/surveys/{surveyId}/views")
    public ResponseEntity<ApiResponse<ArchiveViewPurchaseResponse>> purchaseView(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long surveyId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(archiveService.purchaseView(userId, surveyId)));
    }

    @GetMapping("/surveys/{surveyId}/results")
    public ResponseEntity<ApiResponse<ArchiveResultResponse>> getResults(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long surveyId,
            @RequestParam(required = false) List<Long> filters) {

        return ResponseEntity.ok(ApiResponse.success(
                archiveService.getResults(userId, surveyId, filters)));
    }

    @GetMapping("/my-surveys")
    public ResponseEntity<ApiResponse<CursorPageResponse<ArchiveMyShareableResponse>>> getMySurveys(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                archiveService.getMyShareableSurveys(userId, cursor, size)));
    }

    @PostMapping("/shares")
    public ResponseEntity<ApiResponse<ArchiveShareResponse>> share(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ArchiveShareRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(archiveService.share(userId, request)));
    }
}

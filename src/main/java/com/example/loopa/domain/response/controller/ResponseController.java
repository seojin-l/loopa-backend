package com.example.loopa.domain.response.controller;

import com.example.loopa.domain.response.dto.request.ResponseSubmitRequest;
import com.example.loopa.domain.response.dto.response.ResponseSubmitResponse;
import com.example.loopa.domain.response.service.ResponseService;
import com.example.loopa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/surveys/{surveyId}/responses")
@RequiredArgsConstructor
public class ResponseController {

    private final ResponseService responseService;

    // RESPONSE-01 응답 제출 (게스트 허용)
    // TODO: @CurrentUser 적용 후 교체
    @PostMapping
    public ApiResponse<ResponseSubmitResponse> submit(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable Long surveyId,
            @Valid @RequestBody ResponseSubmitRequest request) {

        return ApiResponse.success(responseService.submit(userId, surveyId, request));
    }
}

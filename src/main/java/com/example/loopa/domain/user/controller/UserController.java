package com.example.loopa.domain.user.controller;

import com.example.loopa.domain.user.dto.response.UserMeResponse;
import com.example.loopa.domain.user.dto.response.UserSurveyResponse;
import com.example.loopa.domain.user.dto.response.UserViewedSurveyResponse;
import com.example.loopa.domain.user.service.UserService;
import com.example.loopa.global.common.CursorPageResponse;
import com.example.loopa.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getMyInfo(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(ApiResponse.success(userService.getMyInfo(userId)));
    }

    @GetMapping("/me/surveys")
    public ResponseEntity<ApiResponse<CursorPageResponse<UserSurveyResponse>>> getMySurveys(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(userService.getMySurveys(userId, cursor, size)));
    }

    @GetMapping("/me/viewed-surveys")
    public ResponseEntity<ApiResponse<CursorPageResponse<UserViewedSurveyResponse>>> getMyViewedSurveys(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(userService.getMyViewedSurveys(userId, cursor, size)));
    }
}

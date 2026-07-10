package com.example.loopa.domain.user.controller;

import com.example.loopa.domain.user.dto.response.UserMeResponse;
import com.example.loopa.domain.user.dto.response.UserSurveyResponse;
import com.example.loopa.domain.user.dto.response.UserViewedSurveyResponse;
import com.example.loopa.domain.user.service.UserService;
import com.example.loopa.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public ResponseEntity<ApiResponse<List<UserSurveyResponse>>> getMySurveys(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(ApiResponse.success(userService.getMySurveys(userId)));
    }

    @GetMapping("/me/viewed-surveys")
    public ResponseEntity<ApiResponse<List<UserViewedSurveyResponse>>> getMyViewedSurveys(
            @AuthenticationPrincipal Long userId) {

        return ResponseEntity.ok(ApiResponse.success(userService.getMyViewedSurveys(userId)));
    }
}

package com.example.loopa.domain.user.controller;


import com.example.loopa.domain.user.dto.response.UserMeResponse;
import com.example.loopa.domain.user.dto.response.UserSurveyResponse;
import com.example.loopa.domain.user.service.UserService;
import com.example.loopa.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.loopa.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getMyInfo(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        return ApiResponse.success(userService.getMyInfo(userId));
    }

    @GetMapping("/me/surveys")
    public ApiResponse<List<UserSurveyResponse>> getMySurveys(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();

        return ApiResponse.success(userService.getMySurveys(userId));
    }

}

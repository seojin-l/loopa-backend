package com.example.loopa.domain.user.controller;

import com.example.loopa.domain.user.dto.response.UserMeResponse;
import com.example.loopa.domain.user.service.UserService;
import com.example.loopa.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}

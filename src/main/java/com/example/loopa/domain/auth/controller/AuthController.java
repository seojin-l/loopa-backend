package com.example.loopa.domain.auth.controller;

import com.example.loopa.domain.auth.dto.request.*;
import com.example.loopa.domain.auth.dto.response.AuthMessageResponse;
import com.example.loopa.domain.auth.dto.response.LoginResponse;
import com.example.loopa.domain.auth.dto.response.TokenRefreshResponse;
import com.example.loopa.domain.auth.service.AuthService;
import com.example.loopa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/email-verifications")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> sendSignupEmailVerification(
            @Valid @RequestBody EmailVerificationSendRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                authService.sendSignupEmailVerification(request)));
    }

    @PostMapping("/email-verifications/verify")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> verifySignupEmail(
            @Valid @RequestBody EmailVerificationVerifyRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                authService.verifySignupEmail(request)));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> signup(
            @Valid @RequestBody SignupRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.signup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
            @Valid @RequestBody TokenRefreshRequest request) {

        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> logout(
            @Valid @RequestBody LogoutRequest request) {

        return ResponseEntity.ok(ApiResponse.success(authService.logout(request)));
    }
}


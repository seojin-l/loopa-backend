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
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/email-verifications")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> sendEmailVerification(
            @Valid @RequestBody EmailVerificationSendRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                authService.sendEmailVerification(request)));
    }

    @PostMapping("/email-verifications/verify")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> verifyEmail(
            @Valid @RequestBody EmailVerificationVerifyRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                authService.verifyEmail(request)));
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

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resetPassword(
            @Valid @RequestBody PasswordResetRequest request) {

        return ResponseEntity.ok(ApiResponse.success(authService.resetPassword(request)));
    }
}

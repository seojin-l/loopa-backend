package com.example.loopa.domain.auth.controller;

import com.example.loopa.domain.auth.dto.request.*;
import com.example.loopa.domain.auth.dto.response.*;
import com.example.loopa.domain.auth.service.AuthService;
import com.example.loopa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.loopa.domain.auth.dto.response.EmailVerificationSendResponse;
import com.example.loopa.domain.auth.dto.response.EmailVerificationVerifyResponse;
import com.example.loopa.domain.auth.dto.response.SignupResponse;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/email-verifications")
    public ApiResponse<EmailVerificationSendResponse> sendEmailVerification(
            @Valid @RequestBody EmailVerificationSendRequest request
    ) {
        return ApiResponse.success(
                "인증번호가 발송되었습니다.",
                authService.sendEmailVerification(request)
        );
    }

    @PostMapping("/email-verifications/verify")
    public ApiResponse<EmailVerificationVerifyResponse> verifyEmail(
            @Valid @RequestBody EmailVerificationVerifyRequest request
    ) {
        return ApiResponse.success(
                "인증번호가 일치합니다.",
                authService.verifyEmail(request)
        );
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", authService.signup(request)));
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
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(request);

        return ResponseEntity.ok(
                ApiResponse.success("로그아웃되었습니다.")
        );
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<AuthMessageResponse>> resetPassword(
            @Valid @RequestBody PasswordResetRequest request) {

        return ResponseEntity.ok(ApiResponse.success(authService.resetPassword(request)));
    }
}

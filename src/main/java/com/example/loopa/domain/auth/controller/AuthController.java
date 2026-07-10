package com.example.loopa.domain.auth.controller;

import com.example.loopa.domain.auth.dto.request.EmailVerificationSendRequest;
import com.example.loopa.domain.auth.dto.request.EmailVerificationVerifyRequest;
import com.example.loopa.domain.auth.dto.request.SignupRequest;
import com.example.loopa.domain.auth.dto.response.AuthMessageResponse;
import com.example.loopa.domain.auth.service.AuthService;
import com.example.loopa.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.example.loopa.domain.auth.dto.request.LoginRequest;
import com.example.loopa.domain.auth.dto.response.LoginResponse;
import com.example.loopa.domain.auth.dto.request.LogoutRequest;
import com.example.loopa.domain.auth.dto.request.TokenRefreshRequest;
import com.example.loopa.domain.auth.dto.response.TokenRefreshResponse;
import com.example.loopa.domain.auth.dto.request.PasswordResetRequest;


@RestController
@RequestMapping
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    //회원가입 인증번호 발송
    @PostMapping("/email-verifications")
    public ApiResponse<AuthMessageResponse> sendSignupEmailVerfication(@Valid @RequestBody EmailVerificationSendRequest request) {
        return ApiResponse.success(authService.sendSignupEmailVerification(request));
    }

    //회원가입 인증번호 검증
    @PostMapping("/email-verifications/verify")
    public ApiResponse<AuthMessageResponse> verifySignupEmail(@Valid @RequestBody EmailVerificationVerifyRequest request) {
        return ApiResponse.success(authService.verifySignupEmail(request));
    }
    //회원가입
    @PostMapping("/signup")
    public ApiResponse<AuthMessageResponse> signup(@Valid @RequestBody SignupRequest request){
        return ApiResponse.success(authService.signup(request));
    }

    //로그인
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/token/refresh")
    public ApiResponse<TokenRefreshResponse> refresh(
            @Valid @RequestBody TokenRefreshRequest request
    ) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<AuthMessageResponse> logout(
            @Valid @RequestBody LogoutRequest request
    ) {
        return ApiResponse.success(authService.logout(request));
    }

    //비밀번호 찾기 인증번호 발송
    @PostMapping("/password-reset/email-verifications")
    public ApiResponse<AuthMessageResponse> sendPasswordResetEmailVerification(
            @Valid @RequestBody EmailVerificationSendRequest request
    ) {
        return ApiResponse.success(authService.sendPasswordResetEmailVerification(request));
    }

    //비밀번호 찾기 인증번호 검증
    @PostMapping("/password-reset/email-verifications/verify")
    public ApiResponse<AuthMessageResponse> verifyPasswordResetEmail(
            @Valid @RequestBody EmailVerificationVerifyRequest request
    ) {
        return ApiResponse.success(authService.verifyPasswordResetEmail(request));
    }

    //비밀번호 변경
    @PostMapping("/password/reset")
    public ApiResponse<AuthMessageResponse> resetPassword(
            @Valid @RequestBody PasswordResetRequest request
    ) {
        return ApiResponse.success(authService.resetPassword(request));
    }
}




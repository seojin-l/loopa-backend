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
}




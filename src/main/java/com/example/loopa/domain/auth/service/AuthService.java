package com.example.loopa.domain.auth.service;

import com.example.loopa.domain.auth.dto.request.EmailVerificationSendRequest;
import com.example.loopa.domain.auth.dto.request.EmailVerificationVerifyRequest;
import com.example.loopa.domain.auth.dto.request.LoginRequest;
import com.example.loopa.domain.auth.dto.request.LogoutRequest;
import com.example.loopa.domain.auth.dto.request.SignupRequest;
import com.example.loopa.domain.auth.dto.request.TokenRefreshRequest;
import com.example.loopa.domain.auth.dto.response.AuthMessageResponse;
import com.example.loopa.domain.auth.dto.response.LoginResponse;
import com.example.loopa.domain.auth.dto.response.TokenRefreshResponse;
import com.example.loopa.domain.auth.entity.EmailVerification;
import com.example.loopa.domain.auth.entity.Purpose;
import com.example.loopa.domain.auth.entity.RefreshToken;
import com.example.loopa.domain.auth.repository.EmailVerificationRepository;
import com.example.loopa.domain.auth.repository.RefreshTokenRepository;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.error.code.AuthErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import com.example.loopa.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;

    @Transactional
    public AuthMessageResponse sendSignupEmailVerification(EmailVerificationSendRequest request) {
        String email = request.email();

        if (userRepository.existsByEmail(email)) {
            throw new GeneralException(AuthErrorCode.ALREADY_EXISTS);
        }

        emailVerificationRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, Purpose.SIGNUP)
                .ifPresent(latest -> {
                    if (latest.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1))) {
                        throw new GeneralException(AuthErrorCode.EMAIL_RATE_LIMITED);
                    }
                });

        emailVerificationRepository.deleteAllByEmailAndPurpose(email, Purpose.SIGNUP);

        String code = createVerificationCode();

        EmailVerification emailVerification = new EmailVerification(
                email,
                code,
                Purpose.SIGNUP,
                LocalDateTime.now().plusMinutes(10)
        );

        emailVerificationRepository.save(emailVerification);

        emailService.sendVerificationCode(email, code);

        return new AuthMessageResponse("인증번호가 발송되었습니다");
    }

    @Transactional
    public AuthMessageResponse verifySignupEmail(EmailVerificationVerifyRequest request) {
        EmailVerification emailVerification = emailVerificationRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(
                        request.email(), Purpose.SIGNUP
                )
                .orElseThrow(() -> new GeneralException(AuthErrorCode.VERIFICATION_CODE_MISMATCH));

        if (emailVerification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new GeneralException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
        }

        if (emailVerification.isMaxAttemptExceeded()) {
            throw new GeneralException(AuthErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);
        }

        if (!emailVerification.getCode().equals(request.code())) {
            emailVerification.incrementAttempt();
            if (emailVerification.isMaxAttemptExceeded()) {
                emailVerification.invalidate();
            }
            throw new GeneralException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        emailVerification.verify();

        return new AuthMessageResponse("인증번호가 일치합니다");
    }

    @Transactional
    public AuthMessageResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new GeneralException(AuthErrorCode.ALREADY_EXISTS);
        }

        boolean verified = emailVerificationRepository.existsByEmailAndPurposeAndVerifiedTrueAndExpiresAtAfter(
                request.email(),
                Purpose.SIGNUP,
                LocalDateTime.now()
        );

        if (!verified) {
            throw new GeneralException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                request.email(),
                encodedPassword,
                request.gender(),
                request.age(),
                request.job()
        );
        userRepository.save(user);

        emailVerificationRepository.deleteAllByEmailAndPurpose(request.email(), Purpose.SIGNUP);

        return new AuthMessageResponse("회원가입이 완료되었습니다");
    }

    private String createVerificationCode() {
        int number = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new GeneralException(AuthErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new GeneralException(AuthErrorCode.LOGIN_FAILED);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail());

        refreshTokenRepository.deleteAllByUser(user);
        refreshTokenRepository.flush();

        RefreshToken savedRefreshToken = new RefreshToken(
                user,
                refreshToken,
                LocalDateTime.now().plus(Duration.ofMillis(jwtProvider.getRefreshTokenExpiration()))
        );
        refreshTokenRepository.save(savedRefreshToken);

        return new LoginResponse(accessToken, refreshToken);
    }

    @Transactional
    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtProvider.validateToken(refreshToken)) {
            throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!jwtProvider.isRefreshToken(refreshToken)) {
            throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshToken savedRefreshToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (savedRefreshToken.isExpired()) {
            throw new GeneralException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        User user = savedRefreshToken.getUser();

        String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail());

        return new TokenRefreshResponse(newAccessToken);
    }

    @Transactional
    public AuthMessageResponse logout(LogoutRequest request) {
        String refreshToken = request.refreshToken();

        if (!jwtProvider.validateToken(refreshToken)) {
            throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        if (!jwtProvider.isRefreshToken(refreshToken)) {
            throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshToken savedRefreshToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        refreshTokenRepository.delete(savedRefreshToken);

        return new AuthMessageResponse("로그아웃되었습니다.");
    }
}

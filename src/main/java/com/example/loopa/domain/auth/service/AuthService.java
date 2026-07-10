package com.example.loopa.domain.auth.service;

import com.example.loopa.domain.auth.dto.request.EmailVerificationSendRequest;
import com.example.loopa.domain.auth.dto.request.EmailVerificationVerifyRequest;
import com.example.loopa.domain.auth.dto.request.LoginRequest;
import com.example.loopa.domain.auth.dto.request.LogoutRequest;
import com.example.loopa.domain.auth.dto.request.PasswordResetRequest;
import com.example.loopa.domain.auth.dto.request.SignupRequest;
import com.example.loopa.domain.auth.dto.request.TokenRefreshRequest;
import com.example.loopa.domain.auth.dto.response.*;
import com.example.loopa.domain.auth.entity.EmailVerification;
import com.example.loopa.domain.auth.entity.Purpose;
import com.example.loopa.domain.auth.entity.RefreshToken;
import com.example.loopa.domain.auth.repository.EmailVerificationRepository;
import com.example.loopa.domain.auth.repository.RefreshTokenRepository;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.error.code.AuthErrorCode;
import com.example.loopa.global.error.code.GlobalErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import com.example.loopa.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.loopa.domain.auth.dto.response.EmailVerificationSendResponse;
import com.example.loopa.domain.auth.dto.response.EmailVerificationVerifyResponse;
import com.example.loopa.domain.token.service.TokenService;
import com.example.loopa.domain.token.entity.TokenTxType;

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
    private final ApplicationEventPublisher eventPublisher;
    private final EmailVerificationFailHandler emailVerificationFailHandler;
    private final TokenService tokenService;

    @Transactional
    public EmailVerificationSendResponse sendEmailVerification(EmailVerificationSendRequest request) {
        Purpose purpose = parsePurpose(request.purpose());
        String email = request.email();

        if (purpose == Purpose.SIGNUP && userRepository.existsByEmail(email)) {
            throw new GeneralException(AuthErrorCode.ALREADY_EXISTS);
        }

        if (purpose == Purpose.PASSWORD_RESET && !userRepository.existsByEmail(email)) {
            throw new GeneralException(AuthErrorCode.EMAIL_NOT_REGISTERED);
        }

        emailVerificationRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .ifPresent(latest -> {
                    if (latest.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1))) {
                        throw new GeneralException(AuthErrorCode.EMAIL_RATE_LIMITED);
                    }
                });

        emailVerificationRepository.deleteAllByEmailAndPurpose(email, purpose);

        String code = createVerificationCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        EmailVerification emailVerification = new EmailVerification(
                email,
                code,
                purpose,
                expiresAt
        );

        emailVerificationRepository.save(emailVerification);

        eventPublisher.publishEvent(new VerificationCodeCreatedEvent(email, code));

        return new EmailVerificationSendResponse(
                email,
                purpose,
                expiresAt
        );
    }

    @Transactional
    public EmailVerificationVerifyResponse verifyEmail(EmailVerificationVerifyRequest request) {
        Purpose purpose = parsePurpose(request.purpose());

        EmailVerification emailVerification = emailVerificationRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(request.email(), purpose)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.VERIFICATION_CODE_MISMATCH));

        if (emailVerification.isVerified()) {
            return new EmailVerificationVerifyResponse(
                    emailVerification.getEmail(),
                    emailVerification.getPurpose(),
                    true
            );
        }

        if (emailVerification.isMaxAttemptExceeded()) {
            throw new GeneralException(AuthErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);
        }

        if (emailVerification.isExpiredOrInvalidated()) {
            throw new GeneralException(AuthErrorCode.VERIFICATION_CODE_EXPIRED);
        }

        if (!emailVerification.getCode().equals(request.code())) {
            emailVerificationFailHandler.recordFailedAttempt(emailVerification.getId());
            throw new GeneralException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        emailVerification.verify();

        return new EmailVerificationVerifyResponse(
                emailVerification.getEmail(),
                emailVerification.getPurpose(),
                emailVerification.isVerified()
        );
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
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
        User savedUser=userRepository.save(user);

        int tokenBalance = tokenService.record(
                savedUser.getId(),
                TokenTxType.SIGNUP_BONUS,
                100,
                null,
                null
        );

        emailVerificationRepository.deleteAllByEmailAndPurpose(request.email(), Purpose.SIGNUP);

        return new SignupResponse(savedUser.getId(), savedUser.getEmail(), tokenBalance, savedUser.getCreatedAt()
        );
    }

    private String createVerificationCode() {
        int number = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    private Purpose parsePurpose(String purpose) {
        try {
            return Purpose.valueOf(purpose);
        } catch (IllegalArgumentException e) {
            throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
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

        return new LoginResponse(user.getId(), user.getEmail(), "Bearer", accessToken, refreshToken);
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
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId(), user.getEmail());

        refreshTokenRepository.delete(savedRefreshToken);
        refreshTokenRepository.flush();

        RefreshToken rotatedRefreshToken = new RefreshToken(
                user,
                newRefreshToken,
                LocalDateTime.now().plus(Duration.ofMillis(jwtProvider.getRefreshTokenExpiration()))
        );

        refreshTokenRepository.save(rotatedRefreshToken);

        return new TokenRefreshResponse("Bearer", newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(LogoutRequest request) {
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
    }

    @Transactional
    public AuthMessageResponse resetPassword(PasswordResetRequest request) {
        boolean verified = emailVerificationRepository.existsByEmailAndPurposeAndVerifiedTrueAndExpiresAtAfter(
                request.email(),
                Purpose.PASSWORD_RESET,
                LocalDateTime.now()
        );

        if (!verified) {
            throw new GeneralException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new GeneralException(AuthErrorCode.EMAIL_NOT_REGISTERED));

        user.updatePassword(passwordEncoder.encode(request.newPassword()));

        emailVerificationRepository.deleteAllByEmailAndPurpose(request.email(), Purpose.PASSWORD_RESET);

        return new AuthMessageResponse("비밀번호가 변경되었습니다");
    }
}

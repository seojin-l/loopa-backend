package com.example.loopa.domain.auth.service;

import com.example.loopa.domain.auth.dto.request.EmailVerificationSendRequest;
import com.example.loopa.domain.auth.dto.request.EmailVerificationVerifyRequest;
import com.example.loopa.domain.auth.dto.request.SignupRequest;
import com.example.loopa.domain.auth.dto.response.AuthMessageResponse;
import com.example.loopa.domain.auth.entity.EmailVerification;
import com.example.loopa.domain.auth.entity.Purpose;
import com.example.loopa.domain.auth.repository.EmailVerificationRepository;
import com.example.loopa.domain.auth.repository.RefreshTokenRepository;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.config.PasswordConfig;
import com.example.loopa.global.error.code.AuthErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.loopa.domain.auth.dto.request.LoginRequest;
import com.example.loopa.domain.auth.dto.response.LoginResponse;
import com.example.loopa.global.security.JwtProvider;
import com.example.loopa.domain.auth.dto.request.LogoutRequest;
import com.example.loopa.domain.auth.dto.request.TokenRefreshRequest;
import com.example.loopa.domain.auth.dto.response.TokenRefreshResponse;
import com.example.loopa.domain.auth.entity.RefreshToken;
import com.example.loopa.domain.auth.repository.RefreshTokenRepository;
import java.time.Duration;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    //인증번호 받기 눌렀을때
    @Transactional
    public AuthMessageResponse sendSignupEmailVerification(EmailVerificationSendRequest request) {
        String email= request.email();

        //이미 가입된 이메일인지 확인
        if (userRepository.existsByEmail(email)){
            throw new GeneralException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        //인증번호 생성
        String code=createVerificationCode();

        EmailVerification emailVerification = new EmailVerification(
                email,
                code,
                Purpose.SIGNUP,
                LocalDateTime.now().plusMinutes(10)
        );

        emailVerificationRepository.save(emailVerification);

        System.out.println("회원가입 인증번호 email =" + email + ",code=" + code);
        return new AuthMessageResponse("인증번호가 발송되었습니다");
    }

    //확인 눌렀을때
    @Transactional
    public AuthMessageResponse verifySignupEmail(EmailVerificationVerifyRequest request) {//가장 최근 인증번호 찾기
        EmailVerification emailVerification=emailVerificationRepository
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(
                        request.email(), Purpose.SIGNUP
                )//발송내역없으면 예외
                .orElseThrow(()-> new GeneralException(AuthErrorCode.EMAIL_VERIFICATION_NOT_FOUND));
        //인증번호 만료 시 예외
        if (emailVerification.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new GeneralException(AuthErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }

        //인증번호 불일치 시 예외
        if (!emailVerification.getCode().equals(request.code())) {
            throw new GeneralException(AuthErrorCode.EMAIL_VERIFICATION_CODE_NOT_MATCH);
        }
        emailVerification.verify();

        return new AuthMessageResponse("인증번호가 일치합니다");
    }

    //회원가입 완료 눌렀을때
    @Transactional
    public AuthMessageResponse signup(SignupRequest request){
        //중복검사
        if (userRepository.existsByEmail(request.email())){
            throw new GeneralException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        }

        boolean verified = emailVerificationRepository.existsByEmailAndPurposeAndVerifiedTrueAndExpiresAtAfter(
                request.email(),
                Purpose.SIGNUP,
                LocalDateTime.now()
        );

        if (!verified) {
            throw new GeneralException(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }

        //사용자 비번 암호
        String encodedPassword = passwordEncoder.encode(request.password());

        User user =new User(
                request.email(),
                encodedPassword,
                request.gender(),
                request.age(),
                request.job()
        );
        userRepository.save(user);

        return new AuthMessageResponse("회원가입이 완료되었습니다");
    }

    private String createVerificationCode() {
        Random random =new Random();
        int number = random.nextInt(1_000_000);
        return  String.format("%06d",number);
    }

    //로그인
    @Transactional
    public LoginResponse login(LoginRequest request){
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(()->new GeneralException(AuthErrorCode.LOGIN_FAILED));//이메일로 회원찾기

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new GeneralException(AuthErrorCode.LOGIN_FAILED);
        }//비번 불일치시 로그인 실패

        String accessToken= jwtProvider.createAccessToken(user.getId(), user.getEmail());//성공 시 토큰 생성
        String refreshToken= jwtProvider.createRefreshToken(user.getId(),user.getEmail());

        //한계정당 refreshToken 하나만 유지
        refreshTokenRepository.deleteAllByUser(user);

        RefreshToken savedRefreshToken=new RefreshToken(
                user,
                refreshToken,
                LocalDateTime.now().plus(Duration.ofMillis(jwtProvider.getRefreshTokenExpiration()))
        );
        refreshTokenRepository.save(savedRefreshToken);

        return  new LoginResponse(accessToken, refreshToken);
    }

    //refresh 매서드
    @Transactional(readOnly = true)
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

    //logout매서드
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

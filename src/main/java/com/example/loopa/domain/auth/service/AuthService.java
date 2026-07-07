package com.example.loopa.domain.auth.service;

import com.example.loopa.domain.auth.dto.request.EmailVerificationSendRequest;
import com.example.loopa.domain.auth.dto.request.EmailVerificationVerifyRequest;
import com.example.loopa.domain.auth.dto.request.SignupRequest;
import com.example.loopa.domain.auth.dto.response.AuthMessageResponse;
import com.example.loopa.domain.auth.entity.EmailVerification;
import com.example.loopa.domain.auth.entity.Purpose;
import com.example.loopa.domain.auth.repository.EmailVerificationRepository;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.config.PasswordConfig;
import com.example.loopa.global.error.code.AuthErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;

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

        //사용자 비번 암호화
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

}

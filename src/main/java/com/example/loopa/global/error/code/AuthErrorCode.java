package com.example.loopa.global.error.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    EMAIL_ALREADY_EXISTS("AUTH_001", "이미 가입된 이메일입니다.", HttpStatus.BAD_REQUEST),
    EMAIL_VERIFICATION_NOT_FOUND("AUTH_002", "인증번호 발송 내역이 없습니다.", HttpStatus.BAD_REQUEST),
    EMAIL_VERIFICATION_EXPIRED("AUTH_003", "인증번호가 만료되었습니다.", HttpStatus.BAD_REQUEST),
    EMAIL_VERIFICATION_CODE_NOT_MATCH("AUTH_004", "인증번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    EMAIL_NOT_VERIFIED("AUTH_005", "이메일 인증이 필요합니다.", HttpStatus.BAD_REQUEST),
    LOGIN_FAILED("AUTH_006", "이메일 또는 비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus status;
}

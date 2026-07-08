package com.example.loopa.global.error.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    EMAIL_ALREADY_EXISTS("AUTH_001", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    VERIFICATION_CODE_MISMATCH("AUTH_002", "인증번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    VERIFICATION_CODE_EXPIRED("AUTH_003", "인증번호가 만료되었습니다.", HttpStatus.BAD_REQUEST),
    EMAIL_NOT_VERIFIED("AUTH_004", "이메일 인증이 완료되지 않았습니다.", HttpStatus.BAD_REQUEST),
    LOGIN_FAILED("AUTH_005", "이메일 또는 비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("AUTH_006", "유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EMAIL_NOT_REGISTERED("AUTH_007", "등록되지 않은 이메일입니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;
}

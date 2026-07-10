package com.example.loopa.global.error.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    ALREADY_EXISTS("AUTH_001", "이미 사용 중인 이메일입니다.", HttpStatus.CONFLICT),
    VERIFICATION_CODE_MISMATCH("AUTH_002", "인증번호가 일치하지 않습니다.", HttpStatus.BAD_REQUEST),
    VERIFICATION_CODE_EXPIRED("AUTH_003", "인증번호가 만료되었습니다.", HttpStatus.BAD_REQUEST),
    EMAIL_NOT_VERIFIED("AUTH_004", "이메일 인증이 완료되지 않았습니다.", HttpStatus.BAD_REQUEST),
    LOGIN_FAILED("AUTH_005", "이메일 또는 비밀번호가 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("AUTH_006", "유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EMAIL_NOT_REGISTERED("AUTH_007", "등록되지 않은 이메일입니다.", HttpStatus.NOT_FOUND),
    EXPIRED_REFRESH_TOKEN("AUTH_008", "만료된 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),
    VERIFICATION_ATTEMPT_EXCEEDED("AUTH_009", "인증 시도 횟수를 초과했습니다. 인증번호를 재발송해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    EMAIL_RATE_LIMITED("AUTH_010", "잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus status;
}

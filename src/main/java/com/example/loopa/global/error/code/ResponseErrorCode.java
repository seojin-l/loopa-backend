package com.example.loopa.global.error.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ResponseErrorCode implements BaseErrorCode {

    REQUIRED_QUESTION_NOT_ANSWERED("RESPONSE_001", "필수 문항에 응답하지 않았습니다.", HttpStatus.BAD_REQUEST),
    ALREADY_PARTICIPATED("RESPONSE_002", "이미 참여한 설문입니다.", HttpStatus.CONFLICT),
    SURVEY_CLOSED("RESPONSE_003", "종료된 설문에는 참여할 수 없습니다.", HttpStatus.CONFLICT),
    SELF_RESPONSE("RESPONSE_004", "본인이 등록한 설문에는 참여할 수 없습니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus status;
}

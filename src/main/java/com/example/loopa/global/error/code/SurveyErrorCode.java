package com.example.loopa.global.error.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SurveyErrorCode implements BaseErrorCode {

    SURVEY_NOT_FOUND("SURVEY_001", "존재하지 않는 설문입니다.", HttpStatus.NOT_FOUND),
    INVALID_DATE_RANGE("SURVEY_002", "마감일은 시작일 이후여야 합니다.", HttpStatus.BAD_REQUEST),
    NO_QUESTIONS("SURVEY_003", "문항은 최소 1개 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    NO_OPTIONS_FOR_MC("SURVEY_004", "객관식 문항은 보기가 최소 1개 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    SHARED_SURVEY_DELETE("SURVEY_005", "공유된 설문은 삭제할 수 없습니다.", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;
}

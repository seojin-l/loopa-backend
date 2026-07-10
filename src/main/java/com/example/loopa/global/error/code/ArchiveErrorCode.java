package com.example.loopa.global.error.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ArchiveErrorCode implements BaseErrorCode {

    NO_VIEW_PERMISSION("ARCHIVE_001", "열람 권한이 없습니다.", HttpStatus.FORBIDDEN),
    ALREADY_SHARED("ARCHIVE_002", "이미 공유된 설문입니다.", HttpStatus.CONFLICT),
    NOT_CLOSED("ARCHIVE_003", "종료된 설문만 공유할 수 있습니다.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}

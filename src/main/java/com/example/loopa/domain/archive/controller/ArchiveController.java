package com.example.loopa.domain.archive.controller;

import com.example.loopa.domain.archive.service.ArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/archive")
@RequiredArgsConstructor
public class ArchiveController {

    private final ArchiveService archiveService;

    // ARCHIVE-01 목록                GET /archive/surveys
    // ARCHIVE-02 열람 정보            GET /archive/surveys/{id}
    // ARCHIVE-03 열람 구매            POST /archive/surveys/{id}/views
    // ARCHIVE-04 세부 결과            GET /archive/surveys/{id}/results
    // ARCHIVE-05 공유 가능한 내 설문   GET /archive/my-surveys
    // ARCHIVE-06 공유                POST /archive/shares
}

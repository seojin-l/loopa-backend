package com.example.loopa.domain.survey.controller;

import com.example.loopa.domain.survey.service.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    // SURVEY-01 목록         GET /surveys
    // SURVEY-02 상세         GET /surveys/{id}
    // SURVEY-03 문항 조회     GET /surveys/{id}/questions
    // SURVEY-04 생성         POST /surveys
    // SURVEY-05 삭제         DELETE /surveys/{id}
}

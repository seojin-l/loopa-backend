package com.example.loopa.domain.response.controller;

import com.example.loopa.domain.response.service.ResponseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/surveys/{surveyId}/responses")
@RequiredArgsConstructor
public class ResponseController {

    private final ResponseService responseService;

    // RESPONSE-01 응답 제출    POST /surveys/{surveyId}/responses
}

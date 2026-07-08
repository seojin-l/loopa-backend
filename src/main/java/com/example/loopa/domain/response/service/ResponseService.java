package com.example.loopa.domain.response.service;

import com.example.loopa.domain.response.repository.SurveyResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResponseService {

    private final SurveyResponseRepository surveyResponseRepository;
}

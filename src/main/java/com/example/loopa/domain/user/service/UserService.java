package com.example.loopa.domain.user.service;

import com.example.loopa.domain.survey.entity.Survey;
import com.example.loopa.domain.survey.repository.SurveyRepository;
import com.example.loopa.domain.user.dto.response.UserMeResponse;
import com.example.loopa.domain.user.dto.response.UserSurveyResponse;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.error.code.GlobalErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.loopa.domain.response.repository.SurveyResponseRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;


    public UserMeResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE));

        return UserMeResponse.from(user);
    }
    public List<UserSurveyResponse> getMySurveys(Long userId) {
        List<Survey> surveys = surveyRepository.findByCreatorIdAndIsDeletedFalseOrderByIdDesc(userId);

        if (surveys.isEmpty()) {
            return List.of();
        }

        List<Long> surveyIds = surveys.stream()
                .map(Survey::getId)
                .toList();

        Map<Long, Long> respondentCountMap = surveyResponseRepository.countBySurveyIds(surveyIds)
                .stream()
                .collect(Collectors.toMap(
                        SurveyResponseRepository.SurveyResponseCount::getSurveyId,
                        SurveyResponseRepository.SurveyResponseCount::getCount
                ));

        return surveys.stream()
                .map(survey -> UserSurveyResponse.from(
                        survey,
                        respondentCountMap.getOrDefault(survey.getId(), 0L)
                ))
                .toList();
    }
}

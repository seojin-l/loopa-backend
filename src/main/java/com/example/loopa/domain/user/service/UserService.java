package com.example.loopa.domain.user.service;

import com.example.loopa.domain.archive.entity.ArchiveView;
import com.example.loopa.domain.archive.repository.ArchiveViewRepository;
import com.example.loopa.domain.response.repository.SurveyResponseRepository;
import com.example.loopa.domain.survey.entity.Survey;
import com.example.loopa.domain.survey.repository.SurveyRepository;
import com.example.loopa.domain.user.dto.response.UserMeResponse;
import com.example.loopa.domain.user.dto.response.UserSurveyResponse;
import com.example.loopa.domain.user.dto.response.UserViewedSurveyResponse;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.common.CursorPageResponse;
import com.example.loopa.global.error.code.GlobalErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ArchiveViewRepository archiveViewRepository;

    public UserMeResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

        return UserMeResponse.from(user);
    }

    public CursorPageResponse<UserSurveyResponse> getMySurveys(Long userId, Long cursor, int size) {
        List<Survey> surveys = surveyRepository.findMySurveys(userId, cursor, size + 1);

        boolean hasNext = surveys.size() > size;
        List<Survey> page = hasNext ? surveys.subList(0, size) : surveys;

        List<Long> surveyIds = page.stream()
                .map(Survey::getId)
                .toList();

        Map<Long, Long> respondentCountMap = surveyIds.isEmpty()
                ? Map.of()
                : surveyResponseRepository.countBySurveyIds(surveyIds)
                        .stream()
                        .collect(Collectors.toMap(
                                SurveyResponseRepository.SurveyResponseCount::getSurveyId,
                                SurveyResponseRepository.SurveyResponseCount::getCount
                        ));

        List<UserSurveyResponse> items = page.stream()
                .map(survey -> UserSurveyResponse.from(
                        survey,
                        respondentCountMap.getOrDefault(survey.getId(), 0L)
                ))
                .toList();

        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext);
    }

    public CursorPageResponse<UserViewedSurveyResponse> getMyViewedSurveys(Long userId, Long cursor, int size) {
        List<ArchiveView> archiveViews = archiveViewRepository.findViewedSurveysByViewerId(userId, cursor, size + 1);

        boolean hasNext = archiveViews.size() > size;
        List<ArchiveView> page = hasNext ? archiveViews.subList(0, size) : archiveViews;

        List<Long> surveyIds = page.stream()
                .map(archiveView -> archiveView.getSurvey().getId())
                .toList();

        Map<Long, Long> respondentCountMap = surveyIds.isEmpty()
                ? Map.of()
                : surveyResponseRepository.countBySurveyIds(surveyIds)
                        .stream()
                        .collect(Collectors.toMap(
                                SurveyResponseRepository.SurveyResponseCount::getSurveyId,
                                SurveyResponseRepository.SurveyResponseCount::getCount
                        ));

        List<UserViewedSurveyResponse> items = page.stream()
                .map(archiveView -> UserViewedSurveyResponse.from(
                        archiveView,
                        respondentCountMap.getOrDefault(archiveView.getSurvey().getId(), 0L)
                ))
                .toList();

        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext);
    }
}

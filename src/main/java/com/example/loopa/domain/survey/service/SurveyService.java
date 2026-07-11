package com.example.loopa.domain.survey.service;

import com.example.loopa.domain.response.repository.SurveyResponseRepository;
import com.example.loopa.domain.survey.dto.request.SurveyCreateRequest;
import com.example.loopa.domain.survey.dto.response.*;
import com.example.loopa.domain.survey.dto.response.SurveyDetailResponse.QuestionCountDto;
import com.example.loopa.domain.survey.entity.*;
import com.example.loopa.domain.survey.repository.SurveyRepository;
import com.example.loopa.domain.token.entity.TokenTxType;
import com.example.loopa.domain.token.service.TokenService;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.common.CursorPageResponse;
import com.example.loopa.global.error.code.GlobalErrorCode;
import com.example.loopa.global.error.code.SurveyErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // SURVEY-01 목록
    public CursorPageResponse<SurveyListResponse> getList(Long userId, String category,
                                                           String keyword, Long cursor, int size) {
        Category cat = null;
        if (category != null && !category.isBlank()) {
            try {
                cat = Category.valueOf(category);
            } catch (IllegalArgumentException e) {
                throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
            }
        }

        //전체카테고리 직업관련 정렬
        List<Survey> surveys;

        boolean isMainFirstPageAllCategory =
                cat == null
                        && cursor == null
                        && size == 4
                        && userId != null;

        if (isMainFirstPageAllCategory) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

            surveys = surveyRepository.findRecommendedSurveyList(
                    LocalDate.now(),
                    userId,
                    user.getJob().name(),
                    keyword,
                    cursor,
                    size + 1
            );
        } else {
            surveys = surveyRepository.findSurveyList(
                    LocalDate.now(),
                    userId,
                    cat == null ? null : cat.name(),
                    keyword,
                    cursor,
                    size + 1
            );
        }
        boolean hasNext = surveys.size() > size;
        List<Survey> page = hasNext ? surveys.subList(0, size) : surveys;

        List<SurveyListResponse> items = page.stream().map(s -> {
            int mc = countByType(s, QuestionType.MULTIPLE_CHOICE);
            int sa = countByType(s, QuestionType.SUBJECTIVE);
            return new SurveyListResponse(
                    s.getId(), s.getTitle(), s.getCategory().name(), s.getTarget(),
                    s.getEstimatedMinutes(), mc * 1 + sa * 2,
                    s.getCreatedAt().toLocalDate().format(DATE_FMT));
        }).toList();

        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext);
    }

    // SURVEY-02 상세
    public SurveyDetailResponse getDetail(Long surveyId) {
        Survey s = findSurveyOrThrow(surveyId);

        int mc = countByType(s, QuestionType.MULTIPLE_CHOICE);
        int sa = countByType(s, QuestionType.SUBJECTIVE);
        String status = s.isClosed() ? "CLOSED" : "IN_PROGRESS";
        long respondentCount = surveyResponseRepository.countBySurveyId(surveyId);

        return new SurveyDetailResponse(
                s.getId(), s.getTitle(), s.getCategory().name(), s.getTarget(),
                s.getDescription(), s.getEstimatedMinutes(),
                s.getStartDate().format(DATE_FMT), s.getEndDate().format(DATE_FMT),
                status, new QuestionCountDto(mc, sa, mc + sa),
                mc * 1 + sa * 2, respondentCount,
                s.getCreatedAt().toLocalDate().format(DATE_FMT));
    }

    // SURVEY-03 문항
    public SurveyQuestionsResponse getQuestions(Long surveyId) {
        Survey s = findSurveyOrThrow(surveyId);

        List<SurveyQuestionsResponse.QuestionDto> questions = s.getQuestions().stream()
                .sorted((a, b) -> a.getQuestionOrder().compareTo(b.getQuestionOrder()))
                .map(q -> new SurveyQuestionsResponse.QuestionDto(
                        q.getId(), q.getQuestionOrder(), q.getType().name(),
                        q.getContent(), q.getIsRequired(), q.getAllowMultiple(),
                        q.getOptions().stream()
                                .sorted((a, b) -> a.getOptionOrder().compareTo(b.getOptionOrder()))
                                .map(o -> new SurveyQuestionsResponse.OptionDto(
                                        o.getId(), o.getOptionOrder(), o.getContent()))
                                .toList()))
                .toList();

        return new SurveyQuestionsResponse(s.getId(), questions);
    }

    // SURVEY-04 생성
    @Transactional
    public SurveyCreateResponse create(Long userId, SurveyCreateRequest req) {
        if (!req.endDate().isAfter(req.startDate())) {
            throw new GeneralException(SurveyErrorCode.INVALID_DATE_RANGE);
        }
        if (req.questions().isEmpty()) {
            throw new GeneralException(SurveyErrorCode.NO_QUESTIONS);
        }

        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));

        Category category;
        try {
            category = Category.valueOf(req.category());
        } catch (IllegalArgumentException e) {
            throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        Survey survey = new Survey(creator, req.title(), req.description(), req.target(),
                category, req.estimatedMinutes(), req.startDate(), req.endDate());

        int mc = 0, sa = 0;
        for (SurveyCreateRequest.QuestionRequest qr : req.questions()) {
            QuestionType type;
            try {
                type = QuestionType.valueOf(qr.type());
            } catch (IllegalArgumentException e) {
                throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
            }

            if (type == QuestionType.MULTIPLE_CHOICE) {
                if (qr.options() == null || qr.options().size() < 2) {
                    throw new GeneralException(SurveyErrorCode.NO_OPTIONS_FOR_MC);
                }
                mc++;
            } else {
                sa++;
            }

            Boolean allowMultiple = qr.allowMultiple() != null ? qr.allowMultiple() : false;
            Question question = new Question(survey, qr.order(), type,
                    qr.content(), qr.isRequired(), allowMultiple);
            survey.addQuestion(question);

            if (qr.options() != null) {
                for (SurveyCreateRequest.OptionRequest or : qr.options()) {
                    QuestionOption option = new QuestionOption(question, or.order(), or.content());
                    question.addOption(option);
                }
            }
        }

        int cost = 10 + mc * 3 + sa * 5;

        surveyRepository.save(survey);

        int balanceAfter = tokenService.record(userId, TokenTxType.SURVEY_CREATE,
                -cost, survey.getId(), null);

        return new SurveyCreateResponse(
                survey.getId(), survey.getTitle(),
                new QuestionCountDto(mc, sa, mc + sa),
                cost, balanceAfter, "IN_PROGRESS",
                survey.getCreatedAt().toLocalDate().format(DATE_FMT));
    }

    // SURVEY-05 삭제
    @Transactional
    public SurveyDeleteResponse delete(Long userId, Long surveyId) {
        Survey survey = findSurveyOrThrow(surveyId);

        if (!survey.getCreator().getId().equals(userId)) {
            throw new GeneralException(GlobalErrorCode.FORBIDDEN);
        }
        if (survey.getSharedToArchive()) {
            throw new GeneralException(SurveyErrorCode.SHARED_SURVEY_DELETE);
        }

        survey.softDelete();

        return new SurveyDeleteResponse(survey.getId(), true);
    }

    private Survey findSurveyOrThrow(Long surveyId) {
        return surveyRepository.findByIdAndIsDeletedFalse(surveyId)
                .orElseThrow(() -> new GeneralException(SurveyErrorCode.SURVEY_NOT_FOUND));
    }

    private int countByType(Survey survey, QuestionType type) {
        return (int) survey.getQuestions().stream()
                .filter(q -> q.getType() == type)
                .count();
    }

}

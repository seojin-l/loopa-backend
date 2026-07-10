package com.example.loopa.domain.response.service;

import com.example.loopa.domain.response.dto.request.ResponseSubmitRequest;
import com.example.loopa.domain.response.dto.request.ResponseSubmitRequest.AnswerRequest;
import com.example.loopa.domain.response.dto.response.ResponseSubmitResponse;
import com.example.loopa.domain.response.dto.response.ResponseSubmitResponse.SkippedDto;
import com.example.loopa.domain.response.dto.response.ResponseSubmitResponse.TokenRewardDto;
import com.example.loopa.domain.response.entity.Answer;
import com.example.loopa.domain.response.entity.AnswerOption;
import com.example.loopa.domain.response.entity.SurveyResponse;
import com.example.loopa.domain.response.repository.SurveyResponseRepository;
import com.example.loopa.domain.survey.entity.Question;
import com.example.loopa.domain.survey.entity.QuestionOption;
import com.example.loopa.domain.survey.entity.QuestionType;
import com.example.loopa.domain.survey.entity.Survey;
import com.example.loopa.domain.survey.repository.SurveyRepository;
import com.example.loopa.domain.token.entity.TokenTxType;
import com.example.loopa.domain.token.repository.TokenTransactionRepository;
import com.example.loopa.domain.token.service.TokenService;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.error.code.GlobalErrorCode;
import com.example.loopa.global.error.code.ResponseErrorCode;
import com.example.loopa.global.error.code.SurveyErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResponseService {

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final TokenTransactionRepository tokenTransactionRepository;

    @Transactional
    public ResponseSubmitResponse submit(Long userId, Long surveyId, ResponseSubmitRequest req) {

        // 1. 설문 조회
        Survey survey = surveyRepository.findByIdAndIsDeletedFalse(surveyId)
                .orElseThrow(() -> new GeneralException(SurveyErrorCode.SURVEY_NOT_FOUND));

        // 종료 확인
        if (survey.isClosed()) {
            throw new GeneralException(ResponseErrorCode.SURVEY_CLOSED);
        }

        // 2. 주체 판별 + 중복 확인
        boolean isGuest = (userId == null);
        User respondent = null;

        if (!isGuest) {
            // 회원: 자가응답 차단
            if (survey.getCreator().getId().equals(userId)) {
                throw new GeneralException(ResponseErrorCode.SELF_RESPONSE);
            }
            // 회원: 중복 참여 확인
            if (surveyResponseRepository.existsBySurveyIdAndRespondentId(surveyId, userId)) {
                throw new GeneralException(ResponseErrorCode.ALREADY_PARTICIPATED);
            }
            respondent = userRepository.findById(userId)
                    .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));
        } else {
            // 게스트: guestKey 필수
            if (req.guestKey() == null || req.guestKey().isBlank()) {
                throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
            }
            // 게스트: 중복 참여 확인
            if (surveyResponseRepository.existsBySurveyIdAndGuestKey(surveyId, req.guestKey())) {
                throw new GeneralException(ResponseErrorCode.ALREADY_PARTICIPATED);
            }
        }

        // 3. 문항 검증
        List<Question> questions = survey.getQuestions();
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        // 제출된 답변의 questionId를 수집
        Set<Long> answeredIds = req.answers().stream()
                .map(AnswerRequest::questionId)
                .collect(Collectors.toSet());

        // 필수 문항 누락 확인
        for (Question q : questions) {
            if (q.getIsRequired() && !answeredIds.contains(q.getId())) {
                throw new GeneralException(ResponseErrorCode.REQUIRED_QUESTION_NOT_ANSWERED);
            }
        }

        // 4. 응답 저장
        SurveyResponse response = isGuest
                ? SurveyResponse.ofGuest(survey, req.guestKey())
                : SurveyResponse.ofMember(survey, respondent);

        surveyResponseRepository.save(response);

        int answeredMc = 0, answeredSa = 0;

        for (AnswerRequest ar : req.answers()) {
            Question question = questionMap.get(ar.questionId());
            if (question == null) {
                throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
            }

            if (question.getType() == QuestionType.MULTIPLE_CHOICE) {
                // 객관식: selectedOptionIds 필수
                if (ar.selectedOptionIds() == null || ar.selectedOptionIds().isEmpty()) {
                    throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
                }
                // 단일선택인데 2개 이상
                if (!question.getAllowMultiple() && ar.selectedOptionIds().size() > 1) {
                    throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
                }

                // optionId 유효성 확인
                Map<Long, QuestionOption> optionMap = question.getOptions().stream()
                        .collect(Collectors.toMap(QuestionOption::getId, o -> o));

                Answer answer = new Answer(response, question, null);
                response.getAnswers().add(answer);

                for (Long optionId : ar.selectedOptionIds()) {
                    QuestionOption option = optionMap.get(optionId);
                    if (option == null) {
                        throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
                    }
                    AnswerOption ao = new AnswerOption(answer, option);
                    answer.addSelectedOption(ao);
                }
                answeredMc++;

            } else {
                // 주관식: answerText 필수
                if (ar.answerText() == null || ar.answerText().isBlank()) {
                    throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
                }
                Answer answer = new Answer(response, question, ar.answerText());
                response.getAnswers().add(answer);
                answeredSa++;
            }
        }

        // 5. 회원만 토큰 적립
        int totalMc = (int) questions.stream().filter(q -> q.getType() == QuestionType.MULTIPLE_CHOICE).count();
        int totalSa = (int) questions.stream().filter(q -> q.getType() == QuestionType.SUBJECTIVE).count();
        int maxToken = totalMc * 1 + totalSa * 2;
        int earned = answeredMc * 1 + answeredSa * 2;

        Integer tokenBalanceAfter = null;
        TokenRewardDto tokenReward = null;

        if (!isGuest) {
            tokenBalanceAfter = tokenService.record(userId, TokenTxType.SURVEY_PARTICIPATE,
                    earned, surveyId, response.getId());
            response.updateEarnedToken(earned);

            int skippedMc = totalMc - answeredMc;
            int skippedSa = totalSa - answeredSa;
            tokenReward = new TokenRewardDto(maxToken,
                    new SkippedDto(skippedMc, skippedSa), earned);
        }

        // 6. 50명 보너스
        long respondentCount = surveyResponseRepository.countBySurveyId(surveyId);
        if (respondentCount >= 50) {
            boolean alreadyGiven = tokenTransactionRepository
                    .existsByRelatedSurveyIdAndType(surveyId, TokenTxType.RESPONDENT_50_BONUS);
            if (!alreadyGiven) {
                tokenService.record(survey.getCreator().getId(),
                        TokenTxType.RESPONDENT_50_BONUS, 10, surveyId, null);
            }
        }

        return new ResponseSubmitResponse(
                response.getId(), surveyId, isGuest,
                tokenReward, tokenBalanceAfter,
                response.getSubmittedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}

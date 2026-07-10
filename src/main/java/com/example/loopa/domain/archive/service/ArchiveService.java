package com.example.loopa.domain.archive.service;

import com.example.loopa.domain.archive.dto.request.ArchiveShareRequest;
import com.example.loopa.domain.archive.dto.response.*;
import com.example.loopa.domain.archive.dto.response.ArchiveResultResponse.*;
import com.example.loopa.domain.archive.entity.ArchiveView;
import com.example.loopa.domain.archive.repository.ArchiveViewRepository;
import com.example.loopa.domain.response.repository.SurveyResponseRepository;
import com.example.loopa.domain.survey.dto.response.SurveyDetailResponse.QuestionCountDto;
import com.example.loopa.domain.survey.entity.*;
import com.example.loopa.domain.survey.repository.SurveyRepository;
import com.example.loopa.domain.token.entity.TokenTxType;
import com.example.loopa.domain.token.service.TokenService;
import com.example.loopa.domain.user.entity.User;
import com.example.loopa.domain.user.repository.UserRepository;
import com.example.loopa.global.common.CursorPageResponse;
import com.example.loopa.global.error.code.ArchiveErrorCode;
import com.example.loopa.global.error.code.GlobalErrorCode;
import com.example.loopa.global.error.code.SurveyErrorCode;
import com.example.loopa.global.error.exception.GeneralException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveService {

    private final ArchiveViewRepository archiveViewRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EntityManager em;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int VIEW_COST = 15;

    // ARCHIVE-01 아카이브 설문 목록
    public CursorPageResponse<ArchiveListResponse> getList(String category, String keyword,
                                                            Long cursor, int size) {
        Category cat = parseCategory(category);

        List<Survey> surveys = surveyRepository.findArchiveList(cat == null ? null : cat.name(), keyword, cursor, size + 1);

        boolean hasNext = surveys.size() > size;
        List<Survey> page = hasNext ? surveys.subList(0, size) : surveys;

        List<ArchiveListResponse> items = page.stream().map(s -> {
            long respondentCount = surveyResponseRepository.countBySurveyId(s.getId());
            return new ArchiveListResponse(
                    s.getId(), s.getTitle(), s.getCategory().name(), s.getTarget(),
                    respondentCount, s.getCreatedAt().toLocalDate().format(DATE_FMT));
        }).toList();

        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext);
    }

    // ARCHIVE-02 열람 정보 조회
    public ArchiveViewInfoResponse getViewInfo(Long userId, Long surveyId) {
        Survey s = findSharedSurveyOrThrow(surveyId);
        User user = findUserOrThrow(userId);

        int mc = countByType(s, QuestionType.MULTIPLE_CHOICE);
        int sa = countByType(s, QuestionType.SUBJECTIVE);
        long respondentCount = surveyResponseRepository.countBySurveyId(surveyId);

        boolean alreadyViewed = s.getCreator().getId().equals(userId)
                || archiveViewRepository.existsByViewerIdAndSurveyId(userId, surveyId);

        return new ArchiveViewInfoResponse(
                s.getId(), s.getTitle(), s.getCategory().name(), s.getTarget(),
                s.getDescription(),
                s.getStartDate().format(DATE_FMT), s.getEndDate().format(DATE_FMT),
                respondentCount, new QuestionCountDto(mc, sa, mc + sa),
                s.getCreatedAt().toLocalDate().format(DATE_FMT),
                VIEW_COST, alreadyViewed, user.getTokenBalance());
    }

    // ARCHIVE-03 열람 구매
    @Transactional
    public ArchiveViewPurchaseResponse purchaseView(Long userId, Long surveyId) {
        Survey s = findSharedSurveyOrThrow(surveyId);
        User user = findUserOrThrow(userId);

        // 본인 설문 or 이미 열람권 보유 -> 과금 없이 성공
        if (s.getCreator().getId().equals(userId)) {
            return buildFreePurchaseResponse(null, surveyId, user.getTokenBalance());
        }

        Optional<ArchiveView> existing = archiveViewRepository.findByViewerIdAndSurveyId(userId, surveyId);
        if (existing.isPresent()) {
            ArchiveView view = existing.get();
            return buildFreePurchaseResponse(view.getId(), surveyId, user.getTokenBalance());
        }

        // 신규 구매
        int balanceBefore = user.getTokenBalance();
        int balanceAfter = tokenService.record(userId, TokenTxType.RESULT_VIEW, -VIEW_COST, surveyId, null);

        ArchiveView view = new ArchiveView(user, s, VIEW_COST);
        archiveViewRepository.save(view);

        return new ArchiveViewPurchaseResponse(
                view.getId(), surveyId, VIEW_COST,
                balanceBefore, balanceAfter,
                view.getViewedAt().toString());
    }

    // ARCHIVE-04 세부 결과 조회 (크로스탭 필터)
    public ArchiveResultResponse getResults(Long userId, Long surveyId, List<Long> filterOptionIds) {
        Survey s = findSharedSurveyOrThrow(surveyId);

        // 권한: 본인 설문 or 열람권 보유
        boolean isOwner = s.getCreator().getId().equals(userId);
        if (!isOwner && !archiveViewRepository.existsByViewerIdAndSurveyId(userId, surveyId)) {
            throw new GeneralException(ArchiveErrorCode.NO_VIEW_PERMISSION);
        }

        int mc = countByType(s, QuestionType.MULTIPLE_CHOICE);
        int sa = countByType(s, QuestionType.SUBJECTIVE);
        long totalRespondentCount = surveyResponseRepository.countBySurveyId(surveyId);

        // 필터 검증
        List<Long> appliedFilters = (filterOptionIds != null && !filterOptionIds.isEmpty())
                ? filterOptionIds : Collections.emptyList();

        if (!appliedFilters.isEmpty()) {
            validateFilterOptions(s, appliedFilters);
        }

        // 필터된 응답자 집합 구하기
        Set<Long> filteredResponseIds;
        long filteredRespondentCount;

        if (appliedFilters.isEmpty()) {
            filteredResponseIds = null; // null = 전체
            filteredRespondentCount = totalRespondentCount;
        } else {
            filteredResponseIds = findFilteredResponseIds(surveyId, appliedFilters);
            filteredRespondentCount = filteredResponseIds.size();
        }

        // 문항별 집계
        List<Question> questions = s.getQuestions().stream()
                .sorted(Comparator.comparingInt(Question::getQuestionOrder))
                .toList();

        List<QuestionResultDto> results = questions.stream().map(q -> {
            if (q.getType() == QuestionType.MULTIPLE_CHOICE) {
                return buildMcResult(q, surveyId, filteredResponseIds, filteredRespondentCount);
            } else {
                return buildSaResult(q, surveyId, filteredResponseIds);
            }
        }).toList();

        SurveyInfoDto surveyInfo = new SurveyInfoDto(
                s.getId(), s.getTitle(), s.getCategory().name(), s.getTarget(),
                s.getDescription(),
                s.getStartDate().format(DATE_FMT), s.getEndDate().format(DATE_FMT),
                totalRespondentCount, new QuestionCountDto(mc, sa, mc + sa),
                s.getCreatedAt().toLocalDate().format(DATE_FMT));

        return new ArchiveResultResponse(surveyInfo, appliedFilters, filteredRespondentCount, results);
    }

    // ARCHIVE-05 공유 가능한 내 설문 목록
    public CursorPageResponse<ArchiveMyShareableResponse> getMyShareableSurveys(Long userId,
                                                                                  Long cursor, int size) {
        List<Survey> surveys = surveyRepository.findShareableSurveys(
                userId, LocalDate.now(), cursor, size + 1);

        boolean hasNext = surveys.size() > size;
        List<Survey> page = hasNext ? surveys.subList(0, size) : surveys;

        List<ArchiveMyShareableResponse> items = page.stream().map(s -> {
            long respondentCount = surveyResponseRepository.countBySurveyId(s.getId());
            return new ArchiveMyShareableResponse(
                    s.getId(), s.getTitle(), s.getCategory().name(), s.getTarget(),
                    respondentCount, "CLOSED", s.getSharedToArchive(),
                    s.getCreatedAt().toLocalDate().format(DATE_FMT));
        }).toList();

        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext);
    }

    // ARCHIVE-06 설문 공유
    @Transactional
    public ArchiveShareResponse share(Long userId, ArchiveShareRequest request) {
        User user = findUserOrThrow(userId);
        int balanceBefore = user.getTokenBalance();

        List<Survey> surveys = new ArrayList<>();
        for (Long surveyId : request.surveyIds()) {
            Survey s = surveyRepository.findByIdAndIsDeletedFalse(surveyId)
                    .orElseThrow(() -> new GeneralException(SurveyErrorCode.SURVEY_NOT_FOUND));

            if (!s.getCreator().getId().equals(userId)) {
                throw new GeneralException(GlobalErrorCode.FORBIDDEN);
            }
            if (!s.isClosed()) {
                throw new GeneralException(ArchiveErrorCode.NOT_CLOSED);
            }
            if (s.getSharedToArchive()) {
                throw new GeneralException(ArchiveErrorCode.ALREADY_SHARED);
            }

            surveys.add(s);
        }

        int balanceAfter = balanceBefore;
        for (Survey s : surveys) {
            s.shareToArchive();
            balanceAfter = tokenService.record(userId, TokenTxType.RESULT_SHARE, +10, s.getId(), null);
        }

        int sharedCount = surveys.size();
        return new ArchiveShareResponse(
                sharedCount, 10, sharedCount * 10,
                balanceBefore, balanceAfter);
    }

    // --- private helpers ---

    private Survey findSharedSurveyOrThrow(Long surveyId) {
        Survey s = surveyRepository.findByIdAndIsDeletedFalse(surveyId)
                .orElseThrow(() -> new GeneralException(SurveyErrorCode.SURVEY_NOT_FOUND));
        if (!s.getSharedToArchive()) {
            throw new GeneralException(SurveyErrorCode.SURVEY_NOT_FOUND);
        }
        return s;
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));
    }

    private Category parseCategory(String category) {
        if (category == null || category.isBlank()) return null;
        try {
            return Category.valueOf(category);
        } catch (IllegalArgumentException e) {
            throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private int countByType(Survey survey, QuestionType type) {
        return (int) survey.getQuestions().stream()
                .filter(q -> q.getType() == type)
                .count();
    }

    private ArchiveViewPurchaseResponse buildFreePurchaseResponse(Long viewId, Long surveyId, int balance) {
        return new ArchiveViewPurchaseResponse(viewId, surveyId, 0, balance, balance,
                java.time.LocalDateTime.now().toString());
    }

    private void validateFilterOptions(Survey survey, List<Long> filterOptionIds) {
        // 이 설문의 객관식 보기 ID 집합
        Set<Long> validOptionIds = survey.getQuestions().stream()
                .filter(q -> q.getType() == QuestionType.MULTIPLE_CHOICE)
                .flatMap(q -> q.getOptions().stream())
                .map(QuestionOption::getId)
                .collect(Collectors.toSet());

        for (Long optionId : filterOptionIds) {
            if (!validOptionIds.contains(optionId)) {
                throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);
            }
        }
    }

    private Set<Long> findFilteredResponseIds(Long surveyId, List<Long> filterOptionIds) {
        // 지정 보기를 모두 선택한 응답자(response) 집합 (AND 조건)
        String jpql = "SELECT ao.answer.response.id " +
                "FROM AnswerOption ao " +
                "WHERE ao.answer.response.survey.id = :surveyId " +
                "AND ao.option.id IN :filterOptionIds " +
                "GROUP BY ao.answer.response.id " +
                "HAVING COUNT(DISTINCT ao.option.id) = :filterSize";

        List<Long> ids = em.createQuery(jpql, Long.class)
                .setParameter("surveyId", surveyId)
                .setParameter("filterOptionIds", filterOptionIds)
                .setParameter("filterSize", (long) filterOptionIds.size())
                .getResultList();

        return new HashSet<>(ids);
    }

    private QuestionResultDto buildMcResult(Question q, Long surveyId,
                                             Set<Long> filteredResponseIds,
                                             long filteredRespondentCount) {
        List<QuestionOption> options = q.getOptions().stream()
                .sorted(Comparator.comparingInt(QuestionOption::getOptionOrder))
                .toList();

        List<OptionResultDto> optionResults = options.stream().map(opt -> {
            long count;
            if (filteredResponseIds == null) {
                // 전체 집계
                String jpql = "SELECT COUNT(ao) FROM AnswerOption ao " +
                        "WHERE ao.option.id = :optionId " +
                        "AND ao.answer.response.survey.id = :surveyId";
                count = em.createQuery(jpql, Long.class)
                        .setParameter("optionId", opt.getId())
                        .setParameter("surveyId", surveyId)
                        .getSingleResult();
            } else {
                if (filteredResponseIds.isEmpty()) {
                    count = 0;
                } else {
                    String jpql = "SELECT COUNT(ao) FROM AnswerOption ao " +
                            "WHERE ao.option.id = :optionId " +
                            "AND ao.answer.response.id IN :responseIds";
                    count = em.createQuery(jpql, Long.class)
                            .setParameter("optionId", opt.getId())
                            .setParameter("responseIds", filteredResponseIds)
                            .getSingleResult();
                }
            }

            double percentage = filteredRespondentCount > 0
                    ? Math.round(count * 1000.0 / filteredRespondentCount) / 10.0
                    : 0.0;

            return new OptionResultDto(opt.getId(), opt.getContent(), count, percentage);
        }).toList();

        return new QuestionResultDto(
                q.getId(), q.getQuestionOrder(), q.getType().name(),
                q.getContent(), optionResults, null);
    }

    private QuestionResultDto buildSaResult(Question q, Long surveyId,
                                             Set<Long> filteredResponseIds) {
        List<String> answers;
        if (filteredResponseIds == null) {
            String jpql = "SELECT a.answerText FROM Answer a " +
                    "WHERE a.question.id = :questionId " +
                    "AND a.response.survey.id = :surveyId " +
                    "AND a.answerText IS NOT NULL";
            answers = em.createQuery(jpql, String.class)
                    .setParameter("questionId", q.getId())
                    .setParameter("surveyId", surveyId)
                    .getResultList();
        } else {
            if (filteredResponseIds.isEmpty()) {
                answers = Collections.emptyList();
            } else {
                String jpql = "SELECT a.answerText FROM Answer a " +
                        "WHERE a.question.id = :questionId " +
                        "AND a.response.id IN :responseIds " +
                        "AND a.answerText IS NOT NULL";
                answers = em.createQuery(jpql, String.class)
                        .setParameter("questionId", q.getId())
                        .setParameter("responseIds", filteredResponseIds)
                        .getResultList();
            }
        }

        return new QuestionResultDto(
                q.getId(), q.getQuestionOrder(), q.getType().name(),
                q.getContent(), null, answers);
    }
}

# Response 도메인 개발 기록

---

## 1. Response 도메인이 뭔가?

설문에 대한 **응답을 제출**하는 도메인입니다. API는 딱 1개(`POST /surveys/{surveyId}/responses`)지만, Loopa에서 **가장 복잡한 로직**이 들어있습니다.

왜 복잡한가:
- 회원과 게스트를 동시에 처리해야 함
- 문항별 유형(객관식/주관식)에 따라 검증 방식이 다름
- 응답 저장 + 토큰 적립 + 50명 보너스까지 **한 트랜잭션**에서 처리
- 중복 응답을 코드 + DB 양쪽에서 방어해야 함

---

## 2. 전체 흐름 한눈에 보기

```
클라이언트 → POST /surveys/101/responses
         → ResponseController.submit()
         → ResponseService.submit()
             → ① 설문 조회 + 종료 확인
             → ② 회원/게스트 판별 + 자가응답·중복 차단
             → ③ 문항 검증 (필수 누락, 유형 일치, 보기 유효성)
             → ④ 응답 저장 (SurveyResponse + Answer + AnswerOption)
             → ⑤ 회원만 토큰 적립 (TokenService 호출)
             → ⑥ 50명 보너스 확인 + 지급
         ← 응답 결과 반환
```

각 단계가 왜 필요하고, 없으면 어떻게 되는지 아래에서 설명합니다.

---

## 3. 각 단계 상세 설명

### ① 설문 조회 + 종료 확인

```java
Survey survey = surveyRepository.findByIdAndIsDeletedFalse(surveyId)
        .orElseThrow(() -> new GeneralException(SurveyErrorCode.SURVEY_NOT_FOUND));

if (survey.isClosed()) {
    throw new GeneralException(ResponseErrorCode.SURVEY_CLOSED);
}
```

**왜 필요한가:**
- 삭제된 설문이나 존재하지 않는 설문에 응답이 저장되면 안 됨
- 마감일이 지난 설문에 응답이 들어오면 결과 집계가 왜곡됨 (마감 후에도 계속 응답이 쌓임)

**없으면?**
- 삭제된 설문에 응답이 저장되어 DB에 고아 데이터가 생김
- 마감된 설문에 무한히 응답이 추가되어 결과가 계속 바뀜

---

### ② 회원/게스트 판별 + 자가응답·중복 차단

```java
boolean isGuest = (userId == null);
```

userId가 없으면(헤더에 인증 정보 없음) 게스트입니다. 이후 **회원과 게스트를 완전히 다르게 처리**합니다.

#### 회원인 경우

```java
// 자기 설문에 응답 차단
if (survey.getCreator().getId().equals(userId)) {
    throw new GeneralException(ResponseErrorCode.SELF_RESPONSE);  // RESPONSE_004
}

// 이미 참여했는지 확인
if (surveyResponseRepository.existsBySurveyIdAndRespondentId(surveyId, userId)) {
    throw new GeneralException(ResponseErrorCode.ALREADY_PARTICIPATED);  // RESPONSE_002
}
```

**자가응답 차단 — 왜 필요한가?**
설문을 만든 사람이 자기 설문에 응답하면:
- 응답자 수가 부풀려져서 50명 보너스를 부정하게 받을 수 있음
- 자기가 원하는 방향으로 결과를 왜곡할 수 있음

**중복 참여 차단 — 왜 코드와 DB 양쪽에서?**
코드(`existsBy...`)로 먼저 확인하는 건 **사용자에게 친절한 에러 메시지**를 주기 위해서입니다. 하지만 코드 검증만으로는 **동시 요청**을 막을 수 없습니다:

```
[요청 A] exists? → false (아직 없음)     [요청 B] exists? → false (아직 없음)
[요청 A] save!                            [요청 B] save!  ← 둘 다 저장됨!
```

그래서 DB에 `UNIQUE(survey_id, respondent_id)` 제약이 걸려있어서, 두 번째 save가 실행되면 DB가 거부합니다. 이때 `DataIntegrityViolationException`이 터지고, `GlobalExceptionHandler`가 잡아서 에러 응답을 반환합니다.

#### 게스트인 경우

```java
// guestKey 필수
if (req.guestKey() == null || req.guestKey().isBlank()) {
    throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);  // COMMON_400
}

// 같은 세션키로 중복 참여 확인
if (surveyResponseRepository.existsBySurveyIdAndGuestKey(surveyId, req.guestKey())) {
    throw new GeneralException(ResponseErrorCode.ALREADY_PARTICIPATED);  // RESPONSE_002
}
```

**guestKey가 뭔가?**
게스트는 로그인을 안 했으니 userId가 없습니다. 그러면 "누가 이미 응답했는지"를 구분할 방법이 없습니다. 그래서 클라이언트(앱)가 UUID를 하나 만들어서 세션에 저장하고, 응답할 때 보내줍니다.

```
앱 접속 → UUID 생성: "b3f1c2a4-7d8e-..."  ← 세션에 보관
응답 제출 → guestKey: "b3f1c2a4-7d8e-..."
같은 설문 또 응답 → UNIQUE(survey_id, guest_key) 위반 → 차단
```

**한계:** 시크릿 창을 열거나 다른 기기에서 접속하면 새 UUID가 생기므로 재참여 가능합니다. 이건 의도된 타협입니다 — 게스트에게 완벽한 중복 방지는 불가능.

---

### ③ 문항 검증

응답 데이터가 유효한지 확인합니다. 3가지를 검증합니다.

#### 검증 1: 필수 문항 누락

```java
for (Question q : questions) {
    if (q.getIsRequired() && !answeredIds.contains(q.getId())) {
        throw new GeneralException(ResponseErrorCode.REQUIRED_QUESTION_NOT_ANSWERED);  // RESPONSE_001
    }
}
```

`isRequired=true`인 문항인데 클라이언트가 답변을 안 보냈으면 거부. 선택 문항(`isRequired=false`)은 건너뛸 수 있음.

**없으면?** 필수 문항을 건너뛰고 제출 가능해져서, 설문 작성자가 꼭 받고 싶은 데이터를 못 받게 됨.

#### 검증 2: questionId가 이 설문 소속인지

```java
Question question = questionMap.get(ar.questionId());
if (question == null) {
    throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);  // COMMON_400
}
```

설문 101의 문항인데 설문 999의 questionId를 보내면 거부.

**없으면?** 다른 설문의 문항에 대한 답변이 이 설문의 응답에 섞여 들어가서 데이터 정합성이 깨짐.

#### 검증 3: 유형별 데이터 확인

**객관식:**
```java
// selectedOptionIds가 있어야 함
if (ar.selectedOptionIds() == null || ar.selectedOptionIds().isEmpty()) → COMMON_400

// 단일선택인데 2개 이상 선택
if (!question.getAllowMultiple() && ar.selectedOptionIds().size() > 1) → COMMON_400

// 선택한 optionId가 이 문항의 보기인지
QuestionOption option = optionMap.get(optionId);
if (option == null) → COMMON_400
```

**주관식:**
```java
// answerText가 있어야 함
if (ar.answerText() == null || ar.answerText().isBlank()) → COMMON_400
```

**없으면?**
- 객관식에 `answerText`를 보내거나, 주관식에 `selectedOptionIds`를 보내는 잘못된 데이터가 저장됨
- 다른 문항의 보기 ID를 선택해서 결과 집계가 엉망이 됨
- 단일선택 문항에 3개를 고르면 "1학년이면서 2학년이면서 3학년"인 응답이 생김

---

### ④ 응답 저장

```java
// 회원
SurveyResponse response = SurveyResponse.ofMember(survey, respondent);
// 게스트
SurveyResponse response = SurveyResponse.ofGuest(survey, req.guestKey());

surveyResponseRepository.save(response);
```

그 다음 각 답변(`Answer`)과 선택한 보기(`AnswerOption`)를 response에 연결해서 저장합니다.

**저장 구조:**
```
SurveyResponse (응답 1건)
├── Answer (문항 1에 대한 답변)
│   ├── AnswerOption (선택한 보기: "Java")
│   └── AnswerOption (선택한 보기: "Python")  ← 복수선택인 경우
├── Answer (문항 2에 대한 답변)
│   └── answerText: "멘토링이 필요합니다"      ← 주관식
└── Answer (문항 3에 대한 답변)
    └── AnswerOption (선택한 보기: "3학년")
```

---

### ⑤ 회원만 토큰 적립

```java
if (!isGuest) {
    int earned = answeredMc * 1 + answeredSa * 2;
    tokenBalanceAfter = tokenService.record(userId, TokenTxType.SURVEY_PARTICIPATE,
            earned, surveyId, response.getId());
    response.updateEarnedToken(earned);
}
```

**게스트는 왜 토큰을 안 주나?**
게스트는 `users` 테이블에 없습니다. 토큰 잔액을 저장할 곳이 없고, 나중에 결과 열람 등 토큰을 쓸 수도 없습니다. 게스트의 참여 가치는 "응답 데이터 기여"이고, 토큰 보상은 회원 가입의 인센티브입니다.

**적립 계산 예시:**
```
설문 구성: 객관식 3개 + 주관식 1개 → maxToken = 3×1 + 1×2 = 5

응답: 객관식 3개 답함 + 주관식 건너뜀
  → earned = 3×1 + 0×2 = 3토큰
  → skipped = { multipleChoice: 0, subjective: 1 }
```

프론트에서 이렇게 보여줄 수 있음:
```
최대 5토큰 중 3토큰 획득!
건너뛴 문항: 주관식 1개 (-2토큰)
```

**earnedToken을 SurveyResponse에도 저장하는 이유:**
`token_transactions`에도 기록되지만, `survey_responses.earned_token`에도 저장합니다. 나중에 마이페이지에서 "이 설문 참여로 몇 토큰 받았지?"를 보여줄 때, 응답 테이블만 조회하면 되니까 편리합니다.

---

### ⑥ 50명 보너스

```java
long respondentCount = surveyResponseRepository.countBySurveyId(surveyId);
if (respondentCount >= 50) {
    boolean alreadyGiven = tokenTransactionRepository
            .existsByRelatedSurveyIdAndType(surveyId, TokenTxType.RESPONDENT_50_BONUS);
    if (!alreadyGiven) {
        tokenService.record(survey.getCreator().getId(),
                TokenTxType.RESPONDENT_50_BONUS, 10, surveyId, null);
    }
}
```

**이 로직이 하는 일:**
1. 이 설문의 총 응답자 수(게스트 포함)를 센다
2. 50명 이상이면 → 이미 이 설문에 보너스를 줬는지 확인
3. 아직 안 줬으면 → 설문 **작성자**에게 +10토큰 (응답자가 아님!)

**"설문당 1회만" — 어떻게 보장하나?**
`token_transactions` 테이블에서 `related_survey_id = 이 설문 AND type = RESPONDENT_50_BONUS`인 거래가 있는지 확인합니다. 있으면 이미 줬으니 스킵.

**없으면?**
50번째, 51번째, 52번째... 응답이 들어올 때마다 작성자에게 10토큰이 계속 쌓임. 100명이면 500토큰 부정 지급.

**응답자에게는 영향 없음:**
50명 보너스는 작성자에게 가는 보상입니다. 응답자의 반환값(tokenReward, tokenBalanceAfter)에는 반영되지 않습니다.

---

## 4. 응답 형태 — 회원 vs 게스트

### 회원 응답

```json
{
  "responseId": 3001,
  "surveyId": 101,
  "isGuest": false,
  "tokenReward": {
    "maxToken": 5,
    "skipped": { "multipleChoice": 0, "subjective": 1 },
    "earnedToken": 3
  },
  "tokenBalanceAfter": 127,
  "submittedAt": "2026-01-05T10:00:00"
}
```

### 게스트 응답

```json
{
  "responseId": 3002,
  "surveyId": 101,
  "isGuest": true,
  "tokenReward": null,
  "tokenBalanceAfter": null,
  "submittedAt": "2026-01-05T10:00:00"
}
```

차이: 게스트는 `tokenReward`와 `tokenBalanceAfter`가 `null`. 토큰 적립 자체가 없으니까.

---

## 5. 에러 발생 시나리오 정리

| 단계 | 상황 | 에러코드 |
|------|------|---------|
| ① | 설문 없음/삭제됨 | SURVEY_001 (404) |
| ① | 종료된 설문 | RESPONSE_003 (409) |
| ② | 자기 설문에 응답 (회원) | RESPONSE_004 (403) |
| ② | 이미 참여함 (회원/게스트) | RESPONSE_002 (409) |
| ② | 게스트인데 guestKey 없음 | COMMON_400 (400) |
| ③ | 필수 문항 미응답 | RESPONSE_001 (400) |
| ③ | 다른 설문의 questionId | COMMON_400 (400) |
| ③ | 객관식에 selectedOptionIds 없음 | COMMON_400 (400) |
| ③ | 단일선택인데 2개 이상 | COMMON_400 (400) |
| ③ | 존재하지 않는 optionId | COMMON_400 (400) |
| ③ | 주관식에 answerText 없음 | COMMON_400 (400) |

---

## 6. 트랜잭션 — 왜 전부 한 덩어리여야 하나?

`submit()` 메서드 전체가 `@Transactional`로 묶여있습니다. 응답 저장 + 토큰 적립 + 50명 보너스가 **전부 성공하거나 전부 실패**합니다.

```
[성공 시] 응답 저장 ✓ + 토큰 적립 ✓ + 50명 보너스 ✓ → 커밋
[실패 시] 응답 저장 ✗ + 토큰 적립 ✗ + 50명 보너스 ✗ → 전부 롤백
```

**트랜잭션 없으면?**
```
응답 저장 ✓ → 토큰 적립 중 에러 ✗
→ 응답은 저장됐는데 토큰은 안 들어옴
→ 사용자: "응답했는데 토큰이 안 왔어요"
→ DB에는 응답이 남아있어서 재시도하면 "이미 참여한 설문입니다"
→ 토큰 영영 못 받음
```

---

## 7. 회원 식별 — @AuthenticationPrincipal

JWT 인증이 완성되어, 컨트롤러에서 회원 식별은 `@AuthenticationPrincipal`로 합니다:

```java
@AuthenticationPrincipal Long userId    // null이면 게스트
```

이 API는 게스트 허용(`permitAll`)이라 JWT가 없어도 됩니다. 없으면 `userId = null` → 게스트로 분기합니다.

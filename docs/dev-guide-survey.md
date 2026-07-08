# Survey 도메인 개발 기록

---

## 1. Survey 도메인이 뭔가?

설문을 **만들고, 조회하고, 삭제**하는 도메인입니다. Loopa의 핵심 도메인 중 하나로, 다른 도메인들이 Survey에 의존합니다:

```
Survey (설문 만들기/조회)
  ↓ 설문이 있어야
Response (응답 제출)
  ↓ 응답이 있어야
Archive (결과 열람/공유)
```

---

## 2. 파일 구조

```
domain/survey/
├── controller/
│   └── SurveyController.java     ← HTTP 요청 받는 입구 (5개 엔드포인트)
├── service/
│   └── SurveyService.java        ← 비즈니스 로직 (검증, 계산, 저장)
├── repository/
│   ├── SurveyRepository.java     ← surveys 테이블 조회
│   ├── QuestionRepository.java   ← questions 테이블 조회
│   └── QuestionOptionRepository.java
├── entity/
│   ├── Survey.java, Question.java, QuestionOption.java
│   ├── Category.java, QuestionType.java
└── dto/
    ├── request/
    │   └── SurveyCreateRequest.java   ← 생성 요청 DTO (@Valid 검증)
    └── response/
        ├── SurveyListResponse.java    ← 목록 항목
        ├── SurveyDetailResponse.java  ← 상세 정보
        ├── SurveyQuestionsResponse.java ← 문항+보기
        ├── SurveyCreateResponse.java  ← 생성 결과
        └── SurveyDeleteResponse.java  ← 삭제 결과
```

---

## 3. 엔드포인트 5개 전체 흐름

### SURVEY-01 · 목록 조회 `GET /surveys`

**게스트 허용.** 메인 화면에서 참여 가능한 설문 목록을 보여줍니다.

```
클라이언트 → GET /surveys?category=IT_AI&keyword=대학생&cursor=50&size=20
         → SurveyController.getList()
         → SurveyService.getList()
         → SurveyRepository.findSurveyList() ← JPQL 커스텀 쿼리
         ← 목록 + nextCursor + hasNext 반환
```

**필터 조건:**
- `is_deleted = false` — 삭제된 설문 제외
- `end_date >= 오늘` — 진행중인 설문만 (종료된 건 안 보임)
- `creator_id != me` — 로그인 상태면 자기 설문 제외 (자기 설문에 응답 불가하니까)
- `category` — 선택적 필터
- `keyword` — 제목에 포함된 검색어 (LIKE)

**커서 페이지네이션이 뭔가?**

"다음 페이지" 구현 방식입니다. 일반적인 offset 방식(`LIMIT 20 OFFSET 40`)은 데이터가 많아지면 느려지고, 중간에 새 데이터가 추가되면 중복/누락이 발생합니다.

커서 방식은 "마지막으로 본 ID 이후의 데이터 N개"를 가져옵니다:

```
첫 페이지: WHERE id < ∞ ORDER BY id DESC LIMIT 21    ← 21개 요청 (20+1)
  → 21개 오면 hasNext=true, 20개만 반환, nextCursor = 20번째 항목의 id
  → 20개 이하면 hasNext=false

다음 페이지: WHERE id < {nextCursor} ORDER BY id DESC LIMIT 21
  → 같은 방식 반복
```

왜 `size + 1`개를 가져오는가? 21개를 요청해서 21개가 왔으면 "아직 더 있다"(hasNext=true), 20개 이하면 "마지막 페이지"(hasNext=false). 실제로 반환하는 건 20개만.

**maxToken 계산:**

각 설문 항목에 `maxToken`(최대 획득 가능 토큰)이 포함됩니다. 이건 DB에 저장된 값이 아니라 문항 구성으로 계산합니다:
```
maxToken = 객관식 문항 수 × 1 + 주관식 문항 수 × 2
```

---

### SURVEY-02 · 상세 조회 `GET /surveys/{surveyId}`

**게스트 허용.** 설문 참여 전에 상세 정보를 보여줍니다.

```
클라이언트 → GET /surveys/101
         → SurveyService.getDetail()
         → findSurveyOrThrow(101) ← 없거나 삭제됐으면 SURVEY_001 에러
         ← 상세 정보 반환
```

**서버에서 계산하는 값들 (DB에 저장 안 함):**
| 필드 | 계산 방법 |
|------|----------|
| `status` | `오늘 > end_date`이면 CLOSED, 아니면 IN_PROGRESS |
| `questionCount` | questions를 type별로 세서 mc/sa/total |
| `maxToken` | mc × 1 + sa × 2 |
| `respondentCount` | survey_responses에서 COUNT |

왜 저장하지 않는가? 응답자 수는 응답이 들어올 때마다 바뀌고, 상태는 시간이 지나면 바뀝니다. 저장하면 동기화 문제가 생기니까 매번 계산합니다.

---

### SURVEY-03 · 문항 조회 `GET /surveys/{surveyId}/questions`

**게스트 허용.** 실제 설문 진행 화면에서 문항과 보기를 보여줍니다.

```
클라이언트 → GET /surveys/101/questions
         → SurveyService.getQuestions()
         ← { surveyId, questions: [ { questionId, order, type, content, options: [...] }, ... ] }
```

- 문항은 `question_order` 순으로 정렬
- 각 문항의 보기는 `option_order` 순으로 정렬
- 주관식 문항은 `options`가 빈 배열 `[]`

**상세(SURVEY-02)와 분리한 이유:** 상세는 "이 설문에 참여할까?" 판단용 요약이고, 문항은 "실제 응답 시작" 시 가져오는 전체 데이터입니다. 한 번에 다 내리면 목록에서 상세 화면으로 넘어갈 때 불필요한 문항 데이터까지 로딩됩니다.

---

### SURVEY-04 · 생성 `POST /surveys`

**회원 전용.** 설문을 생성하고 토큰을 차감합니다. 이 API가 가장 복잡합니다.

```
클라이언트 → POST /surveys (제목, 문항, 보기 등)
         → SurveyService.create()
         → ① 검증: 날짜, 문항 수, 보기 수
         → ② Survey + Question + QuestionOption 엔티티 생성
         → ③ surveyRepository.save(survey)  ← cascade로 문항/보기도 함께 저장
         → ④ tokenService.record(-cost)     ← 토큰 차감 (같은 트랜잭션)
         ← { surveyId, tokenCost, tokenBalanceAfter, ... }
```

**검증 순서:**

```java
// 1. 마감일이 시작일 이후인지
if (!req.endDate().isAfter(req.startDate()))  → SURVEY_002

// 2. 문항이 1개 이상인지
if (req.questions().isEmpty())  → SURVEY_003

// 3. 객관식 문항에 보기가 1개 이상인지
if (type == MULTIPLE_CHOICE && options.isEmpty())  → SURVEY_004

// 4. 토큰 부족 (TokenService 내부에서 검증)
if (잔액 - cost < 0)  → TOKEN_001
```

**비용 계산 예시:**
```
문항 구성: 객관식 3개 + 주관식 1개
비용 = 10(기본) + 3×3(객관식) + 1×5(주관식) = 24토큰
보유 82토큰 → 차감 후 58토큰
```

**왜 save를 먼저 하고 토큰을 차감하는가?**

```java
surveyRepository.save(survey);      // 먼저 저장 (survey.getId()가 필요)
tokenService.record(..., survey.getId(), ...);  // survey ID를 거래내역에 기록
```

`save()` 후에야 `survey.getId()`가 생기므로 (AUTO_INCREMENT), 토큰 거래내역에 "어떤 설문 때문에 차감됐는지" 기록하려면 이 순서가 맞습니다. 둘 다 같은 `@Transactional` 안이라 토큰 차감이 실패하면 설문 저장도 롤백됩니다.

**cascade 동작:**

```java
Survey survey = new Survey(...);
Question q = new Question(survey, ...);
survey.addQuestion(q);                    // Survey의 questions 리스트에 추가
QuestionOption o = new QuestionOption(question, ...);
q.addOption(o);                           // Question의 options 리스트에 추가

surveyRepository.save(survey);            // 이 한 줄로 Survey + Question + Option 전부 INSERT
```

Survey 엔티티에 `cascade = CascadeType.ALL`이 설정되어 있어서, Survey만 save해도 연결된 Question, QuestionOption이 자동으로 함께 저장됩니다.

---

### SURVEY-05 · 삭제 `DELETE /surveys/{surveyId}`

**회원 전용.** 본인이 만든 설문을 소프트 삭제합니다.

```
클라이언트 → DELETE /surveys/101
         → SurveyService.delete()
         → ① findSurveyOrThrow(101)             ← 없으면 SURVEY_001
         → ② creator_id != me?                   ← 남의 설문이면 COMMON_403
         → ③ shared_to_archive == true?           ← 공유된 설문이면 SURVEY_005
         → ④ survey.softDelete()                  ← is_deleted = true
         ← { surveyId: 101, isDeleted: true }
```

**소프트 삭제란?**

DB에서 실제로 DELETE하지 않고, `is_deleted` 플래그만 `true`로 바꿉니다. 이렇게 하면:
- 이 설문에 달린 응답 데이터가 보존됨 (응답자의 토큰 내역이 사라지지 않음)
- 목록 조회 시 `is_deleted = false` 조건으로 자연스럽게 필터링
- 실수로 삭제해도 DB에는 남아있어서 (운영 레벨에서) 복구 가능

**공유된 설문은 왜 삭제 불가?**

아카이브에 공유된 설문은 다른 회원들이 토큰(-15)을 써서 결과를 열람했을 수 있습니다. 이걸 삭제하면 돈 내고 산 결과가 사라지는 셈이라, 공유 후에는 삭제를 차단합니다.

---

## 4. DTO 구조

### Request

**`SurveyCreateRequest`** — 설문 생성 시 클라이언트가 보내는 데이터

```java
SurveyCreateRequest
├── title, description, target, category, estimatedMinutes
├── startDate, endDate
└── questions: List<QuestionRequest>
    ├── order, type, content, isRequired, allowMultiple
    └── options: List<OptionRequest>
        ├── order, content
```

중첩 record(레코드 안에 레코드)로 JSON 구조를 그대로 반영합니다. `@Valid`가 안쪽 레코드까지 전파되어, 보기의 `content`가 비어있으면 400 에러가 자동으로 납니다.

### Response

| DTO | 용도 | 핵심 |
|-----|------|------|
| `SurveyListResponse` | 목록 항목 1개 | maxToken 포함 (참여 시 최대 획득량) |
| `SurveyDetailResponse` | 상세 화면 | status, questionCount, respondentCount 전부 서버 계산 |
| `SurveyQuestionsResponse` | 문항+보기 | 중첩 구조 (Question → Option) |
| `SurveyCreateResponse` | 생성 결과 | tokenCost, tokenBalanceAfter |
| `SurveyDeleteResponse` | 삭제 결과 | isDeleted 확인용 |

---

## 5. Repository 커스텀 쿼리

### `findSurveyList` — 목록 조회 JPQL

```java
@Query("SELECT s FROM Survey s " +
        "WHERE s.isDeleted = false " +
        "AND s.endDate >= :today " +
        "AND (:creatorId IS NULL OR s.creator.id <> :creatorId) " +
        "AND (:category IS NULL OR s.category = :category) " +
        "AND (:keyword IS NULL OR s.title LIKE %:keyword%) " +
        "AND (:cursor IS NULL OR s.id < :cursor) " +
        "ORDER BY s.id DESC")
```

**왜 JPQL인가?**

Spring Data JPA의 메서드 이름 규칙(`findByIsDeletedFalseAndEndDateGreaterThanEqual...`)으로는 이 조건을 표현하기 어렵습니다. 특히 "파라미터가 null이면 조건 무시"하는 패턴은 JPQL의 `:param IS NULL OR` 패턴이 깔끔합니다.

**`:creatorId IS NULL OR s.creator.id <> :creatorId` 의미:**
- 게스트(userId=null)면 조건 무시 → 모든 설문 보임
- 회원(userId=42)이면 자기 설문(creator_id=42) 제외

---

## 6. @CurrentUser 임시 처리

이서진님의 JWT/SecurityConfig가 완성되기 전이라, 회원 식별을 임시로 HTTP 헤더로 받고 있습니다:

```java
// 현재 (임시)
@RequestHeader("X-User-Id") Long userId

// 나중에 (이서진님 JWT 완성 후)
@CurrentUser Long userId
```

테스트할 때 Swagger나 Postman에서 `X-User-Id: 1` 헤더를 넣으면 회원 1번으로 동작합니다. 게스트 허용 API(목록, 상세, 문항)는 이 헤더 없이도 동작합니다 (`required = false`).

---

## 7. 에러 발생 시나리오 정리

| 상황 | 에러코드 | 어디서 발생 |
|------|---------|------------|
| 존재하지 않거나 삭제된 설문 조회 | SURVEY_001 | findSurveyOrThrow() |
| 마감일 ≤ 시작일 | SURVEY_002 | create() 검증 |
| 문항 0개 | SURVEY_003 | create() 검증 |
| 객관식인데 보기 0개 | SURVEY_004 | create() 검증 |
| 공유된 설문 삭제 시도 | SURVEY_005 | delete() 검증 |
| 토큰 부족 | TOKEN_001 | TokenService.record() 내부 |
| 남의 설문 삭제 시도 | COMMON_403 | delete() 검증 |
| 잘못된 카테고리 코드 | COMMON_400 | Category.valueOf() 실패 |

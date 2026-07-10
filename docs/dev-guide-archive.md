# Archive 도메인 개발 기록

---

## 1. Archive 도메인이 뭔가?

종료된 설문의 **결과를 공유하고, 열람하는** 도메인입니다. Loopa의 마지막 도메인으로, 다른 도메인들이 전부 갖춰져야 동작합니다:

```
Survey (설문 만들기)
  ↓ 설문이 있어야
Response (응답 제출)
  ↓ 응답이 쌓여야
Archive (결과 공유/열람/집계)
```

**Archive가 다루는 핵심 흐름:**
1. 설문 작성자가 종료된 자기 설문을 **공유**한다 → 토큰 +10 보상
2. 다른 회원이 공유된 설문의 결과를 **열람 구매**한다 → 토큰 -15 소모
3. 구매 후 결과를 **조회**한다 → 문항별 집계 + 크로스탭 필터

---

## 2. 파일 구조

```
domain/archive/
├── controller/
│   └── ArchiveController.java          ← HTTP 요청 입구 (6개 엔드포인트)
├── service/
│   └── ArchiveService.java             ← 비즈니스 로직 전체
├── repository/
│   └── ArchiveViewRepository.java      ← archive_views 테이블 조회
├── entity/
│   └── ArchiveView.java                ← 열람 기록 엔티티
└── dto/
    ├── request/
    │   └── ArchiveShareRequest.java     ← 공유 요청 (surveyIds)
    └── response/
        ├── ArchiveListResponse.java     ← 목록 항목
        ├── ArchiveViewInfoResponse.java ← 열람 정보 (구매 여부, 비용)
        ├── ArchiveViewPurchaseResponse.java ← 열람 구매 결과
        ├── ArchiveResultResponse.java   ← 세부 결과 (집계 + 크로스탭)
        ├── ArchiveMyShareableResponse.java ← 공유 가능한 내 설문
        └── ArchiveShareResponse.java    ← 공유 결과
```

**Archive가 건드리는 다른 도메인의 Repository:**
- `SurveyRepository` — 아카이브 목록 조회, 공유 가능 설문 조회 쿼리 2개 추가
- `SurveyResponseRepository` — 응답자 수 집계 (`countBySurveyId`)

---

## 3. 엔드포인트 6개 전체 흐름

### ARCHIVE-01 · 아카이브 설문 목록 `GET /archive/surveys`

**회원 전용.** 공유되어 열람 가능한 설문 목록을 보여줍니다.

```
클라이언트 → GET /archive/surveys?category=IT_AI&keyword=대학생&cursor=50&size=20
         → ArchiveController.getList()
         → ArchiveService.getList()
         → SurveyRepository.findArchiveList() ← 커스텀 JPQL
         ← 목록 + nextCursor + hasNext
```

**Survey 목록(`GET /surveys`)과 다른 점:**

| 구분 | Survey 목록 | Archive 목록 |
|------|------------|-------------|
| 대상 | 진행중인 설문 (`end_date >= 오늘`) | 공유된 설문 (`shared_to_archive = true`) |
| 목적 | "참여할 설문 찾기" | "결과를 열람할 설문 찾기" |
| 자기 설문 | 제외 (자기 설문에 응답 불가) | 포함 (자기 설문 결과도 여기서 볼 수 있음) |
| maxToken | 포함 (참여 보상 안내) | 미포함 (이미 종료, 참여 불가) |
| respondentCount | 미포함 | 포함 (얼마나 많은 응답이 있는지 안내) |

**findArchiveList JPQL:**

```java
@Query("SELECT s FROM Survey s " +
        "WHERE s.isDeleted = false " +
        "AND s.sharedToArchive = true " +
        "AND (:category IS NULL OR s.category = :category) " +
        "AND (:keyword IS NULL OR s.title LIKE %:keyword%) " +
        "AND (:cursor IS NULL OR s.id < :cursor) " +
        "ORDER BY s.id DESC")
```

`end_date` 조건이 없는 이유: 공유된 설문은 무조건 종료 상태입니다 (공유 시 종료 여부를 검증하니까). 그래서 `sharedToArchive = true`만 확인하면 충분합니다.

---

### ARCHIVE-02 · 열람 정보 조회 `GET /archive/surveys/{surveyId}`

**회원 전용.** 결과를 열람하기 전에 "이 설문은 뭔지, 열람비가 얼마인지, 이미 구매했는지"를 보여줍니다.

```
클라이언트 → GET /archive/surveys/77
         → ArchiveService.getViewInfo()
         → ① findSharedSurveyOrThrow(77)  ← 없거나 미공유면 SURVEY_001
         → ② 문항 수 계산, 응답자 수 집계
         → ③ alreadyViewed 판별
         ← { surveyInfo + viewCost: 15, alreadyViewed, tokenBalance }
```

**`alreadyViewed` — 왜 필요한가?**

```java
boolean alreadyViewed = s.getCreator().getId().equals(userId)      // 본인 설문
        || archiveViewRepository.existsByViewerIdAndSurveyId(userId, surveyId);  // 기구매
```

`alreadyViewed = true`이면 프론트에서 "15토큰 소모하기" 버튼 대신 "결과 보기" 버튼을 바로 보여줍니다. 두 가지 경우에 true입니다:
1. **본인 설문** — 자기가 만든 설문 결과는 당연히 무료
2. **기구매** — 이미 15토큰을 써서 열람권을 산 상태

**없으면?** 프론트가 매번 "15토큰 소모" 버튼을 보여주고, 이미 구매한 사용자가 다시 누르면 "이미 구매됨" 응답이 옴 — 사용자 경험이 나빠짐.

**`tokenBalance` — 왜 응답에 포함?**

열람 전 화면에서 "현재 보유: 124토큰 / 열람비: 15토큰" 같은 팝업을 보여주기 위해서입니다. 잔액이 부족하면 프론트에서 미리 안내할 수 있습니다.

---

### ARCHIVE-03 · 열람 구매 `POST /archive/surveys/{surveyId}/views`

**회원 전용.** 15토큰을 소모하고 열람권을 획득합니다.

```
클라이언트 → POST /archive/surveys/77/views
         → ArchiveService.purchaseView()
         → ① findSharedSurveyOrThrow(77)
         → ② 본인 설문? → tokenSpent=0으로 즉시 성공
         → ③ 기구매? → tokenSpent=0으로 즉시 성공
         → ④ 신규 구매: TokenService.record(-15) + archive_views 저장
         ← { viewId, tokenSpent, balanceBefore, balanceAfter }
```

**멱등성 — 왜 본인/기구매를 에러가 아니라 성공으로 처리하나?**

```java
// 본인 설문
if (s.getCreator().getId().equals(userId)) {
    return buildFreePurchaseResponse(null, surveyId, user.getTokenBalance());
}

// 이미 구매
Optional<ArchiveView> existing = archiveViewRepository.findByViewerIdAndSurveyId(userId, surveyId);
if (existing.isPresent()) {
    return buildFreePurchaseResponse(view.getId(), surveyId, user.getTokenBalance());
}
```

이렇게 하면 프론트에서 상태 관리가 단순해집니다:
- "결과 보기" 버튼을 누르면 → 먼저 `POST /views`를 호출하고 → 성공하면 결과 화면으로 이동
- 이미 구매했든, 본인 설문이든, 처음 구매하든 **항상 성공 응답**이 옴
- 프론트가 "이미 구매했는지" 상태를 기억할 필요 없음

**에러로 처리하면?** 프론트가 "먼저 ARCHIVE-02로 `alreadyViewed`를 확인하고, true이면 구매를 건너뛰고 바로 결과 조회"라는 분기 로직이 필요해짐. 더 복잡하고, 네트워크 요청도 늘어남.

**`tokenSpent=0`과 `tokenSpent=15`로 구분하는 이유:**

프론트 팝업용입니다:
- `tokenSpent=15` → "15토큰을 사용했습니다. 잔액: 109 → 124"
- `tokenSpent=0` → 팝업 안 띄우고 바로 결과 화면으로

**토큰 차감과 archive_views 저장이 같은 트랜잭션인 이유:**

```java
@Transactional
public ArchiveViewPurchaseResponse purchaseView(...) {
    int balanceAfter = tokenService.record(userId, TokenTxType.RESULT_VIEW, -VIEW_COST, surveyId, null);
    ArchiveView view = new ArchiveView(user, s, VIEW_COST);
    archiveViewRepository.save(view);
    ...
}
```

**트랜잭션이 없으면?**
```
토큰 차감 ✓ (-15) → archive_views 저장 중 에러 ✗
→ 토큰은 빠졌는데 열람권은 없음
→ "열람하기" 누르면 "권한이 없습니다" (ARCHIVE_001)
→ 토큰만 날림
```

반대도 위험합니다:
```
archive_views 저장 ✓ → 토큰 차감 중 에러 ✗
→ 열람권은 있는데 토큰은 안 빠짐
→ 무료로 결과를 열람한 셈
```

---

### ARCHIVE-04 · 세부 결과 조회 `GET /archive/surveys/{surveyId}/results`

**회원 전용.** 문항별 응답 집계를 보여줍니다. **크로스탭 필터**를 지원합니다. Archive의 핵심이자 가장 복잡한 API입니다.

```
클라이언트 → GET /archive/surveys/77/results?filters=9002,9010
         → ArchiveService.getResults()
         → ① 권한 확인 (본인 설문 or 열람권 보유)
         → ② 필터 검증 (객관식 보기 ID만 허용)
         → ③ 필터된 응답자 집합 계산 (AND 조건)
         → ④ 문항별 집계 (MC: 보기별 count/%, SA: 텍스트 목록)
         ← { surveyInfo, appliedFilters, filteredRespondentCount, results }
```

#### 권한 확인

```java
boolean isOwner = s.getCreator().getId().equals(userId);
if (!isOwner && !archiveViewRepository.existsByViewerIdAndSurveyId(userId, surveyId)) {
    throw new GeneralException(ArchiveErrorCode.NO_VIEW_PERMISSION);  // ARCHIVE_001
}
```

두 가지 경우만 결과를 볼 수 있습니다:
1. 본인이 만든 설문 — 자기 설문 결과는 무료
2. `archive_views`에 기록이 있음 — 15토큰 내고 산 열람권 보유

**없으면?** 누구나 URL만 알면 `/archive/surveys/77/results`로 결과를 무료 열람. 토큰 이코노미가 무너짐.

#### 필터 없을 때 — 기본 집계

`filters` 파라미터가 없으면 **전체 응답자** 기준으로 집계합니다.

**객관식 집계:**
```
문항: "현재 학년은?"
보기별 집계 (전체 52명 응답):
  1학년 → 7명  (13.5%)
  2학년 → 45명 (86.5%)
  3학년 → 0명  (0.0%)
  4학년 → 0명  (0.0%)
```

```java
// 보기 하나의 선택 수
String jpql = "SELECT COUNT(ao) FROM AnswerOption ao " +
        "WHERE ao.option.id = :optionId " +
        "AND ao.answer.response.survey.id = :surveyId";
```

**주관식 집계:**
```
문항: "가장 필요한 지원은?"
응답 텍스트 목록:
  - "멘토링 프로그램이 필요합니다."
  - "공모전 정보를 모아주는 채널이 있으면 좋겠습니다."
  - "참가비 지원이 있으면 좋겠습니다."
```

```java
String jpql = "SELECT a.answerText FROM Answer a " +
        "WHERE a.question.id = :questionId " +
        "AND a.response.survey.id = :surveyId " +
        "AND a.answerText IS NOT NULL";
```

**백분율 계산:**
```java
double percentage = filteredRespondentCount > 0
        ? Math.round(count * 1000.0 / filteredRespondentCount) / 10.0  // 소수점 1자리
        : 0.0;
```

`1000.0`을 곱한 뒤 `Math.round`하고 `10.0`으로 나누는 이유: 소수점 첫째 자리까지만 반올림하기 위해서입니다.
```
7 / 52 = 0.13461...
× 1000 = 134.61...
round = 135
÷ 10 = 13.5%
```

#### 필터 있을 때 — 크로스탭 필터 집계

**크로스탭이 뭔가?**

"2학년인 사람만 모아서 나머지 문항 결과를 다시 보자"입니다.

예시: 설문에 문항이 3개 있다고 합시다:
```
Q1. 현재 학년? [1학년] [2학년] [3학년] [4학년]
Q2. 공모전 참여 경험? [있다] [없다]
Q3. 가장 필요한 지원? (주관식)
```

전체 52명 중 Q1에서 "2학년"(optionId=9002)을 선택한 사람이 45명입니다. `?filters=9002`를 보내면:
- **45명**만 추려서
- Q2를 다시 집계 → "2학년 중 공모전 경험 있는 비율은?"
- Q3를 다시 집계 → "2학년들은 어떤 지원이 필요하다고 했나?"

이걸 크로스탭(교차 분석)이라고 합니다.

**복수 필터 — AND 조건:**

`?filters=9002,9010`이면 "2학년이면서 공모전 경험이 있는 사람"으로 좁힙니다.

```java
String jpql = "SELECT ao.answer.response.id " +
        "FROM AnswerOption ao " +
        "WHERE ao.answer.response.survey.id = :surveyId " +
        "AND ao.option.id IN :filterOptionIds " +
        "GROUP BY ao.answer.response.id " +
        "HAVING COUNT(DISTINCT ao.option.id) = :filterSize";
```

이 쿼리가 하는 일을 단계별로 보면:

```sql
-- 1단계: 지정된 보기(9002, 9010) 중 하나라도 선택한 answer_option 행을 찾는다
WHERE ao.option.id IN (9002, 9010)

-- 2단계: response_id별로 그룹핑한다
GROUP BY ao.answer.response.id

-- 3단계: 지정된 보기를 "모두" 선택한 응답자만 남긴다
HAVING COUNT(DISTINCT ao.option.id) = 2  -- filterSize = 2
```

**왜 `COUNT(DISTINCT ao.option.id) = filterSize`인가?**

`IN (9002, 9010)` 조건을 통과한 행 중에서, 응답자가 **두 보기를 모두** 선택했으면 COUNT가 2입니다. 하나만 선택했으면 COUNT가 1이라 HAVING에서 탈락합니다. 이것이 AND 조건을 SQL로 구현하는 방법입니다.

**없으면?** 쿼리에서 `IN` 조건만 쓰면 OR 로직이 됩니다. "2학년이거나 공모전 경험 있거나"인 사람이 전부 포함되어, 의도한 교차 분석이 안 됩니다.

**필터 검증:**

```java
private void validateFilterOptions(Survey survey, List<Long> filterOptionIds) {
    Set<Long> validOptionIds = survey.getQuestions().stream()
            .filter(q -> q.getType() == QuestionType.MULTIPLE_CHOICE)  // 객관식만
            .flatMap(q -> q.getOptions().stream())
            .map(QuestionOption::getId)
            .collect(Collectors.toSet());

    for (Long optionId : filterOptionIds) {
        if (!validOptionIds.contains(optionId)) {
            throw new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE);  // COMMON_400
        }
    }
}
```

두 가지를 검증합니다:
1. **주관식 보기 ID로 필터 시도** → 주관식은 이산값이 아니라 자유 텍스트이므로 필터 조건이 될 수 없음
2. **다른 설문의 optionId로 필터 시도** → 엉뚱한 데이터로 집계되는 걸 방지

**없으면?** 다른 설문의 보기 ID를 넣으면 filteredResponseIds가 빈 집합이 되어 "응답자 0명"으로 나옴. 에러가 아니라 빈 결과가 나오니 사용자가 왜 비었는지 알 수 없음.

#### 응답 DTO 구조

```json
{
  "surveyInfo": {
    "surveyId": 77,
    "title": "대학생 공모전 참여 경험 조사",
    "respondentCount": 52,
    "questionCount": { "multipleChoice": 3, "subjective": 1, "total": 4 }
  },
  "appliedFilters": [9002],
  "filteredRespondentCount": 45,
  "results": [
    {
      "questionId": 5001,
      "type": "MULTIPLE_CHOICE",
      "content": "현재 학년은?",
      "options": [
        { "optionId": 9001, "content": "1학년", "count": 5,  "percentage": 11.1 },
        { "optionId": 9002, "content": "2학년", "count": 45, "percentage": 100.0 }
      ],
      "answers": null
    },
    {
      "questionId": 5004,
      "type": "SUBJECTIVE",
      "content": "가장 필요한 지원은?",
      "options": null,
      "answers": ["멘토링 프로그램이 필요합니다.", "참가비 지원"]
    }
  ]
}
```

주목할 점:
- `respondentCount`(52) vs `filteredRespondentCount`(45) — 전체 vs 필터 후
- 필터로 "2학년"을 선택했으므로, Q1의 "2학년" count는 45, percentage는 100%
- 주관식은 `options: null`, 객관식은 `answers: null` — 타입에 따라 한쪽만 채워짐

---

### ARCHIVE-05 · 공유 가능한 내 설문 목록 `GET /archive/my-surveys`

**회원 전용.** 내가 만든 종료된 설문을 공유 대상으로 보여줍니다.

```
클라이언트 → GET /archive/my-surveys?cursor=50&size=20
         → ArchiveService.getMyShareableSurveys()
         → SurveyRepository.findShareableSurveys()
         ← 종료된 내 설문 목록 (공유 여부 포함)
```

**findShareableSurveys JPQL:**

```java
@Query("SELECT s FROM Survey s " +
        "WHERE s.isDeleted = false " +
        "AND s.creator.id = :creatorId " +
        "AND s.endDate < :today " +
        "AND (:cursor IS NULL OR s.id < :cursor) " +
        "ORDER BY s.id DESC")
```

- `end_date < today` — 종료된 설문만 (진행중이면 아직 응답이 더 들어올 수 있으니 공유 대상 아님)
- `sharedToArchive`로 필터링하지 않음 — 이미 공유된 설문도 목록에 보여줌 (`sharedToArchive: true`로 표기해서 중복 선택 방지)

**왜 이미 공유된 설문도 보여주나?**

프론트에서 "공유" 버튼 옆에 "공유됨" 뱃지를 표시하기 위해서입니다. 목록에서 제외하면 "내가 어떤 설문을 이미 공유했지?"를 확인할 방법이 없습니다.

---

### ARCHIVE-06 · 설문 공유 `POST /archive/shares`

**회원 전용.** 종료된 내 설문을 아카이브에 공유하고 토큰을 받습니다.

```
클라이언트 → POST /archive/shares { "surveyIds": [98, 99] }
         → ArchiveService.share()
         → ① 각 설문 검증 (존재·소유·종료·미공유)
         → ② survey.shareToArchive()  ← shared_to_archive = true, archived_at = now
         → ③ TokenService.record(+10)  ← 공유당 +10토큰
         ← { sharedCount, totalRewardToken, balanceBefore, balanceAfter }
```

**한 번에 여러 설문을 공유하는 이유:**

프론트에서 체크박스로 여러 설문을 선택하고 "선택 공유" 버튼을 누르는 UX입니다. 설문마다 API를 따로 호출하면 네트워크 요청이 N번 발생하고, 중간에 하나가 실패하면 "어디까지 공유됐지?"가 애매해집니다.

**검증 순서와 이유:**

```java
for (Long surveyId : request.surveyIds()) {
    Survey s = surveyRepository.findByIdAndIsDeletedFalse(surveyId)
            .orElseThrow(...)                                       // SURVEY_001
    if (!s.getCreator().getId().equals(userId))  → COMMON_403       // 타인 설문
    if (!s.isClosed())  → ARCHIVE_003                               // 진행중
    if (s.getSharedToArchive())  → ARCHIVE_002                      // 기공유
}
```

4가지를 순서대로 검증합니다. **전부 검증을 통과해야** 실제 공유 처리로 넘어갑니다.

- 존재 확인을 가장 먼저 — 없는 설문을 "진행중인가?" 확인할 수 없으니까
- 소유 확인 — 남의 설문을 공유하면 남의 설문 작성자가 토큰을 받을 수도 없고, 작성자 의도와 무관하게 결과가 공개됨
- 종료 확인 — 진행중 설문을 공유하면 아직 응답이 덜 쌓인 상태에서 결과가 노출됨
- 기공유 확인 — 같은 설문을 두 번 공유하면 토큰을 이중으로 받는 셈

**없으면?**
- 소유 검증 없음 → 남의 설문을 공유해서 내가 토큰을 받거나, 작성자 의도와 무관하게 결과가 공개됨
- 종료 검증 없음 → 설문이 아직 진행중인데 결과가 공개되어, 응답 편향 발생 (다른 사람 답변을 보고 따라 함)
- 기공유 검증 없음 → 같은 설문을 100번 공유해서 1000토큰 부정 수급

**토큰 지급이 반복문 안에 있는 이유:**

```java
int balanceAfter = balanceBefore;
for (Survey s : surveys) {
    s.shareToArchive();
    balanceAfter = tokenService.record(userId, TokenTxType.RESULT_SHARE, +10, s.getId(), null);
}
```

설문마다 개별 토큰 거래를 남깁니다. 이렇게 하면 `token_transactions`에 "어떤 설문 때문에 +10을 받았는지"가 건별로 기록됩니다. 한 번에 +20을 기록하면 나중에 "이 토큰은 어디서 온 거지?"를 추적할 수 없습니다.

`balanceAfter`는 마지막 `record()`의 반환값이 최종 잔액입니다.

---

## 4. findSharedSurveyOrThrow — 공유된 설문 조회

```java
private Survey findSharedSurveyOrThrow(Long surveyId) {
    Survey s = surveyRepository.findByIdAndIsDeletedFalse(surveyId)
            .orElseThrow(() -> new GeneralException(SurveyErrorCode.SURVEY_NOT_FOUND));
    if (!s.getSharedToArchive()) {
        throw new GeneralException(SurveyErrorCode.SURVEY_NOT_FOUND);
    }
    return s;
}
```

ARCHIVE-02, 03, 04에서 공통으로 사용합니다. 두 가지를 한번에 확인합니다:
1. 설문이 존재하고 삭제되지 않았는가
2. 아카이브에 공유되었는가

**미공유 설문도 `SURVEY_001`로 처리하는 이유:**

미공유 설문은 아카이브 관점에서 "존재하지 않는 것"과 같습니다. 별도 에러코드를 만들면 "이 ID의 설문은 존재하지만 공유는 안 됐다"는 정보가 노출됩니다. 보안상 불필요한 정보를 숨기기 위해 같은 에러코드를 씁니다.

---

## 5. 크로스탭 집계의 내부 동작

ARCHIVE-04가 복잡하므로 전체 흐름을 다시 한번 단계별로 정리합니다.

### 전체 집계 (필터 없음)

```
filteredResponseIds = null  (null은 "전체"를 의미)
filteredRespondentCount = 52 (전체 응답자 수)

각 문항마다:
  객관식 → buildMcResult() → 보기별 COUNT + % 계산
  주관식 → buildSaResult() → answerText 목록 수집
```

### 필터 집계 (필터 있음)

```
Step 1. filterOptionIds = [9002, 9010]
Step 2. validateFilterOptions() → 이 설문의 객관식 보기 ID인지 확인
Step 3. findFilteredResponseIds() → AND 조건으로 응답자 집합 축소 → {101, 103, 107, ...}
Step 4. filteredRespondentCount = 집합 크기

각 문항마다:
  객관식 → response_id IN :responseIds 조건으로 COUNT
  주관식 → response_id IN :responseIds 조건으로 텍스트 수집
```

### 필터 결과가 빈 집합일 때

```java
if (filteredResponseIds.isEmpty()) {
    count = 0;  // 쿼리를 실행하지 않음
}
```

빈 집합으로 `IN :responseIds` 쿼리를 실행하면 SQL 에러가 발생할 수 있습니다 (`IN ()`은 유효하지 않은 SQL). 그래서 빈 집합이면 쿼리 없이 count=0으로 처리합니다.

### 집계를 쿼리로 하는 이유

구현 지침 체크리스트에 있는 항목입니다: **"집계가 애플리케이션 루프가 아니라 쿼리로 처리되는가"**

```java
// ❌ 나쁜 예 — 애플리케이션 루프
List<AnswerOption> allOptions = answerOptionRepository.findAll();
int count = 0;
for (AnswerOption ao : allOptions) {
    if (ao.getOption().getId().equals(optionId)) count++;
}

// ✅ 좋은 예 — DB 쿼리
"SELECT COUNT(ao) FROM AnswerOption ao WHERE ao.option.id = :optionId AND ..."
```

응답이 1000개이고 보기가 5개면, 나쁜 예는 5000행을 Java 메모리에 올려서 루프를 돌립니다. 좋은 예는 DB가 집계를 하고 숫자 하나만 반환합니다.

---

## 6. EntityManager를 직접 쓰는 이유

```java
private final EntityManager em;
```

ArchiveService에서 `EntityManager`를 주입받아 JPQL을 직접 실행합니다. 일반적으로는 Repository에 `@Query`를 쓰지만, 집계 쿼리가 **동적 조건** (필터 있음/없음, 필터된 responseIds 사용 여부)에 따라 달라져야 해서 Repository 인터페이스의 고정 메서드로는 깔끔하게 표현하기 어렵습니다.

```java
// 필터 없을 때
"SELECT COUNT(ao) FROM AnswerOption ao WHERE ao.option.id = :optionId AND ao.answer.response.survey.id = :surveyId"

// 필터 있을 때
"SELECT COUNT(ao) FROM AnswerOption ao WHERE ao.option.id = :optionId AND ao.answer.response.id IN :responseIds"
```

WHERE 절 자체가 달라지므로, 하나의 Repository 메서드로 합치기 어렵습니다.

---

## 7. 에러 발생 시나리오 정리

| API | 상황 | 에러코드 |
|-----|------|---------|
| 01~06 공통 | 존재하지 않거나 삭제된 설문 | SURVEY_001 (404) |
| 01 | 잘못된 카테고리 코드 | COMMON_400 (400) |
| 02~04 | 공유되지 않은 설문 접근 | SURVEY_001 (404) |
| 03 | 토큰 부족 (잔액 < 15) | TOKEN_001 (400) |
| 04 | 열람권 미보유 (구매 안 함) | ARCHIVE_001 (403) |
| 04 | 필터에 주관식 보기나 존재하지 않는 optionId | COMMON_400 (400) |
| 06 | 타인 설문 공유 시도 | COMMON_403 (403) |
| 06 | 진행 중 설문 공유 시도 | ARCHIVE_003 (400) |
| 06 | 이미 공유된 설문 | ARCHIVE_002 (409) |

---

## 8. 회원 식별 — @AuthenticationPrincipal

JWT 인증이 완성되어, 컨트롤러에서 회원 식별은 `@AuthenticationPrincipal`로 합니다:

```java
@AuthenticationPrincipal Long userId
```

Archive의 6개 API는 전부 회원 전용입니다 (게스트 허용 없음). SecurityConfig에서 `authenticated()`로 설정되어 있어서, JWT 없이 접근하면 401 에러가 반환됩니다.

---

## 9. SurveyRepository에 추가된 쿼리 2개

Archive 도메인 작업 중 SurveyRepository에 쿼리 2개를 추가했습니다.

### findArchiveList — 아카이브 목록 (ARCHIVE-01)

```java
@Query("SELECT s FROM Survey s " +
        "WHERE s.isDeleted = false " +
        "AND s.sharedToArchive = true " +
        "AND (:category IS NULL OR s.category = :category) " +
        "AND (:keyword IS NULL OR s.title LIKE %:keyword%) " +
        "AND (:cursor IS NULL OR s.id < :cursor) " +
        "ORDER BY s.id DESC")
List<Survey> findArchiveList(Category category, String keyword, Long cursor, int size);
```

### findShareableSurveys — 공유 가능한 내 설문 (ARCHIVE-05)

```java
@Query("SELECT s FROM Survey s " +
        "WHERE s.isDeleted = false " +
        "AND s.creator.id = :creatorId " +
        "AND s.endDate < :today " +
        "AND (:cursor IS NULL OR s.id < :cursor) " +
        "ORDER BY s.id DESC")
List<Survey> findShareableSurveys(Long creatorId, LocalDate today, Long cursor, int size);
```

기존 `findSurveyList`와 동일한 커서 페이지네이션 패턴입니다. 서비스에서 `size + 1`개를 요청해서 `hasNext`를 판별하는 방식도 동일합니다.

# TokenService 개발 기록

---

## 1. TokenService가 뭔가?

Loopa에서 토큰이 움직이는 모든 상황(회원가입 보너스, 설문 생성 비용, 참여 적립, 열람 구매 등)을 **한 곳에서 처리하는 공용 부품**입니다.

다른 Service들이 토큰을 움직여야 할 때 직접 DB를 건드리지 않고, 무조건 `TokenService.record()`를 호출합니다.

```
AuthService (회원가입 +100)       ──┐
SurveyService (설문 생성 -비용)    ──┤
ResponseService (참여 적립 +N)     ──┼──→  TokenService.record()  ──→  DB
ArchiveService (열람 구매 -15)     ──┤
ArchiveService (공유 +10)         ──┘
```

---

## 2. 왜 한 곳에 모았는가?

토큰을 건드리는 도메인이 5개나 됩니다. 만약 각자 직접 처리하면:

- **잔액 검증 로직이 5군데에 흩어짐** → 하나에서 빼먹으면 마이너스 잔액 발생
- **동시 요청 처리(락)를 5군데서 각각 구현** → 실수하면 잔액 꼬임
- **거래 내역 저장 형식이 제각각** → 나중에 원장 추적 불가

TokenService로 통일하면 잔액 검증, 동시성 제어, 원장 기록이 **딱 한 곳**에서만 일어나므로 버그가 줄고 유지보수가 쉽습니다.

---

## 3. record() 메서드 한 줄 한 줄 설명

```java
@Transactional
public int record(Long userId, TokenTxType type, int amount,
                  Long relatedSurveyId, Long relatedResponseId) {
```

### 파라미터

| 파라미터 | 의미 | 예시 |
|---------|------|------|
| `userId` | 토큰을 받거나 쓰는 회원 ID | `42` |
| `type` | 왜 토큰이 움직이는지 (6종 중 하나) | `SURVEY_CREATE`, `SURVEY_PARTICIPATE` |
| `amount` | 변동량 (부호 포함) | `+5` (적립), `-24` (차감) |
| `relatedSurveyId` | 관련 설문 ID (없으면 null) | `7` |
| `relatedResponseId` | 관련 응답 ID (없으면 null) | `null` |

### 반환값

`int` — 변동 후 잔액. 호출한 쪽에서 응답에 실어 보낼 수 있음.

---

### 내부 동작 (4단계)

```java
// 1단계: 비관적 락으로 회원 조회
User user = userRepository.findByIdForUpdate(userId)
        .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));
```

**`findByIdForUpdate`가 뭔가?**

일반 조회(`findById`)와 다르게, DB에 `SELECT ... FOR UPDATE`를 실행합니다. 이러면 **이 트랜잭션이 끝날 때까지 다른 트랜잭션이 같은 행을 수정하지 못합니다** (대기).

**왜 필요한가? — 동시 요청 문제:**

```
[요청 A] 잔액 읽기 → 100       [요청 B] 잔액 읽기 → 100
[요청 A] 100 - 24 = 76 저장    [요청 B] 100 - 15 = 85 저장
결과: 잔액 85 (24토큰이 증발!)
```

비관적 락을 걸면:
```
[요청 A] 잔액 읽기 + 락 → 100        [요청 B] 읽으려 하지만 대기...
[요청 A] 100 - 24 = 76 저장, 락 해제  [요청 B] 이제 읽음 → 76
                                       [요청 B] 76 - 15 = 61 저장
결과: 잔액 61 (정확!)
```

---

```java
// 2단계: 잔액 계산 + 부족 검증
int after = user.getTokenBalance() + amount;
if (after < 0) {
    throw new GeneralException(TokenErrorCode.INSUFFICIENT_BALANCE);
}
```

`amount`는 부호를 포함합니다:
- 적립: `amount = +5` → `100 + 5 = 105` (양수니까 통과)
- 차감: `amount = -24` → `100 + (-24) = 76` (양수니까 통과)
- 잔액 부족: `amount = -200` → `100 + (-200) = -100` (음수! → TOKEN_001 에러)

**차감에서만 에러가 날 수 있습니다.** 적립(양수)은 절대 음수가 안 되므로 항상 통과.

---

```java
// 3단계: User의 잔액 캐시 갱신
user.updateTokenBalance(after);
```

`users.token_balance`를 새 값으로 업데이트합니다. JPA가 트랜잭션 끝날 때 자동으로 UPDATE SQL을 실행합니다.

---

```java
// 4단계: 거래 내역(원장) 저장
TokenTransaction tx = TokenTransaction.of(user, type, amount, after,
        relatedSurveyId, relatedResponseId);
tokenTransactionRepository.save(tx);
```

`token_transactions` 테이블에 한 행을 추가합니다. 이게 **원장**입니다.

저장되는 내용 예시:
| user_id | type | amount | balance_after | related_survey_id |
|---------|------|--------|---------------|-------------------|
| 42 | SURVEY_CREATE | -24 | 76 | 7 |

`balance_after`를 저장하는 이유: 나중에 "이 시점에 잔액이 얼마였지?"를 바로 알 수 있음. 프론트에서 "100 → 76" 같은 표시에 사용.

---

```java
// 5단계: 결과 반환
return after;  // 76
```

호출한 쪽(예: SurveyService)이 이 값을 API 응답에 `tokenBalanceAfter: 76`으로 내려줍니다.

---

## 4. 호출 예시 (다른 Service에서 이렇게 씀)

```java
// 회원가입 시 (AuthService)
tokenService.record(newUser.getId(), TokenTxType.SIGNUP_BONUS, +100, null, null);

// 설문 생성 시 (SurveyService)
int cost = 10 + mcCount * 3 + saCount * 5;  // 예: 10 + 3*3 + 1*5 = 24
int balanceAfter = tokenService.record(userId, TokenTxType.SURVEY_CREATE, -cost, surveyId, null);

// 설문 참여 시 (ResponseService, 회원만)
int earned = answeredMcCount * 1 + answeredSaCount * 2;  // 예: 3*1 + 1*2 = 5
int balanceAfter = tokenService.record(userId, TokenTxType.SURVEY_PARTICIPATE, +earned, surveyId, responseId);

// 50명 보너스 (ResponseService, 작성자에게)
tokenService.record(creatorId, TokenTxType.RESPONDENT_50_BONUS, +10, surveyId, null);

// 아카이브 열람 구매 (ArchiveService)
int balanceAfter = tokenService.record(userId, TokenTxType.RESULT_VIEW, -15, surveyId, null);

// 아카이브 공유 (ArchiveService)
int balanceAfter = tokenService.record(userId, TokenTxType.RESULT_SHARE, +10, surveyId, null);
```

---

## 5. @Transactional 주의사항

`record()`에 `@Transactional`이 붙어 있지만, 실제로는 **호출하는 쪽의 트랜잭션에 합류**합니다.

```java
// SurveyService.java
@Transactional  // ← 이 트랜잭션 안에서
public SurveyCreateResponse create(...) {
    Survey survey = surveyRepository.save(...);           // 설문 저장
    tokenService.record(userId, SURVEY_CREATE, -cost, ...); // 토큰 차감 (같은 트랜잭션)
    return response;
}
// 여기서 트랜잭션 커밋 → 설문 저장 + 토큰 차감이 동시에 확정
// 중간에 에러나면 → 둘 다 롤백 (설문도 안 만들어지고, 토큰도 안 빠짐)
```

이게 중요한 이유: 토큰은 빠졌는데 설문은 안 만들어지는 상황을 방지합니다. **같은 트랜잭션이니까 전부 성공하거나 전부 실패합니다.**

---

## 6. 토큰 거래 유형 6종 정리

| TokenTxType | amount 부호 | 발생 시점 | 대상 |
|-------------|------------|-----------|------|
| `SIGNUP_BONUS` | +100 | 회원가입 | 신규 회원 |
| `SURVEY_CREATE` | -(10 + MC*3 + SA*5) | 설문 생성 | 작성자 |
| `SURVEY_PARTICIPATE` | +(답한 MC*1 + 답한 SA*2) | 응답 제출 | 참여자 (회원만) |
| `RESPONDENT_50_BONUS` | +10 | 응답자 50명 도달 | 작성자 |
| `RESULT_SHARE` | +10 | 아카이브 공유 | 작성자 |
| `RESULT_VIEW` | -15 | 결과 열람 구매 | 열람자 |

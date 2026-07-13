# ERD 설계 근거 문서

> 11개 테이블이 **왜 이렇게 분리되었는지**, 합치면 어떤 이상현상이 생기는지, 정규화·인덱스·제약조건의 설계 근거를 정리한 문서입니다.

---

## 전체 테이블 목록

```
users                  ← 회원
refresh_tokens         ← 리프레시 토큰 (화이트리스트)
email_verifications    ← 이메일 인증
surveys                ← 설문
questions              ← 문항
question_options       ← 보기 (객관식 선택지)
survey_responses       ← 설문 응답 (제출 단위)
answers                ← 문항 답변
answer_options         ← 선택한 보기 (다대다 해소)
token_transactions     ← 토큰 거래내역 (원장)
archive_views          ← 아카이브 열람 기록
```

---

# 1. 설문 구조: surveys → questions → question_options

## 왜 3개로 분리했는가

### 분리하지 않은 경우 (반정규화)

만약 설문, 문항, 보기를 한 테이블에 넣으면:

```
| survey_id | title        | q_order | q_content      | q_type | opt_order | opt_content |
|-----------|-------------|---------|----------------|--------|-----------|-------------|
| 1         | AI 실태조사  | 1       | 현재 학년은?    | MC     | 1         | 1학년       |
| 1         | AI 실태조사  | 1       | 현재 학년은?    | MC     | 2         | 2학년       |
| 1         | AI 실태조사  | 1       | 현재 학년은?    | MC     | 3         | 3학년       |
| 1         | AI 실태조사  | 2       | 필요한 지원은?  | SA     | NULL      | NULL        |
```

**발생하는 이상현상:**

**삽입 이상:** 보기가 없는 주관식 문항을 추가하려면 opt_order, opt_content에 NULL을 넣어야 합니다. 보기가 0개인 건 "데이터가 없는 것"인데 행 자체는 존재해야 하는 모순이 생깁니다.

**갱신 이상:** 설문 제목 "AI 실태조사"를 "AI 활용 실태 조사"로 바꾸려면 해당 설문의 모든 행(위 예시에서 4행)을 전부 UPDATE해야 합니다. 하나라도 놓치면 같은 설문인데 제목이 다른 불일치가 발생합니다.

**삭제 이상:** Q1의 보기 "3학년"을 삭제하면 그 행이 사라지는데, 만약 보기가 2개뿐인 문항이면 보기를 하나 삭제하여 1개만 남았을 때, 나머지 1개도 삭제하면 문항 자체의 정보(q_content, q_type)도 함께 사라집니다. 즉, 보기만 지우고 싶었는데 문항까지 잃는 부작용이 생깁니다.

### 정규화 적용

**제1정규화(1NF) 위반 해소:** 한 셀에 여러 값을 넣지 않습니다. 만약 보기를 `options = "1학년,2학년,3학년"`처럼 콤마로 저장하면 1NF 위반입니다. "2학년을 선택한 사람은 몇 명?"을 집계하려면 문자열을 파싱해야 하고, LIKE 검색은 "1학년"을 찾을 때 "11학년"도 잡히는 등 신뢰할 수 없습니다.

**제2정규화(2NF) 위반 해소:** surveys 테이블의 title, description 등은 survey_id에만 종속됩니다. 만약 questions와 합쳐져 있으면 (survey_id, question_id) 복합키에서 title은 survey_id에만 부분 종속되므로 2NF 위반입니다.

**제3정규화(3NF) 위반 해소:** question_options의 content는 option_id에 종속이지, survey_id나 question_id를 거쳐 이행적으로 종속되면 안 됩니다. 테이블 분리로 이행 종속을 끊었습니다.

### 분리 결과

```
surveys(1) ──< questions(N) ──< question_options(N)
   1:N              1:N
```

- surveys: 설문 메타정보만 (제목, 카테고리, 기간)
- questions: 문항 정보만 (질문, 유형, 순서, 필수여부)
- question_options: 보기 정보만 (보기 내용, 순서)

제목을 바꾸면 surveys 1행만 UPDATE. 보기를 삭제해도 문항 정보는 유지. 주관식 문항은 question_options에 행이 0개이면 되고 NULL 행을 억지로 만들 필요 없음.

---

# 2. 응답 구조: survey_responses → answers → answer_options

## 왜 3개로 분리했는가

응답 데이터도 설문 구조와 동일한 계층입니다:

```
survey_responses(1) ──< answers(N) ──< answer_options(N)
  "누가 언제 응답"      "어떤 문항에 뭐라 답변"   "어떤 보기를 선택"
```

### 분리하지 않은 경우

```
| response_id | survey_id | respondent_id | question_id | answer_text | selected_option_id |
|-------------|-----------|---------------|-------------|-------------|--------------------|
| 1           | 101       | 42            | 5001        | NULL        | 9001               |
| 1           | 101       | 42            | 5001        | NULL        | 9002               |  ← 복수선택
| 1           | 101       | 42            | 5004        | 멘토링 필요 | NULL               |
```

**갱신 이상:** respondent_id를 수정하려면 해당 응답의 모든 행을 UPDATE해야 합니다.

**삽입 이상:** 복수선택(다중 선택) 문항에서 3개를 고르면 3행이 생기는데, 매 행마다 response_id, survey_id, respondent_id가 반복됩니다. 반복되는 데이터가 불일치하면 "같은 응답인데 응답자가 다른" 모순이 생깁니다.

### answer_options가 별도 테이블인 이유 — 다대다(M:N) 해소

핵심은 **복수선택(allow_multiple=true)** 때문입니다.

하나의 답변(answer)이 여러 보기(question_option)를 선택할 수 있고, 하나의 보기가 여러 답변에서 선택될 수 있습니다. 이것은 전형적인 **다대다 관계**입니다.

```
answers(M) ──< answer_options >── question_options(N)
               (교차 테이블)
```

관계형 DB에서 다대다를 직접 표현할 수 없으므로, 교차 테이블(junction table)인 `answer_options`로 해소합니다.

```
-- 단일선택: answer_options에 1행
answer_id=1, option_id=9001          ← "1학년" 선택

-- 복수선택: answer_options에 N행
answer_id=2, option_id=9003          ← "Java" 선택
answer_id=2, option_id=9004          ← "Python" 선택
answer_id=2, option_id=9005          ← "Go" 선택
```

**answer_options 없이 answers에 selected_option_id 컬럼을 넣으면?**

단일선택만 가능합니다. 복수선택을 지원하려면 같은 answer에 대해 행을 여러 개 만들어야 하는데, 그러면 answer_text 같은 정보가 중복됩니다 (2NF 위반).

또는 `selected_option_ids = "9003,9004,9005"` 같은 콤마 구분 문자열로 넣으면 1NF 위반이고, "Python을 선택한 사람 수"를 집계하는 게 불가능합니다.

---

# 3. 토큰 설계: token_transactions + users.token_balance

## 원장(Ledger) 패턴

`token_transactions`는 토큰의 **원장**입니다. 모든 토큰 변동을 빠짐없이 기록합니다.

```
| id | user_id | type              | amount | balance_after | related_survey_id |
|----|---------|-------------------|--------|---------------|-------------------|
| 1  | 42      | SIGNUP_BONUS      | +100   | 100           | NULL              |
| 2  | 42      | SURVEY_CREATE     | -24    | 76            | 101               |
| 3  | 42      | SURVEY_PARTICIPATE| +5     | 81            | 102               |
| 4  | 42      | RESULT_VIEW       | -15    | 66            | 103               |
```

### 왜 token_balance와 token_transactions 둘 다 필요한가

**1단계: 잔액(`token_balance`)은 당연히 필요합니다.**

메인 화면, 마이페이지, 설문 생성, 열람 구매 등 거의 모든 화면에서 잔액을 표시합니다. 유저당 잔액은 항상 1개이므로 users 테이블의 컬럼으로 저장합니다.

**2단계: 잔액만 있으면 추적이 불가능합니다.**

```
현재 잔액: 66토큰
→ "이 66토큰이 어디서 왔지?" 추적 불가능
→ "설문 101 생성 때 얼마 들었지?" 확인 불가능
→ "50명 보너스를 이미 줬는지?" 판단 불가능
→ 버그로 잔액이 꼬였을 때 검증 수단 없음
```

그래서 모든 토큰 변동을 기록하는 원장(`token_transactions`)이 필요합니다.

**3단계: 둘의 역할은 다릅니다.**

| | token_balance | token_transactions |
|---|---|---|
| 역할 | 현재 잔액 (원본) | 변동 이력 (추적) |
| 연산 | 현재 잔액에서 가감 | INSERT만 |
| 용도 | 잔액 조회, 잔액 부족 검사 | 감사 추적, 중복 지급 방지 |

token_balance는 원장의 캐시가 아닙니다. SUM 집계로 잔액을 계산하지 않고, **현재 잔액에서 직접 가감**합니다:

```java
// TokenService.record() — 항상 같은 트랜잭션
int after = user.getTokenBalance() + amount;  // 현재 잔액 + 변동분
user.updateTokenBalance(after);               // 잔액 갱신
tokenTransactionRepository.save(tx);          // 이력 기록
```

**정합성 보장:** 잔액 갱신과 이력 기록을 같은 트랜잭션에서 수행하므로, 하나가 실패하면 둘 다 롤백됩니다. 비관적 락(`findByIdForUpdate`)으로 동시 요청 시 잔액이 꼬이는 것도 방지합니다.

### balance_after 컬럼의 역할

각 거래 시점의 잔액을 기록합니다. 이것도 SUM으로 계산 가능한 파생값이지만 저장하는 이유:

1. **화면 표기:** "82 → 58" 같은 before/after를 보여줄 때 직전 거래의 balance_after가 before가 됩니다
2. **감사 추적:** 나중에 잔액 불일치가 의심될 때, 거래 순서대로 balance_after가 연속적인지 검증할 수 있습니다

### related_survey_id, related_response_id — NULL 허용 FK

모든 거래에 설문/응답이 관련되는 건 아닙니다:

| type | related_survey_id | related_response_id |
|------|-------------------|---------------------|
| SIGNUP_BONUS | NULL | NULL |
| SURVEY_CREATE | 설문 ID | NULL |
| SURVEY_PARTICIPATE | 설문 ID | 응답 ID |
| RESPONDENT_50_BONUS | 설문 ID | NULL |
| RESULT_SHARE | 설문 ID | NULL |
| RESULT_VIEW | 설문 ID | NULL |

가입 보너스는 설문과 무관하므로 둘 다 NULL. 참여 적립은 "어떤 설문의 어떤 응답"인지 둘 다 필요. NULL 허용 FK로 이 다양성을 수용합니다.

---

# 4. 회원-게스트 응답: survey_responses의 배타적 컬럼

```sql
respondent_id  BIGINT  NULL   -- 회원이면 채움, 게스트면 NULL
guest_key      VARCHAR NULL   -- 게스트면 채움, 회원이면 NULL
```

## 왜 이렇게 설계했는가

회원과 게스트가 **같은 테이블**에 응답을 저장합니다. 두 컬럼은 **상호배타적(mutually exclusive)** 으로 채워집니다:

```
| id | survey_id | respondent_id | guest_key        | earned_token |
|----|-----------|---------------|------------------|-------------|
| 1  | 101       | 42            | NULL             | 5           |  ← 회원
| 2  | 101       | NULL          | "b3f1c2a4-..."   | 0           |  ← 게스트
```

### 대안 1: 테이블 분리 (member_responses / guest_responses)

```sql
CREATE TABLE member_responses (respondent_id BIGINT NOT NULL, ...);
CREATE TABLE guest_responses (guest_key VARCHAR NOT NULL, ...);
```

**문제점:**
- "이 설문의 총 응답자 수"를 구하려면 두 테이블을 UNION해야 합니다
- "50명 보너스" 판정도 두 테이블을 합쳐야 합니다
- answers, answer_options의 FK가 어느 테이블을 가리키는지 복잡해집니다
- 응답 집계 쿼리가 전부 2배로 늘어납니다

### 대안 2: 게스트도 users에 저장

```
게스트 접속 → users에 임시 회원 생성 → respondent_id로 응답
```

**문제점:**
- users 테이블에 "진짜 회원"과 "일회성 게스트"가 섞임
- 게스트는 이메일/비밀번호가 없으므로 NOT NULL 제약을 풀어야 함
- 회원 수 집계가 부정확해짐
- 토큰 잔액 관리가 불필요한 행에도 적용됨

### 현재 설계가 최선인 이유

- 하나의 테이블에서 `COUNT(*)` 한 번으로 전체 응답자 수 집계
- 회원/게스트 구분은 `respondent_id IS NULL`로 판별
- users 테이블은 순수하게 "가입한 회원"만 관리
- FK 구조가 단순 (answers → survey_responses 하나만)

---

# 5. 유니크 제약조건 (UNIQUE KEY)

## 5.1 uk_users_email — 이메일 중복 방지

```sql
UNIQUE KEY uk_users_email (email)
```

**왜 필요한가:** 이메일이 로그인 ID입니다. 중복되면 "어느 계정으로 로그인하는 건지" 구분 불가.

**코드 검증만으로 충분하지 않은 이유:**
```
[요청 A] email 존재? → false    [요청 B] email 존재? → false
[요청 A] INSERT!                [요청 B] INSERT!  ← 둘 다 성공 = 중복
```
동시 요청에서 코드의 exists 체크는 **레이스 컨디션**에 취약합니다. DB 제약이 마지막 방어선입니다.

## 5.2 uk_refresh_token — 토큰 해시 유일성

```sql
UNIQUE KEY uk_refresh_token (token)
```

**왜 필요한가:** refresh 토큰의 SHA-256 해시가 중복되면, 로그아웃 시 다른 사용자의 토큰이 무효화될 수 있습니다. SHA-256 충돌 확률은 사실상 0이지만, DB 레벨에서 보장합니다.

**부가 효과:** 해시로 토큰을 조회할 때 UNIQUE 인덱스를 타므로 O(1) 조회 성능.

## 5.3 uk_response_member — 회원 1인 1회 참여

```sql
UNIQUE KEY uk_response_member (survey_id, respondent_id)
```

**왜 필요한가:** 같은 회원이 같은 설문에 두 번 응답하면:
- 응답자 수가 부풀려져서 50명 보너스를 부정하게 트리거
- 토큰을 중복 적립
- 결과 집계가 왜곡 (한 사람의 의견이 2배 반영)

**코드에서도 `existsBySurveyIdAndRespondentId()`로 확인하지만**, 동시 요청 방어는 DB 제약만 가능합니다.

## 5.4 uk_response_guest — 게스트 세션 1회 참여

```sql
UNIQUE KEY uk_response_guest (survey_id, guest_key)
```

**왜 필요한가:** 같은 게스트 세션에서 같은 설문에 중복 응답하는 걸 방지합니다.

**한계:** guest_key는 클라이언트가 생성하는 UUID이므로, 시크릿 모드나 다른 기기에서는 새 UUID가 생깁니다. 이것은 의도된 타협입니다 — 게스트에게 완벽한 중복 방지는 불가능하지만, 같은 세션 내에서의 중복(실수로 두 번 제출, 의도적 새로고침)은 막습니다.

## 5.5 uk_archive_view — 재열람 무료 보장

```sql
UNIQUE KEY uk_archive_view (viewer_id, survey_id)
```

**왜 필요한가:** 같은 사용자가 같은 설문을 두 번 구매하면 토큰이 이중으로 차감됩니다.

**비즈니스 규칙:** "한 번 구매하면 재열람은 무료." 이것을 DB 레벨에서 강제합니다. 코드에서 `findByViewerIdAndSurveyId()`로 기구매 여부를 확인하지만, 동시 요청 시 두 번 INSERT되면 30토큰이 빠지므로 UNIQUE 제약이 필수입니다.

---

# 6. 인덱스 설계

## 원칙: FK 컬럼에는 인덱스를 건다

MySQL에서 FK 제약을 걸면 자동으로 인덱스가 생성되기도 하지만, 명시적으로 인덱스를 선언하는 이유:

1. **JOIN 성능:** `surveys JOIN users ON creator_id = users.id` 할 때 creator_id에 인덱스가 없으면 전체 테이블 스캔
2. **CASCADE 검사:** 부모 행 삭제 시 자식 테이블에서 FK를 검사하는데, 인덱스 없으면 느림

## 테이블별 인덱스

### refresh_tokens

```sql
KEY idx_refresh_tokens_user (user_id)
```

**용도:** 로그아웃 시 "이 회원의 refresh 토큰 삭제", 비밀번호 변경 시 "이 회원의 모든 세션 무효화". user_id로 조회하므로 인덱스 필요.

### email_verifications

```sql
KEY idx_email_verifications_email (email)
```

**용도:** 인증번호 검증 시 `WHERE email = ? AND purpose = ?`로 조회. email에 인덱스가 없으면 전체 스캔.

**users.id가 아니라 email인 이유:** 회원가입 전에 인증이 이루어지므로 아직 user_id가 존재하지 않습니다. users 테이블에 FK를 걸 수 없어서 email 문자열로 연결합니다.

### surveys

```sql
KEY idx_surveys_creator (creator_id)
```

**용도:** "내가 만든 설문 목록"(`WHERE creator_id = ?`), 설문 삭제 시 소유자 확인. 마이페이지에서 자주 사용됩니다.

### questions

```sql
KEY idx_questions_survey (survey_id)
```

**용도:** "이 설문의 문항 목록"(`WHERE survey_id = ?`). 설문 상세/문항 조회/응답 제출 검증에서 매번 사용됩니다.

### question_options

```sql
KEY idx_question_options_question (question_id)
```

**용도:** "이 문항의 보기 목록"(`WHERE question_id = ?`). 문항 조회와 응답 검증에서 사용.

### survey_responses

```sql
KEY idx_survey_responses_survey (survey_id)
```

**용도:** "이 설문의 응답 수"(`COUNT(*) WHERE survey_id = ?`). 응답자 수 집계, 50명 보너스 판정, 결과 집계에서 핵심적으로 사용됩니다.

### answers

```sql
KEY idx_answers_response (response_id)
KEY idx_answers_question (question_id)
```

**두 개의 인덱스가 필요한 이유:**

- `idx_answers_response`: "이 응답의 답변 목록" — 응답 상세 조회
- `idx_answers_question`: "이 문항에 대한 모든 답변" — **결과 집계의 핵심**. 주관식 텍스트 목록을 가져올 때 `WHERE question_id = ?`로 조회합니다. 이 인덱스가 없으면 결과 집계 시 answers 전체 테이블을 스캔합니다.

### answer_options

```sql
KEY idx_answer_options_answer (answer_id)
```

**용도:** "이 답변에서 선택한 보기 목록" — 결과 집계에서 `JOIN answer_options`할 때 사용.

**option_id에도 인덱스가 있으면 좋은 이유:** 크로스탭 필터에서 `WHERE ao.option_id IN (9002, 9010)`으로 조회합니다. FK 제약(`fk_answer_options_option`)이 걸려 있으므로 MySQL이 자동으로 인덱스를 생성합니다.

### token_transactions

```sql
KEY idx_token_transactions_user (user_id)
```

**용도:** "이 회원의 거래내역". 마이페이지 토큰 히스토리에서 사용될 수 있습니다.

**related_survey_id에는 왜 명시적 인덱스가 없는가?** FK 제약이 걸려 있으므로 MySQL이 자동 생성합니다. `existsByRelatedSurveyIdAndType()` (50명 보너스 중복 체크)에서 사용되는데, 이 조회는 설문당 최대 1회이므로 자동 인덱스로 충분합니다.

---

# 7. 소프트 삭제 (is_deleted)

```sql
-- surveys 테이블
is_deleted  BOOLEAN  NOT NULL  DEFAULT FALSE
```

## 왜 물리 삭제(DELETE)를 안 하는가

설문을 DELETE하면:
1. **응답 데이터 손실:** 이 설문에 달린 survey_responses, answers, answer_options가 FK로 연결되어 있으므로 CASCADE DELETE되거나, FK 위반으로 삭제 자체가 실패합니다
2. **토큰 내역 고아화:** token_transactions의 related_survey_id가 가리킬 곳이 없어짐
3. **복구 불가:** 실수로 삭제하면 되돌릴 수 없음

## 조회 시 is_deleted 처리

모든 설문 조회 쿼리에 `WHERE is_deleted = false` 조건이 포함됩니다:

```java
Optional<Survey> findByIdAndIsDeletedFalse(Long id);  // Repository 메서드명에 녹아있음
```

삭제된 설문은 목록에 안 보이지만 DB에는 남아있어서, 응답 데이터와 토큰 내역의 참조 무결성이 유지됩니다.

---

# 8. 저장하지 않는 값 (유도 데이터)

정규화 원칙에 따라, **다른 데이터로 계산 가능한 값은 저장하지 않습니다.** 저장하면 원본이 바뀔 때 동기화 문제가 생깁니다.

| 화면 표기 | 계산 방법 | 저장하지 않는 이유 |
|-----------|----------|-------------------|
| 진행중/종료 | `오늘 > end_date` → CLOSED | 시간이 지나면 자동으로 바뀜. 저장하면 자정에 배치 돌려서 갱신해야 함 |
| 응답자 수 | `COUNT(survey_responses WHERE survey_id=?)` | 응답이 추가될 때마다 갱신해야 함. 동시 요청 시 카운트가 꼬일 수 있음 |
| 문항 수 | `questions`를 type별 COUNT | 문항이 변경되면 (현재는 수정 기능 없지만) 동기화 필요 |
| 보기별 % | `answer_options` GROUP BY | 응답이 추가될 때마다 재계산 필요. 필터 조건에 따라 값이 달라짐 |
| maxToken | `mc×1 + sa×2` | questions에서 매번 계산 |

**token_balance는 예외적으로 저장합니다** (5절 참조). 이것은 성능을 위한 의도적 반정규화이며, 트랜잭션으로 정합성을 보장합니다.

---

# 9. enum 컬럼 — VARCHAR + @Enumerated(STRING)

```sql
gender    VARCHAR(10)   -- MALE, FEMALE
job       VARCHAR(20)   -- STUDENT, UNIVERSITY_STUDENT, ...
category  VARCHAR(20)   -- CAREER, IT_AI, ...
type      VARCHAR(20)   -- MULTIPLE_CHOICE, SUBJECTIVE
type      VARCHAR(30)   -- SIGNUP_BONUS, SURVEY_CREATE, ...
purpose   VARCHAR(20)   -- SIGNUP, PASSWORD_RESET
```

## 왜 ORDINAL이 아닌 STRING인가

`@Enumerated(EnumType.ORDINAL)`을 쓰면 DB에 0, 1, 2 같은 숫자로 저장됩니다.

```java
// ORDINAL이면:
enum Gender { MALE, FEMALE }  // MALE=0, FEMALE=1

// 나중에 NON_BINARY를 추가하면:
enum Gender { MALE, NON_BINARY, FEMALE }  // MALE=0, NON_BINARY=1, FEMALE=2
// 기존 DB의 1은 FEMALE이었는데 이제 NON_BINARY로 해석됨 → 데이터 전부 꼬임
```

`STRING`이면 "MALE", "FEMALE" 문자열로 저장되므로 enum 순서가 바뀌어도 안전합니다. DB를 직접 조회할 때도 숫자가 아니라 의미 있는 문자열이 보여서 디버깅이 쉽습니다.

## 왜 별도 코드 테이블이 아닌가

직업 11종, 카테고리 9종은 **값이 고정**입니다. 운영 중에 동적으로 추가/삭제하지 않습니다. 별도 테이블(`job_codes`, `category_codes`)을 만들면 매번 JOIN이 필요한데, 값이 고정이라 불필요한 복잡성입니다. 앱과 서버가 enum으로 하드코딩해서 일치시킵니다.

---

# 10. refresh_tokens — 화이트리스트 방식

```sql
CREATE TABLE refresh_tokens (
    token       VARCHAR(255) NOT NULL  -- SHA-256 해시 (원문 아님!)
    ...
    UNIQUE KEY uk_refresh_token (token)
);
```

## 왜 별도 테이블인가

users에 `refresh_token` 컬럼을 넣으면 **1인 1세션**만 가능합니다. 여러 기기에서 로그인하면 마지막 기기의 토큰만 유효하고 이전 기기는 자동 로그아웃됩니다.

별도 테이블이면 user_id당 여러 행이 가능하므로 **다중 세션(멀티 디바이스)** 을 지원합니다.

## 왜 해시만 저장하는가

```
DB 유출 시:
- 원문 저장: 공격자가 refresh 토큰으로 access를 재발급받아 계정 탈취
- 해시 저장: 해시로는 원문을 복원할 수 없으므로 토큰 재사용 불가
```

SHA-256은 단방향이라 해시 → 원문 복원이 불가능합니다. 비밀번호를 BCrypt로 해시하는 것과 같은 원리입니다.

## 화이트리스트 vs 블랙리스트

**화이트리스트(현재 방식):** "있어야 유효." 로그아웃 = 행 삭제 → 이후 그 토큰으로 재발급 불가.

**블랙리스트:** "있으면 무효." 로그아웃 = 행 추가 → 이후 그 토큰이 블랙리스트에 있으면 거부.

화이트리스트가 더 안전합니다. 블랙리스트는 "로그아웃 안 한 토큰은 전부 유효"이므로, 토큰이 탈취되면 만료까지 계속 사용됩니다. 화이트리스트는 DB에 없으면 무조건 거부합니다.

---

# 11. email_verifications — users와 FK 없음

```sql
CREATE TABLE email_verifications (
    email  VARCHAR(255) NOT NULL  -- FK가 아님! 단순 문자열
    ...
    KEY idx_email_verifications_email (email)
);
```

## 왜 user_id FK가 아닌가

회원가입 흐름을 보면:

```
1. 인증번호 발송 (email_verifications INSERT)  ← 아직 users에 행이 없음!
2. 인증번호 검증 (verified=true)               ← 아직 users에 행이 없음!
3. 회원가입 (users INSERT)                      ← 이제야 users에 행이 생김
```

인증번호 발송 시점에는 회원이 아직 존재하지 않으므로, users.id를 FK로 참조할 수 없습니다. 그래서 email 문자열로 연결합니다.

비밀번호 재설정(`purpose=PASSWORD_RESET`)은 기존 회원이지만, 같은 테이블 구조를 공유하므로 통일성을 위해 email 기반입니다.

---

# 12. archive_views — 열람 이력

```sql
CREATE TABLE archive_views (
    viewer_id    BIGINT  NOT NULL,
    survey_id    BIGINT  NOT NULL,
    token_spent  INT     NOT NULL DEFAULT 15,
    viewed_at    DATETIME NOT NULL,
    UNIQUE KEY uk_archive_view (viewer_id, survey_id)
);
```

## 왜 별도 테이블인가

token_transactions에 `RESULT_VIEW` 거래가 기록되는데, 왜 archive_views가 또 필요한가?

1. **멱등성 판단:** `archive_views`에 (viewer, survey) 행이 있으면 "이미 구매한 상태"이므로 재과금하지 않습니다. token_transactions에서 판단하려면 `WHERE user_id=? AND type=RESULT_VIEW AND related_survey_id=?`로 매번 검색해야 하는데, 의미적으로 "열람권 보유 여부"와 "거래 이력"은 다른 개념입니다.

2. **마이페이지 열람 목록:** "내가 열람한 설문" 목록을 보여줄 때 `archive_views WHERE viewer_id=?`로 단순 조회. token_transactions에서 추출하려면 type 필터링이 추가로 필요합니다.

3. **UNIQUE 제약:** (viewer_id, survey_id) 유니크로 "1인 1구매"를 DB 레벨에서 강제. token_transactions는 같은 조합으로 여러 행이 있을 수 있어서(다른 type) 이 제약을 걸 수 없습니다.

## token_spent 컬럼

현재는 항상 15이지만, 향후 프로모션(할인 열람) 등이 도입되면 "이 사용자는 이 설문에 실제로 얼마를 냈는지" 기록이 필요할 수 있습니다. 또한 본인 설문/재열람 시 `tokenSpent=0`으로 응답하는 로직과도 연관됩니다.

---

# 요약: 정규화 수준

| 테이블 그룹 | 정규화 수준 | 비고 |
|------------|-----------|------|
| surveys → questions → question_options | **3NF** | 이행 종속 없음, 반복 그룹 없음 |
| survey_responses → answers → answer_options | **3NF** | answer_options = M:N 교차 테이블 |
| users + token_transactions | **역할 분리** | token_balance = 현재 잔액(가감 연산), token_transactions = 변동 이력(추적·감사) |
| refresh_tokens, email_verifications | **3NF** | users와 1:N (email_verifications는 FK 없이 email로 연결) |
| archive_views | **3NF** | 복합 UNIQUE로 1인 1구매 강제 |

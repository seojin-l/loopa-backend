# Loopa API 통합 명세 (Single Source of Truth)

> Loopa 백엔드 구현을 위한 **단일 기준 문서**입니다. 스키마 · 공통 규약 · 전체 API · 에러 코드 · 비즈니스 규칙을 한 파일에 담아 다른 문서 참조 없이 완결됩니다.

## 0. 구현 컨텍스트

- **스택:** Spring Boot 3.x / Java 17+ / Spring Data JPA(Hibernate) / MySQL 8 / Spring Security + JWT(access·refresh) / BCrypt.
- **응답 규약:** 모든 응답은 `{ isSuccess, code, message, result }` envelope로 감쌉니다(3절).
- **인증:** JWT Bearer. access 만료 시 refresh로 재발급. 일부 조회/참여 API는 게스트 허용(Authorization 헤더 유무로 분기).
- **토큰:** 모든 토큰 금액은 **서버가 계산**하며 클라이언트 값을 신뢰하지 않습니다. 토큰 이동은 `token_transactions`(원장)에 기록하고 `users.token_balance`(캐시)를 동일 트랜잭션에서 갱신합니다.
- **명명:** JSON은 camelCase, DB 컬럼은 snake_case. 일시는 ISO 8601.
- **구조:** 1 시스템 개요 → 2 스키마 → 3 공통 규약 → 4~8 도메인 API → 9 에러 코드 마스터 → 10 엔드포인트 인덱스.

---

# 1. 시스템 개요 & 비즈니스 규칙

**핵심 루프:** 진행 중 설문에 응답해 토큰을 벌고(회원), 모은 토큰으로 남의 종료 설문 결과를 열람(15토큰)한다. 작성자는 설문 생성 시 토큰을 쓰고, 응답 50명↑·아카이브 공유로 토큰을 번다.

## 사용자 유형
| 기능 | 회원 | 게스트 |
| --- | --- | --- |
| 설문 목록/상세/검색 조회 | O | O |
| 설문 응답(참여) | O (토큰 적립) | O (토큰 없음) |
| 설문 만들기 | O | X (로그인 필요) |
| 공공 아카이브 / 결과 열람 | O | X (로그인 필요) |
| 마이페이지 | O | X |

> 게스트는 `users`에 저장하지 않음(회원 전용 테이블). 게스트 응답은 `survey_responses`에 `respondent_id = NULL`, `guest_key = 세션키`로 저장. 회원 응답은 반대로 `respondent_id = 회원id`, `guest_key = NULL`. 둘은 상호배타로 채워짐.

## 토큰 정책
| 상황 | 변동 | tx type |
| --- | --- | --- |
| 회원가입 | +100 | `SIGNUP_BONUS` |
| 설문 등록 | −(10 + 객관식수×3 + 주관식수×5) | `SURVEY_CREATE` |
| 설문 참여 | +(답한 객관식수×1 + 답한 주관식수×2) | `SURVEY_PARTICIPATE` |
| 응답자 50명↑ | +10 (설문당 1회) | `RESPONDENT_50_BONUS` |
| 결과 공유 | +10 (공유 1건당) | `RESULT_SHARE` |
| 결과 열람 | −15 | `RESULT_VIEW` |

- **최대 획득 토큰** = 객관식수×1 + 주관식수×2
- **참여 적립** = 최대 − (건너뛴 객관식×1 + 건너뛴 주관식×2) = 답한 객관식×1 + 답한 주관식×2
- **생성 비용** = 10 + 객관식수×3 + 주관식수×5

## 핵심 비즈니스 규칙 (구현 시 강제)
- **진행중/종료 미저장** — `surveys.end_date`로 조회 시 계산 (`오늘 ≤ end_date` → `IN_PROGRESS`, else `CLOSED`).
- **응답자 수·집계·% 미저장** — 조회 시 `survey_responses`/`answers`/`answer_options` 집계.
- **회원 1인 1회 참여** — `UNIQUE(survey_id, respondent_id)`. 회원 응답의 `respondent_id`로 강제.
- **게스트 세션 1회 참여** — `UNIQUE(survey_id, guest_key)`. 같은 게스트 세션의 같은 설문 중복 응답 차단(응답 수 부풀리기·50명 보너스/공유 어뷰징 방어). 세션 밖(다른 기기·시크릿창)까지는 막지 못하는 의도적 타협.
- **자가 응답 금지** — `respondent_id ≠ survey.creator_id` (애플리케이션 레벨).
- **재열람 무료** — `UNIQUE(viewer_id, survey_id)`. 최초 구매 시에만 −15.
- **본인 설문 결과 무료 열람** — 작성자는 열람권 없이 결과 조회 가능.
- **공유는 종료된 본인 설문만** — 진행중/타인/기공유 설문 불가.
- **공유된 설문은 삭제 불가** — `shared_to_archive=true`면 삭제 차단(`SURVEY_005`).
- **소프트 삭제** — `surveys.is_deleted=true`. 물리 삭제 안 함(응답 보존).
- **카테고리(9)·직업(11)은 클라 하드코딩** — 조회 API 없음. 서버는 코드값 검증만.

---

# 2. 데이터베이스 스키마

## DDL (MySQL 8)

```sql
-- =========================================================
-- Loopa Schema (MySQL 8)
-- =========================================================

CREATE TABLE users (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
    email               VARCHAR(255) NOT NULL COMMENT '이메일(로그인 ID)',
    password            VARCHAR(255) NOT NULL COMMENT '비밀번호(해시)',
    gender              VARCHAR(10)  NOT NULL COMMENT '성별 MALE/FEMALE',
    age                 INT          NOT NULL COMMENT '나이',
    job                 VARCHAR(20)  NOT NULL COMMENT '직업(11종 enum)',
    token_balance       INT          NOT NULL DEFAULT 0 COMMENT '보유 토큰(원장 캐시)',
    created_at          DATETIME     NOT NULL COMMENT '생성일시',
    updated_at          DATETIME     NOT NULL COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) COMMENT='회원';

CREATE TABLE refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '토큰 ID',
    user_id     BIGINT       NOT NULL COMMENT '소유 회원 ID',
    token       VARCHAR(255) NOT NULL COMMENT 'refresh 토큰 해시(SHA-256, 원문 저장 안 함)',
    expires_at  DATETIME     NOT NULL COMMENT '만료일시',
    created_at  DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token (token),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) COMMENT='리프레시 토큰 화이트리스트(해시 저장, 로그아웃/재발급용)';

CREATE TABLE email_verifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '인증 ID',
    email       VARCHAR(255) NOT NULL COMMENT '이메일(회원 생성 전이라 FK 아님)',
    code        VARCHAR(10)  NOT NULL COMMENT '인증번호',
    purpose     VARCHAR(20)  NOT NULL COMMENT '목적 SIGNUP/PASSWORD_RESET',
    verified    BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '인증 완료 여부',
    attempt_count INT        NOT NULL DEFAULT 0 COMMENT '인증 시도 횟수(최대 5회)',
    invalidated BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '무효화 여부',
    expires_at  DATETIME     NOT NULL COMMENT '만료일시',
    created_at  DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_email_verifications_email (email)
) COMMENT='이메일 인증';

CREATE TABLE surveys (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '설문 ID',
    creator_id          BIGINT        NOT NULL COMMENT '작성자 ID',
    title               VARCHAR(100)  NOT NULL COMMENT '설문 제목',
    description         VARCHAR(1000) NULL     COMMENT '설문 소개',
    target              VARCHAR(50)   NULL     COMMENT '희망 설문 대상(자유입력)',
    category            VARCHAR(20)   NOT NULL COMMENT '카테고리(9종 enum)',
    estimated_minutes   INT           NULL     COMMENT '예상 소요 시간(분)',
    start_date          DATE          NOT NULL COMMENT '설문 시작일',
    end_date            DATE          NOT NULL COMMENT '마감일(상태 유도 기준)',
    shared_to_archive   BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '아카이브 공유 여부',
    archived_at         DATETIME      NULL     COMMENT '아카이브 공유일시',
    is_deleted          BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '삭제 여부(소프트 삭제)',
    created_at          DATETIME      NOT NULL COMMENT '게시일시',
    PRIMARY KEY (id),
    KEY idx_surveys_creator (creator_id),
    CONSTRAINT fk_surveys_creator FOREIGN KEY (creator_id) REFERENCES users (id)
) COMMENT='설문';

CREATE TABLE questions (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '문항 ID',
    survey_id       BIGINT       NOT NULL COMMENT '설문 ID',
    question_order  INT          NOT NULL COMMENT '문항 순서(설문 번호)',
    type            VARCHAR(20)  NOT NULL COMMENT '유형 MULTIPLE_CHOICE/SUBJECTIVE',
    content         VARCHAR(200) NOT NULL COMMENT '질문 내용',
    is_required     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '필수 응답 여부',
    allow_multiple  BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '복수 선택 허용(객관식만)',
    created_at      DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_questions_survey (survey_id),
    CONSTRAINT fk_questions_survey FOREIGN KEY (survey_id) REFERENCES surveys (id)
) COMMENT='문항';

CREATE TABLE question_options (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '보기 ID',
    question_id   BIGINT       NOT NULL COMMENT '문항 ID',
    option_order  INT          NOT NULL COMMENT '보기 순서',
    content       VARCHAR(200) NOT NULL COMMENT '보기 내용',
    created_at    DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_question_options_question (question_id),
    CONSTRAINT fk_question_options_question FOREIGN KEY (question_id) REFERENCES questions (id)
) COMMENT='보기(객관식 선택지)';

CREATE TABLE survey_responses (
    id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '응답 ID',
    survey_id      BIGINT   NOT NULL COMMENT '설문 ID',
    respondent_id  BIGINT   NULL     COMMENT '응답자 ID(회원, NULL=게스트)',
    guest_key      VARCHAR(64) NULL  COMMENT '게스트 세션 키(회원은 NULL)',
    earned_token   INT      NOT NULL DEFAULT 0 COMMENT '획득 토큰(게스트=0)',
    submitted_at   DATETIME NOT NULL COMMENT '제출일시',
    created_at     DATETIME NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_response_member (survey_id, respondent_id),
    UNIQUE KEY uk_response_guest  (survey_id, guest_key),
    KEY idx_survey_responses_survey (survey_id),
    CONSTRAINT fk_survey_responses_survey FOREIGN KEY (survey_id) REFERENCES surveys (id),
    CONSTRAINT fk_survey_responses_user  FOREIGN KEY (respondent_id) REFERENCES users (id)
) COMMENT='설문 응답';

CREATE TABLE answers (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '답변 ID',
    response_id  BIGINT   NOT NULL COMMENT '응답 ID',
    question_id  BIGINT   NOT NULL COMMENT '문항 ID',
    answer_text  TEXT     NULL     COMMENT '주관식 답변(객관식은 NULL)',
    created_at   DATETIME NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_answers_response (response_id),
    KEY idx_answers_question (question_id),
    CONSTRAINT fk_answers_response FOREIGN KEY (response_id) REFERENCES survey_responses (id),
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions (id)
) COMMENT='문항 답변';

CREATE TABLE answer_options (
    id         BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    answer_id  BIGINT NOT NULL COMMENT '답변 ID',
    option_id  BIGINT NOT NULL COMMENT '선택한 보기 ID',
    PRIMARY KEY (id),
    KEY idx_answer_options_answer (answer_id),
    CONSTRAINT fk_answer_options_answer FOREIGN KEY (answer_id) REFERENCES answers (id),
    CONSTRAINT fk_answer_options_option FOREIGN KEY (option_id) REFERENCES question_options (id)
) COMMENT='답변 선택보기(단일=1행, 복수선택=N행)';

CREATE TABLE token_transactions (
    id                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '거래 ID',
    user_id              BIGINT      NOT NULL COMMENT '회원 ID',
    type                 VARCHAR(30) NOT NULL COMMENT '거래 유형(6종)',
    amount               INT         NOT NULL COMMENT '변동량(부호 있음)',
    balance_after        INT         NOT NULL COMMENT '거래 후 잔액',
    related_survey_id    BIGINT      NULL     COMMENT '관련 설문 ID',
    related_response_id  BIGINT      NULL     COMMENT '관련 응답 ID',
    created_at           DATETIME    NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    KEY idx_token_transactions_user (user_id),
    CONSTRAINT fk_token_tx_user     FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_token_tx_survey   FOREIGN KEY (related_survey_id) REFERENCES surveys (id),
    CONSTRAINT fk_token_tx_response FOREIGN KEY (related_response_id) REFERENCES survey_responses (id)
) COMMENT='토큰 거래내역(원장)';

CREATE TABLE archive_views (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '열람 ID',
    viewer_id    BIGINT   NOT NULL COMMENT '열람자 ID',
    survey_id    BIGINT   NOT NULL COMMENT '설문 ID',
    token_spent  INT      NOT NULL DEFAULT 15 COMMENT '소모 토큰',
    viewed_at    DATETIME NOT NULL COMMENT '열람일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_archive_view (viewer_id, survey_id),
    CONSTRAINT fk_archive_views_user   FOREIGN KEY (viewer_id) REFERENCES users (id),
    CONSTRAINT fk_archive_views_survey FOREIGN KEY (survey_id) REFERENCES surveys (id)
) COMMENT='아카이브 열람(유료 열람 이력)';
```

## 토큰 정책 → 원장 매핑

`token_transactions.type` 하나로 9개 정책을 전부 흡수.

| 정책 | type | amount | 발생 시점 | user_id 대상 |
|------|------|--------|-----------|--------------|
| 가입 초기 토큰 | `SIGNUP_BONUS` | **+100** | 회원가입 완료 | 신규 회원 |
| 설문 등록 | `SURVEY_CREATE` | **−(10 + 3×객관식수 + 5×주관식수)** | 설문 생성 | 작성자 |
| 설문 참여 | `SURVEY_PARTICIPATE` | **+(1×응답한 객관식수 + 2×응답한 주관식수)** | 제출(회원만) | 참여자 |
| 응답자 50명↑ | `RESPONDENT_50_BONUS` | **+10** | 응답자 수(게스트 포함) 50 도달, 설문당 1회 | 작성자 |
| 결과 공유 | `RESULT_SHARE` | **+10** | 종료 설문 아카이브 공유 | 작성자 |
| 결과 열람 | `RESULT_VIEW` | **−15** | 남의 설문 열람(본인·재열람 제외) | 열람자 |

**규칙 상세**
- 게스트 참여는 `SURVEY_PARTICIPATE` 거래 자체가 안 생김(earned_token=0).
- 50명 보너스: 이미 해당 설문에 `RESPONDENT_50_BONUS` 거래가 있으면 재지급 안 함(존재 여부로 1회 보장).
- 열람: `archive_views`에 (viewer, survey) 행이 이미 있으면 무과금. 본인 설문(creator=viewer)은 애초에 과금 안 함.
- 모든 거래는 `users.token_balance` 갱신과 **같은 트랜잭션**에서 처리하고 `balance_after`에 결과 잔액 기록.

---

## 화면에 보이지만 저장하지 않는 "유도 데이터"

| 화면 표기 | 계산 방법 | 저장 안 하는 이유 |
|-----------|-----------|-------------------|
| 진행중 / 종료 | `오늘 > end_date` | 원칙 2 |
| 응답자 수 (예: 52명) | `COUNT(survey_responses WHERE survey_id=?)` (게스트 포함) | 원칙 3 |
| 보기별 % / 인원 (예: 1학년 12.5% 7명) | `answers`·`answer_options` GROUP BY | 원칙 3 |
| 문항 수 (객관식 3 / 주관식 1) | `questions` type별 COUNT | 파생값 |
| **결과 필터링(크로스탭)** | 객관식 보기 조건으로 응답자 집합을 좁힌 뒤 전 문항 재집계 | 조건이 무한 조합이라 저장 불가, 쿼리로만 처리 |

> 필터링: 결과 화면에서 특정 객관식 보기를 체크하면 "그 보기를 고른 응답자"로 좁혀 나머지 문항 분포를 다시 계산. **필터 조건은 객관식 보기만** 가능(주관식은 이산값이 없어 조건이 될 수 없음). 새 테이블 불필요.

---

## 도메인(Enum) 정의

**token_transactions.type (6)**: `SIGNUP_BONUS`, `SURVEY_CREATE`, `SURVEY_PARTICIPATE`, `RESPONDENT_50_BONUS`, `RESULT_SHARE`, `RESULT_VIEW`

**questions.type (2)**: `MULTIPLE_CHOICE`(객관식), `SUBJECTIVE`(주관식)

**email_verifications.purpose (2)**: `SIGNUP`, `PASSWORD_RESET`

**users.gender (2)**: `MALE`(남), `FEMALE`(여)

---

## 코드성 값 (11종 / 9종)

애플리케이션 enum(문자열 저장)으로 관리 — 값이 고정이라 코드 테이블 조인 불필요.

**users.job — 직업 11종**
| 코드 | 표기 |
|------|------|
| STUDENT | 학생 |
| UNIVERSITY_STUDENT | 대학생 |
| GRAD_STUDENT | 대학원생 |
| EMPLOYEE | 직장인 |
| TEACHER | 교사 |
| PROFESSOR | 교수 |
| FREELANCER | 프리랜서 |
| SELF_EMPLOYED | 자영업자 |
| PUBLIC_OFFICIAL | 공무원 |
| UNEMPLOYED | 무직 |
| ETC | 기타 |

**surveys.category — 카테고리 9종**
| 코드 | 표기 |
|------|------|
| CAREER | 학업·진로 |
| IT_AI | IT·AI |
| SERVICE_APP | 서비스·앱 |
| CONSUMER_MARKETING | 소비·마케팅 |
| GAME | 게임 |
| SCHOOL_LIFE | 학교생활 |
| DAILY | 일상 |
| PSYCHOLOGY | 심리 |
| ETC | 기타 |

---

---

# 3. 공통 API 규약

### Base URL
```
https://api.loopa.example
```

### 인증 방식
- JWT, `access` / `refresh` 분리.
- 인증이 필요한 API는 헤더에 `Authorization: Bearer {accessToken}`.
- **게스트 분기**: 일부 API는 `Authorization` 헤더가 **없어도** 호출 가능(게스트). 헤더가 있으면 회원으로 처리. 게스트는 응답 저장은 되나 토큰 거래가 발생하지 않음.
- `accessToken` 만료 시 `401 COMMON_401` → `POST /auth/token/refresh`로 재발급.

### 공통 응답 형식 (Response Envelope)
모든 응답은 아래 형태로 감쌉니다.
```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": { }
}
```

### 커서 기반 페이지네이션
- 목록 API 공통. Query로 `cursor`(선택, 첫 페이지는 생략), `size`(선택, 기본 20).
- 정렬은 **게시일 최신순 고정**.
- `result` 안에 `items`, `nextCursor`, `hasNext` 를 내려줍니다.
```json
"result": {
  "items": [ ],
  "nextCursor": "eyJpZCI6MTIzfQ==",
  "hasNext": true
}
```

### 공통 에러 코드
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 필수값 누락 또는 정규식/길이 제한 위반 |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료/위조 → 재발급 필요 |
| **403** | `COMMON_403` | 접근 권한이 없습니다. | 본인 소유가 아닌 리소스 접근 |
| **404** | `COMMON_404` | 대상을 찾을 수 없습니다. | 존재하지 않는 리소스 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

### 날짜 형식
모든 일시는 ISO 8601 (`2026-07-07T12:00:00`).

---

### 성공 HTTP 상태코드 규칙
- 컨트롤러는 `ResponseEntity<ApiResponse<T>>`로 반환하여 상태코드를 명시한다.
- **201 Created** — 리소스 생성: 회원가입, 설문 생성, 응답 제출, 열람 구매, 설문 공유
- **200 OK** — 그 외 전부: 조회, 로그인/재발급/로그아웃, 인증번호 발송·검증, 비밀번호 재설정, 설문 삭제(soft, result 반환)
- 에러는 4xx/5xx (9절 에러 코드 마스터의 HTTP 참조).
- body의 `code`는 상태코드와 별개의 애플리케이션 레벨 표식으로, **성공은 항상 `COMMON_200`**, 실패는 에러 코드다. (HTTP 201 + body `COMMON_200`은 계층이 달라 공존한다.)

---

# 4. AUTH 도메인

## 도메인 개요

| **도메인** | **Page (사용 위치)** | **API 명** | **담당자** | **Method** | **Endpoint** |
| --- | --- | --- | --- | --- | --- |
| Auth | 회원가입 / 비밀번호 찾기 | 이메일 인증번호 발송 |  | POST | `/auth/email-verifications` |
| Auth | 회원가입 / 비밀번호 찾기 | 이메일 인증번호 검증 |  | POST | `/auth/email-verifications/verify` |
| Auth | 회원가입 | 회원가입 |  | POST | `/auth/signup` |
| Auth | 로그인 | 로그인 |  | POST | `/auth/login` |
| Auth | 전역 | 액세스 토큰 재발급 |  | POST | `/auth/token/refresh` |
| Auth | 마이페이지 | 로그아웃 |  | POST | `/auth/logout` |
| Auth | 비밀번호 찾기 | 비밀번호 재설정 |  | POST | `/auth/password/reset` |

---

## AUTH-01 · 이메일 인증번호 발송

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 입력한 이메일로 6자리 인증번호를 발송합니다. 회원가입·비밀번호 찾기에서 공용으로 사용합니다. |
| **Note** | `purpose=SIGNUP`인 경우 이미 가입된 이메일이면 실패합니다. `purpose=PASSWORD_RESET`인 경우 가입되지 않은 이메일이면 실패합니다. 발송된 인증번호는 `expiresAt`까지만 유효합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Content-Type** | `application/json` | O | 데이터 타입 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
없음

#### 2-4. Request Body
JSON 예시
```json
{
  "email": "likelion@naver.com",
  "purpose": "SIGNUP"
}
```
필드 설명
| **Field** | **Type** | **Required** | **Description** | **Constraints** |
| --- | --- | --- | --- | --- |
| **email** | String | O | 인증받을 이메일 | Not Blank, Email 형식, max 255 |
| **purpose** | String | O | 인증 목적 | `SIGNUP` \| `PASSWORD_RESET` |

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "인증번호가 발송되었습니다.",
  "result": {
    "email": "likelion@naver.com",
    "purpose": "SIGNUP",
    "expiresAt": "2026-07-07T12:05:00"
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.email** | String | 인증번호가 발송된 이메일 |
| **result.purpose** | String | 인증 목적 |
| **result.expiresAt** | String | 인증번호 만료 시각 (ISO 8601) |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "AUTH_001",
  "message": "이미 사용 중인 이메일입니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 이메일 형식 오류, `purpose` 값 오류 |
| **409** | `AUTH_001` | 이미 사용 중인 이메일입니다. | `SIGNUP`인데 이미 가입된 이메일 |
| **404** | `AUTH_007` | 등록되지 않은 이메일입니다. | `PASSWORD_RESET`인데 미가입 이메일 |
| **429** | `AUTH_010` | 잠시 후 다시 시도해주세요. | 짧은 시간 내 재발송 시도(레이트 리밋) |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 메일 발송 실패 등 |

---

## AUTH-02 · 이메일 인증번호 검증

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 발송된 인증번호와 사용자가 입력한 값을 대조합니다. 일치 시 해당 이메일의 인증 상태를 `완료`로 저장합니다. |
| **Note** | 검증 성공 시 서버가 인증 완료 상태를 기록하며, 이후 회원가입/비밀번호 재설정 API는 이 상태를 확인합니다. 별도의 검증 토큰을 반환하지 않고 이메일 기준으로 상태를 관리합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Content-Type** | `application/json` | O | 데이터 타입 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
없음

#### 2-4. Request Body
JSON 예시
```json
{
  "email": "likelion@naver.com",
  "code": "123456",
  "purpose": "SIGNUP"
}
```
필드 설명
| **Field** | **Type** | **Required** | **Description** | **Constraints** |
| --- | --- | --- | --- | --- |
| **email** | String | O | 인증 대상 이메일 | Not Blank, Email 형식 |
| **code** | String | O | 사용자가 입력한 인증번호 | Not Blank, 숫자 6자리 |
| **purpose** | String | O | 인증 목적 | `SIGNUP` \| `PASSWORD_RESET` |

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "인증번호가 일치합니다.",
  "result": {
    "email": "likelion@naver.com",
    "purpose": "SIGNUP",
    "verified": true
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.email** | String | 인증된 이메일 |
| **result.purpose** | String | 인증 목적 |
| **result.verified** | Boolean | 인증 완료 여부 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "AUTH_002",
  "message": "인증번호가 일치하지 않습니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 필수값 누락, 형식 오류 |
| **400** | `AUTH_002` | 인증번호가 일치하지 않습니다. | 잘못된 인증번호 입력 |
| **400** | `AUTH_003` | 인증번호가 만료되었습니다. | `expiresAt` 경과 또는 무효화됨 → 재발송 필요 |
| **429** | `AUTH_009` | 인증 시도 횟수를 초과했습니다. 인증번호를 재발송해주세요. | 5회 이상 실패 → 재발송 필요 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## AUTH-03 · 회원가입

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 이메일 인증이 완료된 사용자의 회원 정보를 등록합니다. |
| **Note** | 사전에 `purpose=SIGNUP`으로 이메일 인증이 완료되어 있어야 합니다. 가입 완료 시 **가입 축하 토큰 100개가 자동 지급**됩니다(토큰 거래유형 `SIGNUP_BONUS`). 가입 후에는 로그인 화면으로 이동하므로 본 API는 토큰(access/refresh)을 반환하지 않습니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Content-Type** | `application/json` | O | 데이터 타입 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
없음

#### 2-4. Request Body
JSON 예시
```json
{
  "email": "likelion@naver.com",
  "password": "password1234",
  "gender": "MALE",
  "age": 21,
  "job": "UNIVERSITY_STUDENT",
  "agreedToTerms": true
}
```
필드 설명
| **Field** | **Type** | **Required** | **Description** | **Constraints** |
| --- | --- | --- | --- | --- |
| **email** | String | O | 이메일(인증 완료된 값) | Not Blank, Email 형식, max 255 |
| **password** | String | O | 비밀번호 | Not Blank, min 8, max 100 |
| **gender** | String | O | 성별 | `MALE` \| `FEMALE` |
| **age** | Integer | O | 나이 | 1 이상 |
| **job** | String | O | 직업 | 직업 코드 11종 중 1 (예: `UNIVERSITY_STUDENT`) |
| **agreedToTerms** | Boolean | O | 이용약관·개인정보처리방침 동의 | `true`만 허용 |

### 3. Response
#### 3-1. Success Response

**HTTP Status: `201 Created`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "회원가입이 완료되었습니다.",
  "result": {
    "userId": 1,
    "email": "likelion@naver.com",
    "tokenBalance": 100,
    "createdAt": "2026-07-07T12:00:00"
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.userId** | Long | 회원 ID |
| **result.email** | String | 이메일 |
| **result.tokenBalance** | Integer | 지급된 초기 보유 토큰 (100) |
| **result.createdAt** | String | 가입 시각 (ISO 8601) |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "AUTH_004",
  "message": "이메일 인증이 완료되지 않았습니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 필수값 누락, 정규식/길이 위반, 미동의 |
| **400** | `AUTH_004` | 이메일 인증이 완료되지 않았습니다. | 인증 없이 가입 시도 → 인증 선행 필요 |
| **409** | `AUTH_001` | 이미 사용 중인 이메일입니다. | 중복 이메일 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## AUTH-04 · 로그인

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 이메일·비밀번호로 로그인하고 access/refresh 토큰을 발급합니다. |
| **Note** | 이메일 미가입/비밀번호 불일치는 계정 노출 방지를 위해 동일한 메시지로 응답합니다. 게스트 참여는 로그인 없이 진행되므로 본 API를 호출하지 않습니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Content-Type** | `application/json` | O | 데이터 타입 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
없음

#### 2-4. Request Body
JSON 예시
```json
{
  "email": "likelion@naver.com",
  "password": "password1234"
}
```
필드 설명
| **Field** | **Type** | **Required** | **Description** | **Constraints** |
| --- | --- | --- | --- | --- |
| **email** | String | O | 이메일 | Not Blank, Email 형식 |
| **password** | String | O | 비밀번호 | Not Blank |

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "userId": 1,
    "email": "likelion@naver.com",
    "tokenType": "Bearer",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6..."
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.userId** | Long | 회원 ID |
| **result.email** | String | 이메일 |
| **result.tokenType** | String | 토큰 타입 (`Bearer` 고정) |
| **result.accessToken** | String | 액세스 토큰 |
| **result.refreshToken** | String | 리프레시 토큰 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "AUTH_005",
  "message": "이메일 또는 비밀번호가 일치하지 않습니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 필수값 누락 |
| **401** | `AUTH_005` | 이메일 또는 비밀번호가 일치하지 않습니다. | 미가입 이메일 또는 비밀번호 불일치 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## AUTH-05 · 액세스 토큰 재발급

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 유효한 refreshToken으로 새로운 access/refresh 토큰을 재발급합니다. |
| **Note** | 재발급 시 refreshToken도 함께 갱신(rotation)됩니다. 기존 refreshToken은 무효화됩니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Content-Type** | `application/json` | O | 데이터 타입 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
없음

#### 2-4. Request Body
JSON 예시
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6..."
}
```
필드 설명
| **Field** | **Type** | **Required** | **Description** | **Constraints** |
| --- | --- | --- | --- | --- |
| **refreshToken** | String | O | 재발급용 리프레시 토큰 | Not Blank |

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "tokenType": "Bearer",
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6..."
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.tokenType** | String | 토큰 타입 (`Bearer` 고정) |
| **result.accessToken** | String | 신규 액세스 토큰 |
| **result.refreshToken** | String | 신규 리프레시 토큰 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "AUTH_006",
  "message": "유효하지 않은 리프레시 토큰입니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | refreshToken 누락 |
| **401** | `AUTH_006` | 유효하지 않은 리프레시 토큰입니다. | 만료/위조/무효화된 토큰 → 재로그인 필요 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## AUTH-06 · 로그아웃

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 현재 세션의 refreshToken을 무효화하여 로그아웃합니다. |
| **Note** | accessToken으로 사용자를 식별하고, body의 refreshToken을 무효화합니다. 클라이언트는 저장 중인 토큰을 폐기합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Content-Type** | `application/json` | O | 데이터 타입 |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
없음

#### 2-4. Request Body
JSON 예시
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6..."
}
```
필드 설명
| **Field** | **Type** | **Required** | **Description** | **Constraints** |
| --- | --- | --- | --- | --- |
| **refreshToken** | String | O | 무효화할 리프레시 토큰 | Not Blank |

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "로그아웃되었습니다.",
  "result": null
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result** | Null | 반환값 없음 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "COMMON_401",
  "message": "인증이 필요합니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **401** | `AUTH_006` | 유효하지 않은 리프레시 토큰입니다. | 이미 무효화되었거나 잘못된 refreshToken |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## AUTH-07 · 비밀번호 재설정

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 이메일 인증을 마친 사용자의 비밀번호를 새 값으로 변경합니다. |
| **Note** | 사전에 `purpose=PASSWORD_RESET`으로 이메일 인증이 완료되어 있어야 합니다. 비로그인 상태(비밀번호 찾기)에서 호출되며, 이메일로 대상 회원을 식별합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Content-Type** | `application/json` | O | 데이터 타입 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
없음

#### 2-4. Request Body
JSON 예시
```json
{
  "email": "likelion@naver.com",
  "newPassword": "newPassword1234"
}
```
필드 설명
| **Field** | **Type** | **Required** | **Description** | **Constraints** |
| --- | --- | --- | --- | --- |
| **email** | String | O | 대상 이메일(인증 완료된 값) | Not Blank, Email 형식 |
| **newPassword** | String | O | 새 비밀번호 | Not Blank, min 8, max 100 |

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "비밀번호가 변경되었습니다.",
  "result": {
    "userId": 1,
    "email": "likelion@naver.com",
    "updatedAt": "2026-07-07T12:10:00"
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.userId** | Long | 회원 ID |
| **result.email** | String | 이메일 |
| **result.updatedAt** | String | 변경 시각 (ISO 8601) |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "AUTH_004",
  "message": "이메일 인증이 완료되지 않았습니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 필수값 누락, 비밀번호 길이 위반 |
| **400** | `AUTH_004` | 이메일 인증이 완료되지 않았습니다. | 인증 없이 재설정 시도 |
| **404** | `AUTH_007` | 등록되지 않은 이메일입니다. | 미가입 이메일 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## AUTH 도메인 에러 코드 정리

| **Error Code** | **HTTP** | **Message** |
| --- | --- | --- |
| `AUTH_001` | 409 | 이미 사용 중인 이메일입니다. |
| `AUTH_002` | 400 | 인증번호가 일치하지 않습니다. |
| `AUTH_003` | 400 | 인증번호가 만료되었습니다. |
| `AUTH_004` | 400 | 이메일 인증이 완료되지 않았습니다. |
| `AUTH_005` | 401 | 이메일 또는 비밀번호가 일치하지 않습니다. |
| `AUTH_006` | 401 | 유효하지 않은 리프레시 토큰입니다. |
| `AUTH_007` | 404 | 등록되지 않은 이메일입니다. |
| `AUTH_008` | 401 | 만료된 리프레시 토큰입니다. |
| `AUTH_009` | 429 | 인증 시도 횟수를 초과했습니다. 인증번호를 재발송해주세요. |
| `AUTH_010` | 429 | 잠시 후 다시 시도해주세요. |

---

# 5. User 도메인

## 도메인 개요

| **도메인** | **Page (사용 위치)** | **API 명** | **담당자** | **Method** | **Endpoint** |
| --- | --- | --- | --- | --- | --- |
| User | 메인 / 마이페이지 | 내 정보 조회 |  | GET | `/users/me` |
| User | 마이페이지 · 내가 등록한 설문 | 내가 등록한 설문 목록 |  | GET | `/users/me/surveys` |
| User | 마이페이지 · 열람한 설문 | 내가 열람한 설문 목록 |  | GET | `/users/me/viewed-surveys` |

---

## USER-01 · 내 정보 조회

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 로그인한 회원의 기본 정보와 현재 보유 토큰을 조회합니다. |
| **Note** | 메인·마이페이지 상단의 인사말("안녕하세요, {email}님")과 보유 토큰 표기에 사용됩니다. 프로필 이미지는 사용하지 않습니다(기본 아바타 고정). |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
없음

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "userId": 1,
    "email": "likelion@gmail.com",
    "tokenBalance": 124,
    "gender": "MALE",
    "age": 21,
    "job": "UNIVERSITY_STUDENT",
    "createdAt": "2026-01-01T12:00:00"
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.userId** | Long | 회원 ID |
| **result.email** | String | 이메일 |
| **result.tokenBalance** | Integer | 현재 보유 토큰 |
| **result.gender** | String | 성별 (`MALE` \| `FEMALE`) |
| **result.age** | Integer | 나이 |
| **result.job** | String | 직업 코드 (11종 중 1) |
| **result.createdAt** | String | 가입 시각 (ISO 8601) |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "COMMON_401",
  "message": "인증이 필요합니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료/위조 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## USER-02 · 내가 등록한 설문 목록

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 로그인한 회원이 등록한 설문들을 미리보기 형태로 최신순 조회합니다. |
| **Note** | 각 설문의 진행중/종료 상태는 마감일(`end_date`) 기준으로 서버에서 계산해 내려줍니다. 응답자 수는 게스트 응답을 포함합니다. 커서 기반 페이지네이션을 사용합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **cursor** | `eyJpZCI6MTIzfQ==` | X | 다음 페이지 커서. 첫 페이지는 생략 |
| **size** | `20` | X | 페이지 크기 (기본 20) |

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "items": [
      {
        "surveyId": 101,
        "title": "대학생 AI 활용 실태 조사",
        "category": "IT_AI",
        "target": "대학생",
        "respondentCount": 52,
        "status": "IN_PROGRESS",
        "createdAt": "2026-01-01"
      },
      {
        "surveyId": 98,
        "title": "대학생 공모전 참여 경험 조사",
        "category": "CAREER",
        "target": "대학생",
        "respondentCount": 52,
        "status": "CLOSED",
        "createdAt": "2026-01-01"
      }
    ],
    "nextCursor": "eyJpZCI6OTh9",
    "hasNext": true
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.items[].surveyId** | Long | 설문 ID |
| **result.items[].title** | String | 설문 제목 |
| **result.items[].category** | String | 카테고리 코드 (9종 중 1) |
| **result.items[].target** | String | 희망 설문 대상 |
| **result.items[].respondentCount** | Integer | 응답자 수 (게스트 포함) |
| **result.items[].status** | String | 진행 상태 (`IN_PROGRESS` \| `CLOSED`) |
| **result.items[].createdAt** | String | 게시일 (yyyy-MM-dd) |
| **result.nextCursor** | String | 다음 페이지 커서 (없으면 null) |
| **result.hasNext** | Boolean | 다음 페이지 존재 여부 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "COMMON_401",
  "message": "인증이 필요합니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 커서 형식 오류, size 범위 위반 |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## USER-03 · 내가 열람한 설문 목록

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 로그인한 회원이 토큰을 소모하여 공공 아카이브에서 열람한 설문들을 최신순 조회합니다. |
| **Note** | 유료로 열람한(15토큰 소모) 설문만 포함됩니다. 본인이 등록한 설문은 열람 대상이 아니므로 제외됩니다. 미리보기 항목은 등록한 설문 목록과 달리 진행/종료 상태를 표기하지 않습니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **cursor** | `eyJpZCI6MTIzfQ==` | X | 다음 페이지 커서. 첫 페이지는 생략 |
| **size** | `20` | X | 페이지 크기 (기본 20) |

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "items": [
      {
        "surveyId": 77,
        "title": "대학생 AI 활용 실태 조사",
        "category": "IT_AI",
        "target": "대학생",
        "respondentCount": 52,
        "viewedAt": "2026-01-05T09:30:00",
        "createdAt": "2026-01-01"
      }
    ],
    "nextCursor": null,
    "hasNext": false
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.items[].surveyId** | Long | 설문 ID |
| **result.items[].title** | String | 설문 제목 |
| **result.items[].category** | String | 카테고리 코드 (9종 중 1) |
| **result.items[].target** | String | 희망 설문 대상 |
| **result.items[].respondentCount** | Integer | 응답자 수 (게스트 포함) |
| **result.items[].viewedAt** | String | 열람 시각 (ISO 8601) |
| **result.items[].createdAt** | String | 게시일 (yyyy-MM-dd) |
| **result.nextCursor** | String | 다음 페이지 커서 (없으면 null) |
| **result.hasNext** | Boolean | 다음 페이지 존재 여부 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "COMMON_401",
  "message": "인증이 필요합니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 커서 형식 오류, size 범위 위반 |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## User 도메인 참고

- 본 도메인은 별도 도메인 에러 코드 없이 공통 에러 코드(`COMMON_*`)만 사용합니다.
- `status` 값 정의: `IN_PROGRESS`(진행 중, 오늘 ≤ 마감일), `CLOSED`(종료, 오늘 > 마감일). 서버 계산 값이며 DB에 저장되지 않습니다.
- `category`, `job`은 코드값으로 내려가며, 화면 표기 라벨(예: `IT_AI` → "IT·AI")은 클라이언트가 매핑합니다.

---

# 6. Survey 도메인

## 도메인 개요

| **도메인** | **Page (사용 위치)** | **API 명** | **담당자** | **Method** | **Endpoint** |
| --- | --- | --- | --- | --- | --- |
| Survey | 메인 · 참여 가능한 설문 / 더보기 | 참여 가능한 설문 목록 조회 |  | GET | `/surveys` |
| Survey | 설문 참여(상세) 화면 | 설문 상세 조회 |  | GET | `/surveys/{surveyId}` |
| Survey | 설문 진행 화면 | 설문 문항 조회 |  | GET | `/surveys/{surveyId}/questions` |
| Survey | 설문 만들기 | 설문 생성 |  | POST | `/surveys` |
| Survey | 마이페이지 · 휴지통 | 설문 삭제 |  | DELETE | `/surveys/{surveyId}` |

---

## SURVEY-01 · 참여 가능한 설문 목록 조회

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 현재 참여 가능한(진행 중) 설문을 미리보기 형태로 최신순 조회합니다. 카테고리 필터·제목 검색을 지원합니다. |
| **Note** | 진행 중(오늘 ≤ 마감일)인 설문만 반환합니다. `category`·`keyword`는 각각 생략 가능하며 함께 조합할 수 있습니다. 로그인 상태로 호출하면 본인이 등록한 설문은 목록에서 제외됩니다(자기 설문 참여 불가). 획득 가능 토큰은 문항 구성으로 서버에서 계산합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | X | 로그인 시 회원(본인 설문 제외), 미포함 시 게스트 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **category** | `IT_AI` | X | 카테고리 코드로 필터 (미지정 시 전체) |
| **keyword** | `대학생` | X | 설문 제목 검색어 |
| **cursor** | `eyJpZCI6MTIzfQ==` | X | 다음 페이지 커서. 첫 페이지는 생략 |
| **size** | `20` | X | 페이지 크기 (기본 20) |

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "items": [
      {
        "surveyId": 101,
        "title": "대학생 AI 활용 실태 조사",
        "category": "IT_AI",
        "target": "대학생",
        "estimatedMinutes": 3,
        "maxToken": 18,
        "createdAt": "2026-01-01"
      }
    ],
    "nextCursor": "eyJpZCI6MTAxfQ==",
    "hasNext": true
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.items[].surveyId** | Long | 설문 ID |
| **result.items[].title** | String | 설문 제목 |
| **result.items[].category** | String | 카테고리 코드 (9종 중 1) |
| **result.items[].target** | String | 희망 설문 대상 |
| **result.items[].estimatedMinutes** | Integer | 예상 소요 시간(분) |
| **result.items[].maxToken** | Integer | 획득 가능 토큰 (객관식 수×1 + 주관식 수×2) |
| **result.items[].createdAt** | String | 게시일 (yyyy-MM-dd) |
| **result.nextCursor** | String | 다음 페이지 커서 (없으면 null) |
| **result.hasNext** | Boolean | 다음 페이지 존재 여부 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "COMMON_400",
  "message": "잘못된 요청입니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 커서 형식 오류, 존재하지 않는 category 코드, size 범위 위반 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## SURVEY-02 · 설문 상세 조회

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 설문 참여 전 상세 정보를 조회합니다. 미리보기 정보와 소개, 문항 수, 기간, 최대 획득 토큰을 포함합니다. |
| **Note** | 문항 수는 객관식/주관식으로 구분해 내려갑니다. 최대 획득 토큰과 응답자 수는 서버 계산 값입니다. 실제 문항·보기 데이터는 [SURVEY-03]에서 조회합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | X | 로그인 시 회원, 미포함 시 게스트 |

#### 2-2. Path Variable
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **surveyId** | `101` | O | 조회할 설문 ID |

#### 2-3. Query String
없음

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "surveyId": 101,
    "title": "대학생 공모전 참여 경험 조사",
    "category": "CAREER",
    "target": "대학생",
    "description": "대학생들의 공모전 참여 경험과 참여를 방해하는 요인을 조사하기 위한 설문입니다.",
    "estimatedMinutes": 5,
    "startDate": "2026-01-01",
    "endDate": "2026-01-15",
    "status": "IN_PROGRESS",
    "questionCount": {
      "multipleChoice": 3,
      "subjective": 1,
      "total": 4
    },
    "maxToken": 5,
    "respondentCount": 52,
    "createdAt": "2026-01-01"
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.surveyId** | Long | 설문 ID |
| **result.title** | String | 설문 제목 |
| **result.category** | String | 카테고리 코드 |
| **result.target** | String | 희망 설문 대상 |
| **result.description** | String | 설문 소개 |
| **result.estimatedMinutes** | Integer | 예상 소요 시간(분) |
| **result.startDate** | String | 설문 시작일 (yyyy-MM-dd) |
| **result.endDate** | String | 마감일 (yyyy-MM-dd) |
| **result.status** | String | 진행 상태 (`IN_PROGRESS` \| `CLOSED`) |
| **result.questionCount.multipleChoice** | Integer | 객관식 문항 수 |
| **result.questionCount.subjective** | Integer | 주관식 문항 수 |
| **result.questionCount.total** | Integer | 전체 문항 수 |
| **result.maxToken** | Integer | 최대 획득 가능 토큰 |
| **result.respondentCount** | Integer | 응답자 수 (게스트 포함) |
| **result.createdAt** | String | 게시일 (yyyy-MM-dd) |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "SURVEY_001",
  "message": "존재하지 않는 설문입니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **404** | `SURVEY_001` | 존재하지 않는 설문입니다. | 잘못된 surveyId 또는 삭제된 설문 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## SURVEY-03 · 설문 문항 조회

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 설문 진행 화면에서 응답할 문항과 보기를 순서대로 조회합니다. |
| **Note** | 문항 순서(`order`)대로 정렬되어 내려갑니다. 주관식 문항의 `options`는 빈 배열입니다. 실제 응답 제출은 Response 도메인에서 처리합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | X | 로그인 시 회원, 미포함 시 게스트 |

#### 2-2. Path Variable
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **surveyId** | `101` | O | 조회할 설문 ID |

#### 2-3. Query String
없음

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "surveyId": 101,
    "questions": [
      {
        "questionId": 5001,
        "order": 1,
        "type": "MULTIPLE_CHOICE",
        "content": "현재 학년은 어떻게 되시나요?",
        "isRequired": true,
        "allowMultiple": false,
        "options": [
          { "optionId": 9001, "order": 1, "content": "1학년" },
          { "optionId": 9002, "order": 2, "content": "2학년" },
          { "optionId": 9003, "order": 3, "content": "3학년" },
          { "optionId": 9004, "order": 4, "content": "4학년" }
        ]
      },
      {
        "questionId": 5002,
        "order": 2,
        "type": "SUBJECTIVE",
        "content": "학교에서 공모전 참여를 위해 가장 필요하다고 생각하는 지원은 무엇인가요?",
        "isRequired": false,
        "allowMultiple": false,
        "options": []
      }
    ]
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.surveyId** | Long | 설문 ID |
| **result.questions[].questionId** | Long | 문항 ID |
| **result.questions[].order** | Integer | 문항 순서 |
| **result.questions[].type** | String | 문항 유형 (`MULTIPLE_CHOICE` \| `SUBJECTIVE`) |
| **result.questions[].content** | String | 질문 내용 |
| **result.questions[].isRequired** | Boolean | 필수 응답 여부 |
| **result.questions[].allowMultiple** | Boolean | 복수 선택 허용 여부 (객관식만 유효) |
| **result.questions[].options[].optionId** | Long | 보기 ID |
| **result.questions[].options[].order** | Integer | 보기 순서 |
| **result.questions[].options[].content** | String | 보기 내용 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "SURVEY_001",
  "message": "존재하지 않는 설문입니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **404** | `SURVEY_001` | 존재하지 않는 설문입니다. | 잘못된 surveyId 또는 삭제된 설문 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## SURVEY-04 · 설문 생성

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 기본 정보와 문항·보기를 한 번에 등록하여 설문을 생성합니다. |
| **Note** | 로그인 필수(게스트 생성 불가). 생성 시 **토큰이 차감**됩니다: `10 + 객관식 수×3 + 주관식 수×5`. 보유 토큰이 부족하면 실패합니다. 문항은 최소 1개 이상이어야 하며, 객관식 문항은 보기가 최소 1개 이상이어야 합니다. 마감일은 시작일 이후여야 합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Content-Type** | `application/json` | O | 데이터 타입 |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
없음

#### 2-4. Request Body
JSON 예시
```json
{
  "title": "대학생 공모전 참여 경험 조사",
  "description": "대학생들의 공모전 참여 경험과 참여를 방해하는 요인을 조사하기 위한 설문입니다.",
  "target": "대학생",
  "category": "CAREER",
  "estimatedMinutes": 5,
  "startDate": "2026-01-01",
  "endDate": "2026-01-15",
  "questions": [
    {
      "order": 1,
      "type": "MULTIPLE_CHOICE",
      "content": "현재 학년은 어떻게 되시나요?",
      "isRequired": true,
      "allowMultiple": false,
      "options": [
        { "order": 1, "content": "1학년" },
        { "order": 2, "content": "2학년" },
        { "order": 3, "content": "3학년" },
        { "order": 4, "content": "4학년" }
      ]
    },
    {
      "order": 2,
      "type": "SUBJECTIVE",
      "content": "학교에서 공모전 참여를 위해 가장 필요하다고 생각하는 지원은 무엇인가요?",
      "isRequired": false,
      "allowMultiple": false,
      "options": []
    }
  ]
}
```
필드 설명
| **Field** | **Type** | **Required** | **Description** | **Constraints** |
| --- | --- | --- | --- | --- |
| **title** | String | O | 설문 제목 | Not Blank, max 100 |
| **description** | String | X | 설문 소개 | max 1000 |
| **target** | String | X | 희망 설문 대상 | max 50 |
| **category** | String | O | 카테고리 코드 | 9종 중 1 |
| **estimatedMinutes** | Integer | X | 예상 소요 시간(분) | 1 이상 |
| **startDate** | String | O | 설문 시작일 | yyyy-MM-dd |
| **endDate** | String | O | 마감일 | yyyy-MM-dd, startDate 이후 |
| **questions** | Array | O | 문항 목록 | 최소 1개 |
| **questions[].order** | Integer | O | 문항 순서 | 1 이상 |
| **questions[].type** | String | O | 문항 유형 | `MULTIPLE_CHOICE` \| `SUBJECTIVE` |
| **questions[].content** | String | O | 질문 내용 | Not Blank, max 200 |
| **questions[].isRequired** | Boolean | O | 필수 응답 여부 | |
| **questions[].allowMultiple** | Boolean | X | 복수 선택 허용 (객관식만) | 기본 false |
| **questions[].options** | Array | △ | 보기 목록 | 객관식이면 최소 1개, 주관식이면 빈 배열 |
| **questions[].options[].order** | Integer | O | 보기 순서 | 1 이상 |
| **questions[].options[].content** | String | O | 보기 내용 | Not Blank, max 200 |

### 3. Response
#### 3-1. Success Response

**HTTP Status: `201 Created`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "설문이 성공적으로 등록되었습니다.",
  "result": {
    "surveyId": 101,
    "title": "대학생 공모전 참여 경험 조사",
    "questionCount": {
      "multipleChoice": 3,
      "subjective": 1,
      "total": 4
    },
    "tokenCost": 24,
    "tokenBalanceAfter": 58,
    "status": "IN_PROGRESS",
    "createdAt": "2026-01-01"
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.surveyId** | Long | 생성된 설문 ID |
| **result.title** | String | 설문 제목 |
| **result.questionCount.multipleChoice** | Integer | 객관식 문항 수 |
| **result.questionCount.subjective** | Integer | 주관식 문항 수 |
| **result.questionCount.total** | Integer | 전체 문항 수 |
| **result.tokenCost** | Integer | 차감된 토큰 (10 + 객관식×3 + 주관식×5) |
| **result.tokenBalanceAfter** | Integer | 차감 후 잔여 토큰 |
| **result.status** | String | 진행 상태 (`IN_PROGRESS`) |
| **result.createdAt** | String | 게시일 (yyyy-MM-dd) |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "TOKEN_001",
  "message": "토큰이 부족합니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 필수값 누락, 길이 위반, 카테고리 코드 오류 |
| **400** | `SURVEY_002` | 마감일은 시작일 이후여야 합니다. | endDate ≤ startDate |
| **400** | `SURVEY_003` | 문항은 최소 1개 이상이어야 합니다. | 빈 questions |
| **400** | `SURVEY_004` | 객관식 문항은 보기가 최소 1개 이상이어야 합니다. | 객관식인데 options 비어있음 |
| **400** | `TOKEN_001` | 토큰이 부족합니다. | 생성 비용 > 보유 토큰 |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## SURVEY-05 · 설문 삭제

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 본인이 등록한 설문을 삭제합니다. (마이페이지 휴지통) |
| **Note** | 소프트 삭제 처리되어 목록·검색에서 제외되며, 연결된 응답 데이터는 보존됩니다. 본인 소유 설문만 삭제할 수 있습니다. 진행중·종료 상태 모두 삭제 가능하지만, **공공 아카이브에 공유된 설문(`shared_to_archive=true`)은 삭제할 수 없습니다**(SURVEY_005). 삭제된 설문은 복구할 수 없습니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **surveyId** | `101` | O | 삭제할 설문 ID |

#### 2-3. Query String
없음

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "설문이 삭제되었습니다.",
  "result": {
    "surveyId": 101,
    "isDeleted": true
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.surveyId** | Long | 삭제된 설문 ID |
| **result.isDeleted** | Boolean | 삭제 처리 여부 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "COMMON_403",
  "message": "접근 권한이 없습니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **403** | `COMMON_403` | 접근 권한이 없습니다. | 본인 소유가 아닌 설문 삭제 시도 |
| **404** | `SURVEY_001` | 존재하지 않는 설문입니다. | 잘못된 surveyId 또는 이미 삭제됨 |
| **409** | `SURVEY_005` | 공유된 설문은 삭제할 수 없습니다. | 아카이브에 공유된 설문(`shared_to_archive=true`) |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## Survey 도메인 에러 코드 정리

| **Error Code** | **HTTP** | **Message** |
| --- | --- | --- |
| `SURVEY_001` | 404 | 존재하지 않는 설문입니다. |
| `SURVEY_002` | 400 | 마감일은 시작일 이후여야 합니다. |
| `SURVEY_003` | 400 | 문항은 최소 1개 이상이어야 합니다. |
| `SURVEY_004` | 400 | 객관식 문항은 보기가 최소 1개 이상이어야 합니다. |
| `SURVEY_005` | 409 | 공유된 설문은 삭제할 수 없습니다. |
| `TOKEN_001` | 400 | 토큰이 부족합니다. (Token 도메인 공용) |

**참고**
- `maxToken`(최대 획득 가능 토큰) = 객관식 문항 수 × 1 + 주관식 문항 수 × 2.
- `tokenCost`(생성 비용) = 10 + 객관식 문항 수 × 3 + 주관식 문항 수 × 5.
- 예시의 3객관식·1주관식 → 생성 비용 24, 보유 82 → 잔여 58 (기획 최종 확인 화면과 동일).

---

# 7. Response 도메인

## 도메인 개요

| **도메인** | **Page (사용 위치)** | **API 명** | **담당자** | **Method** | **Endpoint** |
| --- | --- | --- | --- | --- | --- |
| Response | 설문 진행 화면 · 제출 | 설문 응답 제출 |  | POST | `/surveys/{surveyId}/responses` |

---

## RESPONSE-01 · 설문 응답 제출

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 한 설문에 대한 응답 전체를 한 번에 제출합니다. 회원은 토큰이 적립되고, 게스트는 응답만 저장됩니다. |
| **Note** | 응답한 문항만 `answers`에 담아 전송하고, 건너뛴(선택) 문항은 생략합니다. **필수 문항은 모두 응답해야** 하며, 하나라도 누락 시 실패합니다. 회원은 한 설문에 **한 번만** 참여할 수 있고, 본인이 등록한 설문에는 참여할 수 없습니다. 이미 종료된 설문에는 참여할 수 없습니다. |
| **Note(토큰)** | 회원 적립 토큰 = 최대 획득 토큰 − (건너뛴 객관식 수 × 1 + 건너뛴 주관식 수 × 2). 즉 `답한 객관식 수 × 1 + 답한 주관식 수 × 2`. 제출 결과의 토큰 값은 **서버가 권위 있게 계산**해 반환합니다. 제출 전 "제출하시겠습니까?" 팝업의 미리보기 수치는 동일한 공식으로 클라이언트가 계산합니다. |
| **Note(보너스)** | 이 제출로 해당 설문의 응답자 수(게스트 포함)가 50명에 도달하면, 설문 작성자에게 +10 토큰이 자동 지급됩니다(설문당 1회). 본 응답자에게 반환되는 값에는 영향을 주지 않습니다. |
| **Note(게스트)** | 게스트 제출(Authorization 헤더 없음)은 `guestKey`를 필수로 보냅니다. 클라이언트가 게스트 세션 진입 시 UUID를 1회 생성해 브라우저 세션에 보관하고, 모든 응답에 동일 값을 사용합니다. 서버는 `survey_responses.guest_key`에 저장하고 `UNIQUE(survey_id, guest_key)`로 **같은 세션의 동일 설문 중복 응답을 차단**합니다(응답 수 부풀리기 방어). 회원 제출은 `guestKey`를 보내지 않으며 무시됩니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Content-Type** | `application/json` | O | 데이터 타입 |
| **Authorization** | `Bearer {accessToken}` | X | 포함 시 회원(토큰 적립), 미포함 시 게스트 |

#### 2-2. Path Variable
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **surveyId** | `101` | O | 응답할 설문 ID |

#### 2-3. Query String
없음

#### 2-4. Request Body
JSON 예시
```json
{
  "guestKey": "b3f1c2a4-7d8e-4f10-9abc-1234567890ab",
  "answers": [
    {
      "questionId": 5001,
      "selectedOptionIds": [9002]
    },
    {
      "questionId": 5003,
      "selectedOptionIds": [9010, 9011]
    },
    {
      "questionId": 5004,
      "answerText": "멘토링 프로그램과 정보 제공이 필요합니다."
    }
  ]
}
```
필드 설명
| **Field** | **Type** | **Required** | **Description** | **Constraints** |
| --- | --- | --- | --- | --- |
| **guestKey** | String | △ | 게스트 세션 키 (UUID) | **게스트 제출 시 필수**, 회원 제출 시 생략/무시. max 64 |
| **answers** | Array | O | 응답한 문항 목록 (건너뛴 문항은 제외) | 최소 1개 |
| **answers[].questionId** | Long | O | 응답 대상 문항 ID | 해당 설문의 문항이어야 함 |
| **answers[].selectedOptionIds** | Array(Long) | △ | 선택한 보기 ID 목록 (객관식) | 객관식 필수. 단일 선택은 1개, `allowMultiple=true`면 1개 이상 |
| **answers[].answerText** | String | △ | 주관식 답변 텍스트 | 주관식 필수, Not Blank |

> 각 문항은 유형에 따라 `selectedOptionIds`(객관식) 또는 `answerText`(주관식) 중 하나만 사용합니다.

### 3. Response
#### 3-1. Success Response

**HTTP Status: `201 Created`**

**(회원 제출 시)**
```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "설문 참여가 완료되었습니다.",
  "result": {
    "responseId": 3001,
    "surveyId": 101,
    "isGuest": false,
    "tokenReward": {
      "maxToken": 5,
      "skipped": {
        "multipleChoice": 0,
        "subjective": 1
      },
      "earnedToken": 3
    },
    "tokenBalanceAfter": 127,
    "submittedAt": "2026-01-05T10:00:00"
  }
}
```
**(게스트 제출 시)**
```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "설문 참여가 완료되었습니다.",
  "result": {
    "responseId": 3002,
    "surveyId": 101,
    "isGuest": true,
    "tokenReward": null,
    "tokenBalanceAfter": null,
    "submittedAt": "2026-01-05T10:00:00"
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.responseId** | Long | 생성된 응답 ID |
| **result.surveyId** | Long | 설문 ID |
| **result.isGuest** | Boolean | 게스트 응답 여부 |
| **result.tokenReward** | Object | 토큰 적립 내역 (게스트는 null) |
| **result.tokenReward.maxToken** | Integer | 최대 획득 가능 토큰 |
| **result.tokenReward.skipped.multipleChoice** | Integer | 건너뛴 객관식 문항 수 |
| **result.tokenReward.skipped.subjective** | Integer | 건너뛴 주관식 문항 수 |
| **result.tokenReward.earnedToken** | Integer | 실제 적립된 토큰 |
| **result.tokenBalanceAfter** | Integer | 적립 후 보유 토큰 (게스트는 null) |
| **result.submittedAt** | String | 제출 시각 (ISO 8601) |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "RESPONSE_001",
  "message": "필수 문항에 응답하지 않았습니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 존재하지 않는 questionId/optionId, 유형 불일치(객관식에 answerText 등), 게스트 제출인데 `guestKey` 누락 |
| **400** | `RESPONSE_001` | 필수 문항에 응답하지 않았습니다. | 필수 문항 누락 → 모든 필수 문항 응답 필요 |
| **403** | `RESPONSE_004` | 본인이 등록한 설문에는 참여할 수 없습니다. | 작성자가 자기 설문에 응답 시도 |
| **404** | `SURVEY_001` | 존재하지 않는 설문입니다. | 잘못된 surveyId 또는 삭제된 설문 |
| **409** | `RESPONSE_002` | 이미 참여한 설문입니다. | 회원 재참여, 또는 같은 게스트 세션(`guestKey`)의 동일 설문 재참여 |
| **409** | `RESPONSE_003` | 종료된 설문에는 참여할 수 없습니다. | 마감일이 지난 설문 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## Response 도메인 에러 코드 정리

| **Error Code** | **HTTP** | **Message** |
| --- | --- | --- |
| `RESPONSE_001` | 400 | 필수 문항에 응답하지 않았습니다. |
| `RESPONSE_002` | 409 | 이미 참여한 설문입니다. (회원 재참여 / 게스트 세션 중복) |
| `RESPONSE_003` | 409 | 종료된 설문에는 참여할 수 없습니다. |
| `RESPONSE_004` | 403 | 본인이 등록한 설문에는 참여할 수 없습니다. |

**참고**
- 게스트 중복은 `guestKey` 기준으로만 막히므로 다른 기기·시크릿창 등 세션이 바뀌면 재참여 가능합니다(의도된 한계). 회원 중복은 `respondent_id` 기준으로 확실히 차단됩니다.
- 응답 제출은 슬라이드 방식으로 진행되지만 **저장은 마지막 제출 1회**로 이루어집니다. 참여 중 뒤로가기("설문을 나가시겠어요?")로 이탈하면 작성 중 내용은 저장되지 않습니다(임시 저장 없음). 이는 별도 API가 아닌 클라이언트 처리입니다.
- 건너뛸 수 있는 문항은 필수(`isRequired=false`)뿐입니다. 필수 문항 미응답은 제출 자체가 차단됩니다(RESPONSE_001).
- 예시 기준: 설문이 객관식 3·주관식 1이면 `maxToken=5`. 주관식 1개를 건너뛰면 `earnedToken = 5 − (0×1 + 1×2) = 3`.

---

# 8. Archive 도메인

## 도메인 개요

| **도메인** | **Page (사용 위치)** | **API 명** | **담당자** | **Method** | **Endpoint** |
| --- | --- | --- | --- | --- | --- |
| Archive | 공공 아카이브 / 더보기 | 아카이브 설문 목록 조회 |  | GET | `/archive/surveys` |
| Archive | 열람하기 화면 | 아카이브 설문 열람 정보 조회 |  | GET | `/archive/surveys/{surveyId}` |
| Archive | 열람하기 · 토큰 소모 팝업 | 설문 결과 열람 구매 |  | POST | `/archive/surveys/{surveyId}/views` |
| Archive | 설문 세부 사항(결과) 화면 | 설문 세부 결과 조회 (필터 지원) |  | GET | `/archive/surveys/{surveyId}/results` |
| Archive | 공유 가능한 내 설문 화면 | 공유 가능한 내 설문 목록 조회 |  | GET | `/archive/my-surveys` |
| Archive | 공유하고 토큰 받기 | 설문 공유 |  | POST | `/archive/shares` |

---

## ARCHIVE-01 · 아카이브 설문 목록 조회

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 공유되어 열람 가능한 설문을 미리보기 형태로 최신순 조회합니다. 카테고리 필터·제목 검색을 지원합니다. |
| **Note** | 종료되고 아카이브에 공유된(`shared_to_archive=true`) 설문만 반환합니다. 공공 아카이브 화면과 더보기 화면이 동일 API를 사용하며, 노출 개수는 `size`로 조절합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **category** | `IT_AI` | X | 카테고리 코드로 필터 (미지정 시 전체) |
| **keyword** | `대학생` | X | 설문 제목 검색어 |
| **cursor** | `eyJpZCI6MTIzfQ==` | X | 다음 페이지 커서. 첫 페이지는 생략 |
| **size** | `20` | X | 페이지 크기 (기본 20) |

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "items": [
      {
        "surveyId": 77,
        "title": "대학생 AI 활용 실태 조사",
        "category": "IT_AI",
        "target": "대학생",
        "respondentCount": 52,
        "createdAt": "2026-01-01"
      }
    ],
    "nextCursor": "eyJpZCI6Nzd9",
    "hasNext": true
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.items[].surveyId** | Long | 설문 ID |
| **result.items[].title** | String | 설문 제목 |
| **result.items[].category** | String | 카테고리 코드 |
| **result.items[].target** | String | 희망 설문 대상 |
| **result.items[].respondentCount** | Integer | 응답자 수 (게스트 포함) |
| **result.items[].createdAt** | String | 게시일 (yyyy-MM-dd) |
| **result.nextCursor** | String | 다음 페이지 커서 (없으면 null) |
| **result.hasNext** | Boolean | 다음 페이지 존재 여부 |

#### 3-2. Error Response
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | 커서 형식 오류, category 코드 오류 |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## ARCHIVE-02 · 아카이브 설문 열람 정보 조회

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 결과를 열람하기 전, 설문 요약 정보와 열람 비용·구매 여부를 조회합니다. (열람하기 화면) |
| **Note** | `alreadyViewed`가 true면 이미 열람권을 보유한 것으로, 추가 토큰 소모 없이 결과 조회로 바로 이동할 수 있습니다. 본인이 등록한 설문도 `alreadyViewed=true`(무료)로 반환됩니다. `viewCost`는 15로 고정입니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **surveyId** | `77` | O | 조회할 설문 ID |

#### 2-3. Query String
없음

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "surveyId": 77,
    "title": "대학생 공모전 참여 경험 조사",
    "category": "CAREER",
    "target": "대학생",
    "description": "대학생들의 공모전 참여 경험과 참여를 방해하는 요인을 조사하기 위한 설문입니다.",
    "startDate": "2026-01-01",
    "endDate": "2026-01-15",
    "respondentCount": 52,
    "questionCount": {
      "multipleChoice": 3,
      "subjective": 1,
      "total": 4
    },
    "createdAt": "2026-01-01",
    "viewCost": 15,
    "alreadyViewed": false,
    "tokenBalance": 124
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.surveyId** | Long | 설문 ID |
| **result.title** | String | 설문 제목 |
| **result.category** | String | 카테고리 코드 |
| **result.target** | String | 희망 설문 대상 |
| **result.description** | String | 설문 소개 |
| **result.startDate** | String | 설문 시작일 |
| **result.endDate** | String | 마감일 |
| **result.respondentCount** | Integer | 응답자 수 (게스트 포함) |
| **result.questionCount.\*** | Integer | 객관식/주관식/전체 문항 수 |
| **result.createdAt** | String | 게시일 |
| **result.viewCost** | Integer | 열람 비용 (15 고정) |
| **result.alreadyViewed** | Boolean | 이미 열람권 보유 여부 (본인 설문 포함 시 true) |
| **result.tokenBalance** | Integer | 현재 보유 토큰 (팝업 표기용) |

#### 3-2. Error Response
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **404** | `SURVEY_001` | 존재하지 않는 설문입니다. | 잘못된 surveyId 또는 미공유 설문 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## ARCHIVE-03 · 설문 결과 열람 구매

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 15토큰을 소모하여 해당 설문의 결과 열람권을 획득합니다. |
| **Note** | 보유 토큰이 15 미만이면 실패합니다. 한 번 구매하면 재열람은 무료이므로, 이미 열람권이 있거나 본인 설문인 경우 토큰을 소모하지 않고 `tokenSpent=0`으로 성공 반환합니다. 구매 성공 후 [ARCHIVE-04]로 결과를 조회합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **surveyId** | `77` | O | 열람할 설문 ID |

#### 2-3. Query String
없음

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `201 Created`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "열람권을 획득했습니다.",
  "result": {
    "viewId": 4501,
    "surveyId": 77,
    "tokenSpent": 15,
    "tokenBalanceBefore": 124,
    "tokenBalanceAfter": 109,
    "viewedAt": "2026-01-05T09:30:00"
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.viewId** | Long | 열람 기록 ID |
| **result.surveyId** | Long | 설문 ID |
| **result.tokenSpent** | Integer | 소모 토큰 (15, 재열람/본인 설문은 0) |
| **result.tokenBalanceBefore** | Integer | 소모 전 보유 토큰 |
| **result.tokenBalanceAfter** | Integer | 소모 후 보유 토큰 |
| **result.viewedAt** | String | 열람권 획득 시각 (ISO 8601) |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "TOKEN_001",
  "message": "토큰이 부족합니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `TOKEN_001` | 토큰이 부족합니다. | 보유 토큰 < 15 |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **404** | `SURVEY_001` | 존재하지 않는 설문입니다. | 잘못된 surveyId 또는 미공유 설문 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## ARCHIVE-04 · 설문 세부 결과 조회 (필터 지원)

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 설문 정보와 문항별 응답 집계 결과를 조회합니다. 특정 보기를 선택한 응답자만으로 재집계하는 필터(크로스탭)를 지원합니다. |
| **Note** | 열람권이 있거나(구매 완료) 본인이 등록한 설문일 때만 조회할 수 있습니다. 객관식은 보기별 인원 수·비율을, 주관식은 응답 텍스트 목록을 반환합니다. `filters`에 객관식 보기 ID를 지정하면, 해당 보기들을 **모두** 선택한 응답자 집합으로 좁혀 전 문항을 재집계합니다(비율·인원은 좁혀진 응답자 기준). 주관식 보기는 필터 조건이 될 수 없습니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **surveyId** | `77` | O | 조회할 설문 ID |

#### 2-3. Query String
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **filters** | `9002,9010` | X | 필터 조건 보기 ID 목록(콤마 구분). 지정된 보기를 모두 선택한 응답자로 한정 |

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "surveyInfo": {
      "surveyId": 77,
      "title": "대학생 공모전 참여 경험 조사",
      "category": "CAREER",
      "target": "대학생",
      "description": "대학생들의 공모전 참여 경험과 참여를 방해하는 요인을 조사하기 위한 설문입니다.",
      "startDate": "2026-01-01",
      "endDate": "2026-01-15",
      "respondentCount": 52,
      "questionCount": { "multipleChoice": 3, "subjective": 1, "total": 4 },
      "createdAt": "2026-01-01"
    },
    "appliedFilters": [],
    "filteredRespondentCount": 52,
    "results": [
      {
        "questionId": 5001,
        "order": 1,
        "type": "MULTIPLE_CHOICE",
        "content": "현재 학년은 어떻게 되시나요?",
        "options": [
          { "optionId": 9001, "content": "1학년", "count": 7, "percentage": 12.5 },
          { "optionId": 9002, "content": "2학년", "count": 45, "percentage": 87.5 },
          { "optionId": 9003, "content": "3학년", "count": 0, "percentage": 0.0 },
          { "optionId": 9004, "content": "4학년", "count": 0, "percentage": 0.0 }
        ]
      },
      {
        "questionId": 5004,
        "order": 4,
        "type": "SUBJECTIVE",
        "content": "학교에서 공모전 참여를 위해 가장 필요하다고 생각하는 지원은 무엇인가요?",
        "answers": [
          "멘토링 프로그램이 필요합니다.",
          "공모전 정보를 모아주는 채널이 있으면 좋겠습니다."
        ]
      }
    ]
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.surveyInfo.\*** | Object | 설문 정보 탭 데이터 (제목·카테고리·기간·대상·응답자 수·문항 수 등) |
| **result.appliedFilters** | Array(Long) | 실제 적용된 필터 보기 ID (없으면 빈 배열) |
| **result.filteredRespondentCount** | Integer | 필터 적용 후 응답자 수 (필터 없으면 전체와 동일) |
| **result.results[].questionId** | Long | 문항 ID |
| **result.results[].order** | Integer | 문항 순서 |
| **result.results[].type** | String | 문항 유형 |
| **result.results[].content** | String | 질문 내용 |
| **result.results[].options[].optionId** | Long | 보기 ID (객관식) |
| **result.results[].options[].content** | String | 보기 내용 |
| **result.results[].options[].count** | Integer | 해당 보기 응답 인원 수 |
| **result.results[].options[].percentage** | Number | 해당 보기 비율(%) — 필터 적용 시 좁혀진 응답자 기준 |
| **result.results[].answers** | Array(String) | 주관식 응답 텍스트 목록 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "ARCHIVE_001",
  "message": "열람 권한이 없습니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | filters에 주관식 보기/존재하지 않는 optionId 지정 |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **403** | `ARCHIVE_001` | 열람 권한이 없습니다. | 열람권 미보유(구매 필요) 상태에서 결과 접근 |
| **404** | `SURVEY_001` | 존재하지 않는 설문입니다. | 잘못된 surveyId 또는 미공유 설문 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## ARCHIVE-05 · 공유 가능한 내 설문 목록 조회

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 내가 등록한 설문 중 종료된 설문을 공유 대상으로 조회합니다. (공유 가능한 내 설문 화면) |
| **Note** | 종료(`CLOSED`)된 본인 설문만 대상입니다. 이미 공유한 설문은 `sharedToArchive=true`로 표기되어 중복 공유를 방지합니다. 커서 기반 페이지네이션을 사용합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **cursor** | `eyJpZCI6MTIzfQ==` | X | 다음 페이지 커서. 첫 페이지는 생략 |
| **size** | `20` | X | 페이지 크기 (기본 20) |

#### 2-4. Request Body
없음

### 3. Response
#### 3-1. Success Response

**HTTP Status: `200 OK`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "result": {
    "items": [
      {
        "surveyId": 98,
        "title": "대학생 공모전 참여 경험 조사",
        "category": "CAREER",
        "target": "대학생",
        "respondentCount": 52,
        "status": "CLOSED",
        "sharedToArchive": false,
        "createdAt": "2026-01-01"
      }
    ],
    "nextCursor": null,
    "hasNext": false
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.items[].surveyId** | Long | 설문 ID |
| **result.items[].title** | String | 설문 제목 |
| **result.items[].category** | String | 카테고리 코드 |
| **result.items[].target** | String | 희망 설문 대상 |
| **result.items[].respondentCount** | Integer | 응답자 수 (게스트 포함) |
| **result.items[].status** | String | 진행 상태 (`CLOSED` 고정) |
| **result.items[].sharedToArchive** | Boolean | 이미 공유되었는지 여부 |
| **result.items[].createdAt** | String | 게시일 |

#### 3-2. Error Response
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## ARCHIVE-06 · 설문 공유

### 1. API 개요
| **분류** | **내용** |
| --- | --- |
| **Description** | 선택한 종료 설문들을 공공 아카이브에 공유하고, 공유당 토큰을 지급받습니다. |
| **Note** | 종료된 본인 설문만 공유할 수 있습니다. 새로 공유되는 설문 **1건당 +10 토큰**이 지급됩니다. 이미 공유된 설문·진행 중 설문·타인 설문이 포함되면 실패합니다. 공유 후 "설문을 공유하여 토큰을 받았습니다" 팝업에 보유 토큰 변화를 표기합니다. |

### 2. Request
#### 2-1. Header
| **Key** | **Value (Example)** | **Required** | **Description** |
| --- | --- | --- | --- |
| **Content-Type** | `application/json` | O | 데이터 타입 |
| **Authorization** | `Bearer {accessToken}` | O | 로그인 사용자 인증 |

#### 2-2. Path Variable
없음

#### 2-3. Query String
없음

#### 2-4. Request Body
JSON 예시
```json
{
  "surveyIds": [98, 99]
}
```
필드 설명
| **Field** | **Type** | **Required** | **Description** | **Constraints** |
| --- | --- | --- | --- | --- |
| **surveyIds** | Array(Long) | O | 공유할 설문 ID 목록 | 최소 1개, 본인 소유·종료·미공유 |

### 3. Response
#### 3-1. Success Response

**HTTP Status: `201 Created`**

```json
{
  "isSuccess": true,
  "code": "COMMON_200",
  "message": "설문을 공유하여 토큰을 받았습니다.",
  "result": {
    "sharedCount": 2,
    "rewardTokenPerSurvey": 10,
    "totalRewardToken": 20,
    "tokenBalanceBefore": 109,
    "tokenBalanceAfter": 129
  }
}
```
응답 필드 설명
| **Field** | **Type** | **Description** |
| --- | --- | --- |
| **result.sharedCount** | Integer | 실제 공유된 설문 수 |
| **result.rewardTokenPerSurvey** | Integer | 공유 1건당 지급 토큰 (10) |
| **result.totalRewardToken** | Integer | 총 지급 토큰 |
| **result.tokenBalanceBefore** | Integer | 지급 전 보유 토큰 |
| **result.tokenBalanceAfter** | Integer | 지급 후 보유 토큰 |

#### 3-2. Error Response
JSON 예시
```json
{
  "isSuccess": false,
  "code": "ARCHIVE_002",
  "message": "이미 공유된 설문입니다.",
  "result": null
}
```
Error 시나리오
| **HTTP Status** | **Error Code** | **Message** | **Cause & Solution** |
| --- | --- | --- | --- |
| **400** | `COMMON_400` | 잘못된 요청입니다. | surveyIds 누락/빈 배열 |
| **400** | `ARCHIVE_003` | 종료된 설문만 공유할 수 있습니다. | 진행 중 설문 포함 |
| **401** | `COMMON_401` | 인증이 필요합니다. | accessToken 누락/만료 |
| **403** | `COMMON_403` | 접근 권한이 없습니다. | 타인 설문 공유 시도 |
| **404** | `SURVEY_001` | 존재하지 않는 설문입니다. | 잘못된 surveyId 포함 |
| **409** | `ARCHIVE_002` | 이미 공유된 설문입니다. | 이미 공유된 설문 포함 |
| **500** | `COMMON_500` | 서버 에러가 발생했습니다. | 서버 내부 로직 오류 |

---

## Archive 도메인 에러 코드 정리

| **Error Code** | **HTTP** | **Message** |
| --- | --- | --- |
| `ARCHIVE_001` | 403 | 열람 권한이 없습니다. |
| `ARCHIVE_002` | 409 | 이미 공유된 설문입니다. |
| `ARCHIVE_003` | 400 | 종료된 설문만 공유할 수 있습니다. |
| `TOKEN_001` | 400 | 토큰이 부족합니다. (Token 도메인 공용) |

**참고**
- **열람 흐름:** [ARCHIVE-02]로 요약·구매여부 확인 → 미보유 시 [ARCHIVE-03]로 15토큰 소모 구매 → [ARCHIVE-04]로 결과 조회. 이미 보유(또는 본인 설문)면 구매 없이 [ARCHIVE-04]로 직행.
- **마이페이지 연동:** "내가 등록한 설문 / 열람한 설문"의 자세히 보기도 [ARCHIVE-04]를 사용합니다(본인 설문·기구매 설문은 무료 접근).
- **크로스탭 필터:** `filters`는 객관식 보기 ID만 허용하며, 지정된 보기를 모두 선택한 응답자로 좁혀 전 문항을 재집계합니다. 같은 문항 내 여러 보기를 지정하는 경우의 처리(OR 여부)는 구현 시 팀 합의로 확정하세요(기본은 지정 보기 전부 충족 = AND).
- **주관식 개인정보 AI 검사:** 공유 시점 주관식 스크리닝은 현재 범위에서 제외(향후 확장). 현재는 원문 그대로 결과에 노출됩니다.

---

# 9. 전체 에러 코드 마스터

## 공통 (COMMON)
| Code | HTTP | Message |
| --- | --- | --- |
| `COMMON_400` | 400 | 잘못된 요청입니다. |
| `COMMON_401` | 401 | 인증이 필요합니다. |
| `COMMON_403` | 403 | 접근 권한이 없습니다. |
| `COMMON_404` | 404 | 대상을 찾을 수 없습니다. |
| `COMMON_500` | 500 | 서버 에러가 발생했습니다. |

## AUTH
| Code | HTTP | Message |
| --- | --- | --- |
| `AUTH_001` | 409 | 이미 사용 중인 이메일입니다. |
| `AUTH_002` | 400 | 인증번호가 일치하지 않습니다. |
| `AUTH_003` | 400 | 인증번호가 만료되었습니다. |
| `AUTH_004` | 400 | 이메일 인증이 완료되지 않았습니다. |
| `AUTH_005` | 401 | 이메일 또는 비밀번호가 일치하지 않습니다. |
| `AUTH_006` | 401 | 유효하지 않은 리프레시 토큰입니다. |
| `AUTH_007` | 404 | 등록되지 않은 이메일입니다. |
| `AUTH_008` | 401 | 만료된 리프레시 토큰입니다. |
| `AUTH_009` | 429 | 인증 시도 횟수를 초과했습니다. 인증번호를 재발송해주세요. |
| `AUTH_010` | 429 | 잠시 후 다시 시도해주세요. |

## Survey
| Code | HTTP | Message |
| --- | --- | --- |
| `SURVEY_001` | 404 | 존재하지 않는 설문입니다. |
| `SURVEY_002` | 400 | 마감일은 시작일 이후여야 합니다. |
| `SURVEY_003` | 400 | 문항은 최소 1개 이상이어야 합니다. |
| `SURVEY_004` | 400 | 객관식 문항은 보기가 최소 1개 이상이어야 합니다. |
| `SURVEY_005` | 409 | 공유된 설문은 삭제할 수 없습니다. |

## Response
| Code | HTTP | Message |
| --- | --- | --- |
| `RESPONSE_001` | 400 | 필수 문항에 응답하지 않았습니다. |
| `RESPONSE_002` | 409 | 이미 참여한 설문입니다. |
| `RESPONSE_003` | 409 | 종료된 설문에는 참여할 수 없습니다. |
| `RESPONSE_004` | 403 | 본인이 등록한 설문에는 참여할 수 없습니다. |

## Archive
| Code | HTTP | Message |
| --- | --- | --- |
| `ARCHIVE_001` | 403 | 열람 권한이 없습니다. |
| `ARCHIVE_002` | 409 | 이미 공유된 설문입니다. |
| `ARCHIVE_003` | 400 | 종료된 설문만 공유할 수 있습니다. |

## Token
| Code | HTTP | Message |
| --- | --- | --- |
| `TOKEN_001` | 400 | 토큰이 부족합니다. (설문 생성·결과 열람 공용) |

> User 도메인은 전용 에러 코드 없이 `COMMON_*`만 사용합니다.

---

# 10. 엔드포인트 인덱스

인증 표기: **공개**(토큰 불필요) / **회원**(로그인 필수) / **선택**(게스트 허용, 헤더 유무로 분기)

| # | 도메인 | Method | Endpoint | 인증 | 설명 |
| --- | --- | --- | --- | --- | --- |
| 1 | Auth | POST | `/auth/email-verifications` | 공개 | 이메일 인증번호 발송 |
| 2 | Auth | POST | `/auth/email-verifications/verify` | 공개 | 이메일 인증번호 검증 |
| 3 | Auth | POST | `/auth/signup` | 공개 | 회원가입 |
| 4 | Auth | POST | `/auth/login` | 공개 | 로그인 |
| 5 | Auth | POST | `/auth/token/refresh` | 공개 | 액세스 토큰 재발급 |
| 6 | Auth | POST | `/auth/logout` | 회원 | 로그아웃 |
| 7 | Auth | POST | `/auth/password/reset` | 공개 | 비밀번호 재설정 |
| 8 | User | GET | `/users/me` | 회원 | 내 정보 조회 |
| 9 | User | GET | `/users/me/surveys` | 회원 | 내가 등록한 설문 목록 |
| 10 | User | GET | `/users/me/viewed-surveys` | 회원 | 내가 열람한 설문 목록 |
| 11 | Survey | GET | `/surveys` | 선택 | 참여 가능한 설문 목록 (category/keyword/cursor) |
| 12 | Survey | GET | `/surveys/{surveyId}` | 선택 | 설문 상세 조회 |
| 13 | Survey | GET | `/surveys/{surveyId}/questions` | 선택 | 설문 문항 조회 |
| 14 | Survey | POST | `/surveys` | 회원 | 설문 생성 (토큰 차감) |
| 15 | Survey | DELETE | `/surveys/{surveyId}` | 회원 | 설문 삭제 (소프트) |
| 16 | Response | POST | `/surveys/{surveyId}/responses` | 선택 | 설문 응답 제출 (회원 토큰 적립) |
| 17 | Archive | GET | `/archive/surveys` | 회원 | 아카이브 설문 목록 |
| 18 | Archive | GET | `/archive/surveys/{surveyId}` | 회원 | 아카이브 열람 정보(구매 전) |
| 19 | Archive | POST | `/archive/surveys/{surveyId}/views` | 회원 | 결과 열람 구매 (−15) |
| 20 | Archive | GET | `/archive/surveys/{surveyId}/results` | 회원 | 세부 결과 조회 (filters 크로스탭) |
| 21 | Archive | GET | `/archive/my-surveys` | 회원 | 공유 가능한 내 설문 목록 |
| 22 | Archive | POST | `/archive/shares` | 회원 | 설문 공유 (+10/건) |

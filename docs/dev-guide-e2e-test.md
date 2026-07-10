# E2E API 테스트 기록

---

## 1. 테스트 환경

```
서버: Spring Boot 4.1.0 (local 프로파일)
DB: MySQL 8.0 (Docker, 포트 3307→3306 매핑)
Java: 17
테스트 방법: curl 직접 호출
테스트 날짜: 2026-07-11
```

테스트 전에 모든 테이블을 `TRUNCATE`하고 빈 상태에서 시작했습니다.

---

## 2. 테스트 흐름

전체 API를 실제 비즈니스 시나리오 순서대로 호출했습니다:

```
1. 인증번호 발송 → 인증 → 회원가입 → 로그인                    (AUTH)
2. 설문 생성 → 목록/상세/문항 조회                              (SURVEY)
3. 다른 유저 가입 → 해당 유저가 설문에 응답 + 게스트 응답         (RESPONSE)
4. 설문 종료 → 아카이브 공유 → 열람 구매 → 결과 조회             (ARCHIVE)
5. 내 설문 목록 / 열람 설문 목록 조회                            (USER)
6. 설문 삭제 + 엣지 케이스                                      (EDGE)
```

---

## 3. AUTH 도메인 테스트 결과

### AUTH-01: 인증번호 발송 `POST /auth/email-verifications`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 발송 (purpose: SIGNUP) | 200, 인증번호 발송 메시지 | ✅ COMMON_200 |
| 1분 이내 재발송 | 429, 레이트리밋 | ✅ AUTH_010 |
| 빈 body | 400, 검증 에러 | ✅ COMMON_400 |
| 잘못된 이메일 형식 | 400, 검증 에러 | ✅ COMMON_400 |

### AUTH-02: 인증번호 확인 `POST /auth/email-verifications/verify`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 인증 (올바른 코드) | 200, 인증 완료 | ✅ COMMON_200 |
| 틀린 코드 1~5회 | 400, 코드 불일치 + DB attemptCount 증가 | ✅ AUTH_002 (C1 fix 확인) |
| 6번째 시도 (5회 초과) | 429, 시도 횟수 초과 | ✅ AUTH_009 (체크 순서 수정 후 정상) |

**C1 수정 검증:** 틀린 코드를 보낼 때마다 DB의 `attempt_count`가 1씩 증가하는 것을 직접 확인했습니다. `REQUIRES_NEW` 트랜잭션으로 분리한 덕분에 `GeneralException`으로 본 트랜잭션이 롤백되어도 카운트는 DB에 남습니다.

**체크 순서 검증:** `isMaxAttemptExceeded()`가 `isExpiredOrInvalidated()`보다 먼저 실행되므로, 6번째 시도에서 정확하게 AUTH_009가 반환됩니다.

### AUTH-03: 회원가입 `POST /auth/signup`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 가입 | 201, 가입 완료 + 가입 보너스 100토큰 | ✅ COMMON_200 (토큰 100 확인) |
| 중복 이메일 | 409, 이미 사용 중 | ✅ AUTH_001 |

### AUTH-04: 로그인 `POST /auth/login`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 로그인 | 200, accessToken + refreshToken | ✅ JWT 발급 확인 |
| 틀린 비밀번호 | 401, 이메일/비밀번호 불일치 | ✅ AUTH_005 |

### AUTH-05: 토큰 갱신 `POST /auth/token/refresh`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 갱신 | 200, 새 accessToken | ✅ 새 토큰 발급 |

### AUTH-06: 로그아웃 `POST /auth/logout`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 로그아웃 (Bearer 토큰 필요) | 200, 로그아웃 완료 | ✅ |
| 로그아웃 후 같은 refreshToken으로 갱신 | 401, 유효하지 않은 토큰 | ✅ AUTH_006 |

**참고:** 로그아웃 API는 SecurityConfig에서 `authenticated`로 설정되어 있어 Authorization 헤더가 필요합니다.

### AUTH-07: 비밀번호 재설정 `POST /auth/password/reset`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| PASSWORD_RESET 인증번호 발송 | 200 | ✅ |
| 인증번호 확인 | 200 | ✅ |
| 비밀번호 변경 | 200, 비밀번호 변경 완료 | ✅ |
| 새 비밀번호로 로그인 | 200, 로그인 성공 | ✅ |
| 이전 비밀번호로 로그인 | 401, 실패 | ✅ AUTH_005 |

---

## 4. SURVEY 도메인 테스트 결과

### SURVEY-01: 목록 조회 `GET /surveys`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 게스트 조회 | 200, 전체 진행중 설문 목록 | ✅ |
| 로그인 유저 조회 | 200, 자기 설문 제외 | ✅ (items: 0개 — 자기 설문만 있어서) |
| SQL에 LIMIT 포함 | 서버 로그에 LIMIT 쿼리 | ✅ (C3 fix 확인) |

### SURVEY-02: 상세 조회 `GET /surveys/{surveyId}`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 조회 | 200, 계산 필드(status, maxToken, respondentCount) 포함 | ✅ |
| 존재하지 않는 설문 | 404, SURVEY_001 | ✅ |

**계산 필드 검증:** 객관식 1 + 주관식 1 → maxToken = 1×1 + 1×2 = 3, status = IN_PROGRESS (endDate가 미래). 모두 정확.

### SURVEY-03: 문항 조회 `GET /surveys/{surveyId}/questions`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 조회 | 200, 문항+보기 중첩 구조 | ✅ |
| 객관식 문항에 옵션 3개 | options 배열 3개 | ✅ |
| 주관식 문항 | options 빈 배열 | ✅ |

### SURVEY-04: 설문 생성 `POST /surveys`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 생성 (객관식 1 + 주관식 1) | 200, tokenCost=18, balance=82 | ✅ |
| 잘못된 category (LIFESTYLE) | 400 | ✅ COMMON_400 |
| 잘못된 QuestionType (SHORT_ANSWER) | 400 | ✅ COMMON_400 |

**토큰 비용 계산:** 기본 10 + 객관식 1×3 + 주관식 1×5 = 18. 100 - 18 = 82. 정확.

**참고:** QuestionType은 `MULTIPLE_CHOICE` / `SUBJECTIVE`이고, Category는 `CAREER` / `IT_AI` / `SERVICE_APP` / `CONSUMER_MARKETING` / `GAME` / `SCHOOL_LIFE` / `DAILY` / `PSYCHOLOGY` / `ETC` 입니다.

### SURVEY-05: 설문 삭제 `DELETE /surveys/{surveyId}`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 공유된 설문 삭제 시도 | 400, SURVEY_005 | ✅ |
| 공유 안 된 설문 정상 삭제 | 200, isDeleted=true | ✅ |
| 삭제 후 조회 | 404, SURVEY_001 | ✅ |

---

## 5. RESPONSE 도메인 테스트 결과

### 응답 제출 `POST /surveys/{surveyId}/responses`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 회원 응답 (다른 유저) | 200, 토큰 보상 +3 (maxToken 전액) | ✅ tokenBalanceAfter=103 |
| 게스트 응답 (guestKey 포함) | 200, tokenReward=null | ✅ |
| 종료된 설문 응답 시도 | 400, RESPONSE_003 | ✅ |
| 주관식에 빈 문자열 | 400, COMMON_400 | ✅ (answerText.isBlank() 검증) |

**토큰 보상 검증:** 객관식 1개(1) + 주관식 1개(2) = 3토큰. skipped 없으므로 earnedToken = maxToken = 3. user2의 잔액 100 + 3 = 103.

**주관식 빈 문자열 주의:** 주관식 문항에 답변을 보내는 경우, `answerText`가 빈 문자열이면 COMMON_400 에러가 발생합니다. 답변을 건너뛰려면 해당 문항의 답변을 아예 보내지 말아야 합니다 (단, isRequired=true면 누락 불가).

---

## 6. ARCHIVE 도메인 테스트 결과

### ARCHIVE-01: 아카이브 목록 `GET /archive/surveys`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 공유된 설문 목록 | 200, 응답자 수 포함 | ✅ respondentCount=2 |

### ARCHIVE-02: 아카이브 상세 `GET /archive/surveys/{surveyId}`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 열람 전 상세 | 200, alreadyViewed=false, viewCost=15 | ✅ |

### ARCHIVE-03: 열람 구매 `POST /archive/surveys/{surveyId}/views`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 구매 | 201, 토큰 -15 | ✅ tokenBalanceAfter=88 |

### ARCHIVE-04: 결과 조회 `GET /archive/surveys/{surveyId}/results`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 조회 | 200, 객관식 통계 + 주관식 답변 | ✅ |

**결과 검증:**
- 객관식: 동아리 1표(50%), 학업 1표(50%), 아르바이트 0표(0%) — 정확
- 주관식: "좋아요", "교내 편의시설 개선" — 정확

### ARCHIVE-05: 공유 가능 설문 `GET /archive/my-surveys`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 종료되지 않은 설문 | 빈 목록 | ✅ items: [] |
| 종료된 설문 | sharedToArchive=false, status=CLOSED | ✅ |

**참고:** `findShareableSurveys`는 `end_date < today` 조건이 있어서 종료된 설문만 나옵니다.

### ARCHIVE-06: 설문 공유 `POST /archive/shares`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 공유 | 201, 토큰 +10 | ✅ tokenBalanceAfter=92 |

**요청 형식 주의:** `{"surveyIds": [1]}` — 배열 형태입니다 (복수 설문 동시 공유 가능).

---

## 7. USER 도메인 테스트 결과

### USER-01: 내 정보 `GET /users/me`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 정상 조회 | 200, 프로필 + tokenBalance | ✅ |

### USER-02: 내 설문 목록 `GET /users/me/surveys`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 커서 페이지네이션 | CursorPageResponse(items, nextCursor, hasNext) | ✅ |
| 설문 상태/삭제 가능 여부 | status=CLOSED, canDelete=false (공유됨) | ✅ |

### USER-03: 열람 설문 목록 `GET /users/me/viewed-surveys`

| 시나리오 | 기대 | 결과 |
|----------|------|------|
| 커서 페이지네이션 | CursorPageResponse | ✅ |
| 열람한 설문 표시 | respondentCount 포함 | ✅ |

---

## 8. 토큰 흐름 추적

테스트 중 user1(test@example.com)의 토큰 잔액 변화:

```
가입 보너스:          +100 → 100
설문 1 생성 (비용 18): -18 → 82
설문 아카이브 공유:    +10 → 92
설문 2 생성 (비용 13): -13 → 79
```

user2(user2@example.com)의 토큰 잔액 변화:

```
가입 보너스:          +100 → 100
설문 1 응답 (보상 3):  +3  → 103
아카이브 열람 구매:    -15 → 88
```

**모든 토큰 거래가 정확하게 계산되고 반영됨을 확인했습니다.**

---

## 9. 발견 및 수정한 이슈

### 체크 순서 버그 (E2E 중 발견, 즉시 수정)

**파일:** `AuthService.java` — `verifyEmail()` 메서드

인증코드 5회 실패 후 6번째 시도 시, `isExpiredOrInvalidated()`가 `isMaxAttemptExceeded()`보다 먼저 체크되어 AUTH_003(만료)이 AUTH_009(시도 초과) 대신 반환되는 문제. 체크 순서를 바꿔서 수정 완료.

자세한 내용은 `dev-guide-auth-review.md` 7절 → 5단계 참조.

---

## 10. 전체 엔드포인트 체크리스트

| # | 메서드 | 엔드포인트 | 인증 | 결과 |
|---|--------|-----------|------|------|
| 1 | POST | `/auth/email-verifications` | X | ✅ |
| 2 | POST | `/auth/email-verifications/verify` | X | ✅ |
| 3 | POST | `/auth/signup` | X | ✅ |
| 4 | POST | `/auth/login` | X | ✅ |
| 5 | POST | `/auth/token/refresh` | X | ✅ |
| 6 | POST | `/auth/logout` | O | ✅ |
| 7 | POST | `/auth/password/reset` | X | ✅ |
| 8 | GET | `/surveys` | △ (게스트 허용) | ✅ |
| 9 | GET | `/surveys/{id}` | △ | ✅ |
| 10 | GET | `/surveys/{id}/questions` | △ | ✅ |
| 11 | POST | `/surveys` | O | ✅ |
| 12 | DELETE | `/surveys/{id}` | O | ✅ |
| 13 | POST | `/surveys/{id}/responses` | △ | ✅ |
| 14 | GET | `/archive/surveys` | O | ✅ |
| 15 | GET | `/archive/surveys/{id}` | O | ✅ |
| 16 | POST | `/archive/surveys/{id}/views` | O | ✅ |
| 17 | GET | `/archive/surveys/{id}/results` | O | ✅ |
| 18 | GET | `/archive/my-surveys` | O | ✅ |
| 19 | POST | `/archive/shares` | O | ✅ |
| 20 | GET | `/users/me` | O | ✅ |
| 21 | GET | `/users/me/surveys` | O | ✅ |
| 22 | GET | `/users/me/viewed-surveys` | O | ✅ |

**인증 범례:** X = 불필요, O = 필수, △ = 선택 (게스트 허용, 로그인 시 추가 기능)

**22개 엔드포인트 전체 정상 동작 확인.**

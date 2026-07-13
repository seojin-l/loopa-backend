# 배포 서버 더미 데이터 가이드

> 서버: `http://13.209.139.144`

---

## 테스트 계정

| 이메일 | 비밀번호 | userId | 역할 |
|--------|----------|--------|------|
| seungheelee1122@gmail.com | pepple12 | 6 | 메인 계정 (설문 생성 + 아카이브 공유) |
| 202100839@inu.ac.kr | pepple12 | 7 | 서브 계정 (다른 유저 설문 테스트용) |

---

## 아카이브 설문 (종료 + 공유완료)

| id | 제목 | 카테고리 | 응답수 | 종료일 |
|----|------|----------|--------|--------|
| 2 | AI 도구 활용 실태조사 | IT_AI | 10명 | 2026-07-11 |
| 3 | 대학생 진로 고민 설문 | CAREER | 8명 | 2026-06-30 |
| 4 | 대학생 수면 패턴 조사 | DAILY | 12명 | 2026-07-05 |

### 관련 API

- `GET /archives` → 아카이브 목록 조회
- `GET /archives/{surveyId}/result` → 결과 조회

### 결과 데이터 형태

- **객관식**: 각 보기별 선택 수 + 퍼센트
- **주관식**: 응답자가 작성한 텍스트 리스트

### 열람 상��

- userId=6이 설문 2, 3을 이미 열람함 (토큰 5씩 차감됨)
- 설문 4는 아직 미열람 상태

---

## 진행중 설문 (응답 가능)

| id | 제목 | 카테고리 | 생성자 | 응답수 | 종료일 |
|----|------|----------|--------|--------|--------|
| 5 | 대학생 카페 이용 습관 조사 | DAILY | userId=6 | 3명 | 07-25 |
| 6 | 게임 플레이 습관 설문 | GAME | userId=6 | 4명 | 07-30 |
| 7 | 서비스/앱 만족도 조사 | SERVICE_APP | userId=6 | 0명 | 07-28 |
| 8 | 대학생 독서 습관 조사 | DAILY | userId=7 | 5명 | 07-28 |
| 9 | 취업 준비 스트레스 설문 | CAREER | userId=7 | 0명 | 07-26 |

### 응답 테스트 방법

- **userId=6** 로그인 → 설문 8, 9에 응답 가능 (타인 설문)
- **userId=7** 로그인 → 설문 5, 6, 7에 응답 가능 (타인 설문)
- **게스트 응답** → 로그인 없이 guestKey만 보내면 응답 가능
- 본인이 만든 설문에는 응답 불가

---

## 토큰 내역 (userId=6)

| 순서 | 유형 | 금액 | 잔액 | 비고 |
|------|------|------|------|------|
| 1 | SIGNUP_BONUS | +100 | 100 | 회원가입 보너스 |
| 2 | SURVEY_CREATE | -30 | 70 | 설문 2 생성 |
| 3 | SURVEY_CREATE | -30 | 40 | 설문 3 생성 |
| 4 | SURVEY_CREATE | -30 | 10 | 설문 4 생성 |

**현재 잔액: 10 토큰**

---

## enum 값 정리

### 카테고리 (category)

```
CAREER, IT_AI, SERVICE_APP, CONSUMER_MARKETING, GAME, SCHOOL_LIFE, DAILY, PSYCHOLOGY, ETC
```

### 직업 (job)

```
STUDENT, UNIVERSITY_STUDENT, GRAD_STUDENT, EMPLOYEE, TEACHER, PROFESSOR, FREELANCER, SELF_EMPLOYED, PUBLIC_OFFICIAL, UNEMPLOYED, ETC
```

### 성별 (gender)

```
MALE, FEMALE
```

### 토큰 거래 유형 (token transaction type)

```
SIGNUP_BONUS, SURVEY_CREATE, SURVEY_PARTICIPATE, RESULT_VIEW, RESULT_SHARE, RESPONDENT_50_BONUS
```

---

## 주요 비즈니스 규칙

| 항목 | 내용 |
|------|------|
| 설문 생성 | 토큰 30 차감 |
| 설문 응답 (회원) | 토큰 적립 |
| 아카이브 열람 | 토큰 5 차감 |
| 50명 응답 보너스 | 설문 응답자 50명 달성 시 생성자에게 보너��� 지급 |
| 설문 종료 조건 | end_date < 오늘 |
| 아카이브 공유 조건 | 본인 설문 + 종료된 설문 |

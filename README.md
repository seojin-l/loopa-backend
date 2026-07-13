# Loopa API — 토큰 기반 설문조사 플랫폼 백엔드

> 설문 생성 · 응답 · 아카이브 열람을 **토큰 이코노미**로 연결하는 REST API

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.1-6DB33F?style=flat-square&logo=spring-boot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-4479A1?style=flat-square&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![AWS EC2](https://img.shields.io/badge/AWS_EC2-FF9900?style=flat-square&logo=amazonec2&logoColor=white)
![AWS RDS](https://img.shields.io/badge/AWS_RDS-527FFF?style=flat-square&logo=amazonrds&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat-square&logo=github-actions&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-009639?style=flat-square&logo=nginx&logoColor=white)

---

## 프로젝트 소개

멋쟁이사자처럼 인천대 14기 팀 프로젝트로, 설문조사를 만들고 응답하면 토큰을 주고받는 플랫폼입니다.

설문 생성자는 토큰을 소비해 설문을 등록하고, 응답자는 참여 보상으로 토큰을 획득합니다. 종료된 설문은 아카이브에 공유하여 다른 사용자가 토큰으로 결과를 열람할 수 있습니다.

- **프론트엔드 레포**: [loopa-frontend](https://github.com/seojin-l/loopa-frontend)

---

## 기술 설계

### 인증: JWT Refresh Token 화이트리스트

블랙리스트 방식은 만료된 토큰을 계속 저장해야 하므로 데이터가 누적됩니다. 화이트리스트 방식은 유효한 토큰만 DB에 저장하고, 갱신 시 기존 토큰을 삭제한 뒤 새 토큰을 저장합니다(Refresh Token Rotation). 저장 공간이 사용자 수에 비례하여 예측 가능하고, 강제 로그아웃 시 해당 행만 삭제하면 즉시 무효화됩니다.

### 인증: 이메일 발송 비동기 처리

이메일 발송은 외부 SMTP 서버 호출이라 1~3초 소요됩니다. 동기 처리 시 트랜잭션이 이메일 발송까지 대기하면서, 발송 실패 시 인증 코드 DB 저장까지 롤백됩니다. `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`로 DB 커밋 후 비동기 발송하여, 이메일 실패가 DB에 영향을 주지 않도록 분리했습니다.

### 토큰: 단일 진입점으로 모듈화

설문 생성, 응답 참여, 아카이브 공유/열람 등 여러 도메인에서 토큰 차감·지급이 발생합니다. `TokenService.record()` 하나로 잔액 검증, 차감, 이력 저장을 처리하여 잔액 불일치를 방지하고, 새로운 토큰 사유가 생겨도 `TokenTxType` enum 추가만으로 확장 가능합니다.

### 토큰: 동시성 제어

토큰 잔액 변경 시 `SELECT FOR UPDATE`(비관적 락)를 사용하여 동시 요청 간 갱신 분실(Lost Update)을 방지합니다. 현재 서비스 규모에서는 비관적 락으로 충분하며, 트래픽이 커지면 낙관적 락이나 Redis 분산 락으로 전환할 수 있습니다.

### 응답: 단일 트랜잭션 원자성

응답 제출 시 답변 저장, 토큰 지급, 50명 보너스 지급이 하나의 트랜잭션으로 처리됩니다. 보너스 지급에 실패하면 응답 저장까지 전체 롤백됩니다. 부분 성공을 허용하면 토큰 잔액 불일치가 발생할 수 있어, 의도적으로 전체 원자성을 유지했습니다.

### DB: 응답 테이블 정규화

복수선택 문항에서 보기를 3개 고르면, Answer에 직접 저장할 경우 동일 문항에 대해 행이 3개 생기면서 삽입 이상이 발생합니다. Answer(1) → AnswerOption(N)으로 분리하여 하나의 답변에 여러 선택지를 정규화 저장합니다. 게스트 응답은 회원과 속성이 다르므로 users 테이블이 아닌 `survey_response.guest_key`로 관리하고, `(survey_id, guest_key)` 유니크 제약으로 중복 응답을 방지합니다.

### 공통: 응답 래퍼와 페이지네이션

모든 API는 `ApiResponse<T>`로 응답 형식을 통일하고, 도메인별 `ErrorCode` enum과 `GlobalExceptionHandler`로 예외를 일괄 처리합니다. 목록 조회는 `CursorPageResponse<T>`로 커서 기반 페이지네이션을 적용하여, 데이터가 늘어나도 일정한 조회 성능을 유지합니다.

---

## 아키텍처 & CI/CD

![아키텍처 & CI/CD](images/cicd%20포함%20diagram.png)

| 구성요소 | 역할 |
|---------|------|
| Nginx | 리버스 프록시, HTTPS 종료 (Let's Encrypt) |
| Spring Boot | API 서버 (Docker 컨테이너) |
| RDS MySQL | 데이터베이스 (Private Subnet, EC2에서만 접근 가능) |
| GitHub Actions | main push 시 Gradle 빌드 → ECR push → EC2 자동 배포 |

**보안 구성**
- EC2: 443(HTTPS), 80(HTTP→리다이렉트), 22(SSH) 포트만 오픈
- RDS: 3306 포트를 EC2 보안그룹에서만 허용
- DB를 Private Subnet에 배치하여 외부 접근 차단

---

## 토큰 이코노미

사용자의 활동에 따라 토큰이 지급·차감됩니다.

| 활동 | 토큰 | 트랜잭션 타입 |
|------|------|-------------|
| 회원가입 | +100 | SIGNUP_BONUS |
| 설문 생성 | -(10 + 객관식×3 + 주관식×5) | SURVEY_CREATE |
| 설문 참여 | +(객관식×1 + 주관식×2) | SURVEY_PARTICIPATE |
| 응답자 50명 달성 | +20 | RESPONDENT_50_BONUS |
| 아카이브 공유 | +10 | RESULT_SHARE |
| 아카이브 열람 | -15 | RESULT_VIEW |

---

## API 엔드포인트

### Auth (`/auth`)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/auth/email-verifications` | - | 이메일 인증번호 발송 |
| POST | `/auth/email-verifications/verify` | - | 인증번호 확인 |
| POST | `/auth/signup` | - | 회원가입 |
| POST | `/auth/login` | - | 로그인 |
| POST | `/auth/token/refresh` | - | Access Token 재발급 |
| POST | `/auth/logout` | O | 로그아웃 |
| POST | `/auth/password/reset` | - | 비밀번호 재설정 |

### Survey (`/surveys`)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/surveys` | - | 설문 목록 (커서 페이지네이션) |
| GET | `/surveys/{id}` | - | 설문 상세 |
| GET | `/surveys/{id}/questions` | - | 설문 문항 |
| POST | `/surveys` | O | 설문 생성 |
| GET | `/surveys/{id}/result` | O | 내 설문 결과 조회 |
| DELETE | `/surveys/{id}` | O | 설문 삭제 |

### Response (`/surveys/{id}/responses`)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/surveys/{id}/responses` | - | 설문 응답 (게스트 가능) |

### Archive (`/archive`)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/archive/surveys` | O | 아카이브 목록 |
| GET | `/archive/surveys/{id}` | O | 열람 정보 조회 |
| POST | `/archive/surveys/{id}/views` | O | 결과 열람 구매 |
| GET | `/archive/surveys/{id}/results` | O | 결과 조회 (크로스탭 필터) |
| GET | `/archive/my-surveys` | O | 내 공유 가능 설문 |
| POST | `/archive/shares` | O | 아카이브 공유 |

### User (`/users`)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/users/me` | O | 내 프로필 |
| GET | `/users/me/surveys` | O | 내 설문 목록 |
| GET | `/users/me/viewed-surveys` | O | 열람한 아카이브 목록 |

---

## ERD

![ERD](images/erd.png)

총 11개 테이블, 정규화 기반 설계

| 테이블 | 설명 |
|--------|------|
| `users` | 회원 정보, 토큰 잔액 |
| `email_verifications` | 이메일 인증 코드 |
| `refresh_tokens` | JWT Refresh Token (화이트리스트) |
| `surveys` | 설문 메타 정보 |
| `questions` | 설문 문항 |
| `question_options` | 객관식 보기 |
| `survey_responses` | 응답 (회원/게스트) |
| `answers` | 문항별 답변 |
| `answer_options` | 객관식 선택 항목 |
| `archive_views` | 아카이브 열람 이력 |
| `token_transactions` | 토큰 거래 이력 |

---

## 프로젝트 구조

```
com.example.loopa
├── domain
│   ├── auth/          인증 (이메일 인증, JWT, 로그인/회원가입)
│   ├── survey/        설문 (생성, 조회, 삭제, 결과)
│   ├── response/      설문 응답 (회원/게스트, 답변 저장)
│   ├── archive/       아카이브 (공유, 열람 구매, 크로스탭 결과)
│   ├── token/         토큰 (잔액 관리, 거래 이력)
│   └── user/          사용자 (프로필, 내 설문/열람 목록)
└── global
    ├── common/        CursorPageResponse<T>
    ├── config/        Security, Async, Swagger
    ├── error/         ErrorCode, ExceptionHandler
    ├── response/      ApiResponse<T>
    └── security/      JwtProvider, JwtAuthenticationFilter
```

---

## 팀 구성

멋쟁이사자처럼 인천대 14기 **Loopa** 팀

- **백엔드**: 이승희, 이서진
- **프론트엔드**: 김아현, 이승민
- **디자인**: 조용현
- **기획**: 임성우

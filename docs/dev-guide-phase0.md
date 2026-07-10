# **0단계 개발 기록 — Global 공통 기반 + 전체 Entity + 도메인 계층 뼈대 + DDL**

> **이 문서의 목적:** 이번 작업에서 만든 모든 파일이 **왜 필요하고**, **무슨 역할이고**, **없으면 어떤 문제가 생기는지** 설명합니다. 코드를 읽기 전에 이 문서를 먼저 보면 각 파일의 존재 이유를 이해할 수 있습니다.
>

---

## **1. 전체 구조 한눈에 보기**

```json
src/main/java/com/example/loopa/
├── global/                          ← 모든 도메인이 공통으로 쓰는 부품
│   ├── error/
│   │   ├── code/
│   │   │   ├── BaseErrorCode.java        ← 에러코드 인터페이스 (규격)
│   │   │   ├── GlobalErrorCode.java      ← 공통 에러코드 (400, 401, 403, 404, 500)
│   │   │   ├── AuthErrorCode.java        ← 인증 관련 에러코드 (AUTH_001~007)
│   │   │   ├── SurveyErrorCode.java      ← 설문 관련 에러코드 (SURVEY_001~005)
│   │   │   ├── ResponseErrorCode.java    ← 응답 관련 에러코드 (RESPONSE_001~004)
│   │   │   ├── ArchiveErrorCode.java     ← 아카이브 관련 에러코드 (ARCHIVE_001~003)
│   │   │   └── TokenErrorCode.java       ← 토큰 관련 에러코드 (TOKEN_001)
│   │   ├── exception/
│   │   │   └── GeneralException.java     ← 우리 프로젝트 전용 예외 클래스
│   │   └── handler/
│   │       └── GlobalExceptionHandler.java ← 예외를 JSON 응답으로 바꿔주는 곳
│   ├── config/
│   │   └── SwaggerConfig.java            ← (기존) API 문서 설정
│   └── response/
│       └── ApiResponse.java              ← (기존) 공통 응답 형식
│
├── domain/
│   ├── user/
│   │   ├── entity/
│   │   │   ├── User.java              ← 회원 테이블
│   │   │   ├── Gender.java            ← 성별 enum
│   │   │   └── Job.java               ← 직업 enum (11종)
│   │   ├── controller/
│   │   │   └── UserController.java    ← USER-01~03 엔드포인트 (뼈대)
│   │   ├── service/
│   │   │   └── UserService.java       ← 회원 비즈니스 로직 (뼈대)
│   │   └── repository/
│   │       └── UserRepository.java    ← findByEmail, existsByEmail, findByIdForUpdate(비관적 락)
│   ├── auth/entity/
│   │   ├── EmailVerification.java     ← 이메일 인증 테이블
│   │   ├── RefreshToken.java          ← 리프레시 토큰 테이블
│   │   └── Purpose.java              ← 인증 목적 enum
│   ├── survey/
│   │   ├── entity/
│   │   │   ├── Survey.java            ← 설문 테이블
│   │   │   ├── Question.java          ← 문항 테이블
│   │   │   ├── QuestionOption.java    ← 보기(선택지) 테이블
│   │   │   ├── Category.java          ← 카테고리 enum (9종)
│   │   │   └── QuestionType.java      ← 문항 유형 enum
│   │   ├── controller/
│   │   │   └── SurveyController.java  ← SURVEY-01~05 엔드포인트 (뼈대)
│   │   ├── service/
│   │   │   └── SurveyService.java     ← 설문 비즈니스 로직 (뼈대)
│   │   └── repository/
│   │       ├── SurveyRepository.java
│   │       ├── QuestionRepository.java
│   │       └── QuestionOptionRepository.java
│   ├── response/
│   │   ├── entity/
│   │   │   ├── SurveyResponse.java    ← 설문 응답 테이블
│   │   │   ├── Answer.java            ← 개별 답변 테이블
│   │   │   └── AnswerOption.java      ← 객관식에서 선택한 보기 테이블
│   │   ├── controller/
│   │   │   └── ResponseController.java ← RESPONSE-01 엔드포인트 (뼈대)
│   │   ├── service/
│   │   │   └── ResponseService.java   ← 응답 비즈니스 로직 (뼈대)
│   │   └── repository/
│   │       ├── SurveyResponseRepository.java
│   │       ├── AnswerRepository.java
│   │       └── AnswerOptionRepository.java
│   ├── token/
│   │   ├── entity/
│   │   │   ├── TokenTransaction.java  ← 토큰 거래내역 테이블 (원장)
│   │   │   └── TokenTxType.java       ← 거래 유형 enum (6종)
│   │   ├── service/
│   │   │   └── TokenService.java      ← 공용 토큰 부품 (뼈대, record 메서드)
│   │   └── repository/
│   │       └── TokenTransactionRepository.java
│   └── archive/
│       ├── entity/
│       │   └── ArchiveView.java       ← 아카이브 열람 기록 테이블
│       ├── controller/
│       │   └── ArchiveController.java ← ARCHIVE-01~06 엔드포인트 (뼈대)
│       ├── service/
│       │   └── ArchiveService.java    ← 아카이브 비즈니스 로직 (뼈대)
│       └── repository/
│           └── ArchiveViewRepository.java

src/main/resources/
├── application.yaml               ← (기존) 기본 설정
├── application-local.yaml         ← 로컬 개발용 DB 설정
├── application-prod.yaml          ← (기존) 운영 설정
└── schema.sql                     ← 참고용 DDL (테이블 생성 SQL)
```

---

## **2. Global 공통 기반 — 에러 처리 시스템**

### **왜 필요한가?**

API 서버는 실패할 때도 클라이언트(앱)에게 **일관된 형식**으로 알려줘야 합니다. 공통 에러 처리가 없으면 이런 일이 벌어집니다:

```
// A 개발자가 만든 API
{ "error": "이메일이 중복됩니다", "status": 400 }

// B 개발자가 만든 API  
{ "isSuccess": false, "msg": "중복 이메일" }

// Spring이 기본으로 뱉는 에러 (우리 형식이 아님)
{ "timestamp": "2026-07-07", "status": 500, "error": "Internal Server Error" }
```

프론트엔드 입장에서는 API마다 에러 형식이 다르면 매번 다르게 처리해야 해서 지옥입니다. 그래서 **모든 에러를 한 곳에서 잡아서** 우리가 정한 `ApiResponse` 형식으로 통일합니다.

### **파일별 역할**

#### **`BaseErrorCode.java` — 에러코드의 규격(인터페이스)**

```java
public interface BaseErrorCode {
    String getCode();      // "AUTH_001" 같은 에러 코드
    String getMessage();   // "이미 사용 중인 이메일입니다." 같은 메시지
    HttpStatus getStatus(); // 400, 401, 404 같은 HTTP 상태코드
}
```

**왜 인터페이스인가?** 에러코드를 도메인별로 나눠서 관리하고 싶기 때문입니다. `AuthErrorCode`, `SurveyErrorCode` 등이 각각 별도 enum이지만, 전부 `BaseErrorCode`를 구현하기 때문에 `GeneralException`이나 `ApiResponse`에서 **어떤 도메인 에러든 똑같이** 받아서 처리할 수 있습니다.

**없으면?** 에러코드 enum을 하나의 거대한 파일에 전부 넣거나, 도메인마다 에러 처리 방식이 달라집니다.

#### **`GlobalErrorCode.java` — 공통 에러코드 (HTTP 기본)**

```java
INVALID_INPUT_VALUE("COMMON_400", "잘못된 요청입니다.", HttpStatus.BAD_REQUEST),
VALIDATION_ERROR("COMMON_400", "입력값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
UNAUTHORIZED("COMMON_401", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
FORBIDDEN("COMMON_403", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
NOT_FOUND("COMMON_404", "대상을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
INTERNAL_SERVER_ERROR("COMMON_500", "서버 에러가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
```

특정 도메인에 속하지 않는 범용 에러들입니다. 예를 들어 "로그인 안 하고 마이페이지 접근" 같은 건 Auth 에러가 아니라 공통 401 에러입니다.

#### **도메인별 ErrorCode — `AuthErrorCode`, `SurveyErrorCode`, `ResponseErrorCode`, `ArchiveErrorCode`, `TokenErrorCode`**

각 도메인의 비즈니스 규칙 위반을 정의합니다. **`loopa_api_full.md` 9절의 에러코드 마스터 표를 그대로 코드화**한 것입니다.

| enum 상수 | 코드 | 의미 | 언제 발생하나 |
| --- | --- | --- | --- |
| SURVEY_NOT_FOUND | SURVEY_001 | 존재하지 않는 설문 | 삭제되었거나 없는 설문 ID로 조회할 때 |
| INVALID_DATE_RANGE | SURVEY_002 | 마감일이 시작일보다 앞섬 | 설문 생성 시 날짜 검증 |
| NO_QUESTIONS | SURVEY_003 | 문항이 0개 | 설문 생성 시 문항 검증 |
| NO_OPTIONS_FOR_MC | SURVEY_004 | 객관식인데 보기 없음 | 설문 생성 시 보기 검증 |
| SHARED_SURVEY_DELETE | SURVEY_005 | 공유된 설문 삭제 시도 | 아카이브에 공유된 설문을 삭제하려 할 때 |

**없으면?** Service 코드에서 에러를 던질 때마다 직접 HTTP 상태코드와 메시지를 하드코딩해야 합니다. "이 에러가 409야 400이야?" 같은 혼란이 생기고, API 명세와 실제 코드가 어긋나기 쉽습니다.

#### **`GeneralException.java` — 우리 프로젝트 전용 예외**

```java
public class GeneralException extends RuntimeException {
    private final BaseErrorCode errorCode;
}
```

Java의 `RuntimeException`을 상속받은 **우리만의 예외 클래스**입니다. Service 계층에서 비즈니스 규칙이 위반되면 이걸 던집니다:

```java
// Service에서 이렇게 사용
if (survey == null) {
    throw new GeneralException(SurveyErrorCode.SURVEY_NOT_FOUND);
}
```

**왜 `RuntimeException`을 그냥 안 쓰나?** `RuntimeException`에는 에러 코드(SURVEY_001)가 없습니다. 그냥 메시지 문자열만 있어서, 나중에 이걸 잡아서 JSON 응답으로 바꿀 때 코드를 뽑아낼 방법이 없습니다. `GeneralException`은 `BaseErrorCode`를 들고 있기 때문에 코드, 메시지, HTTP 상태를 한번에 꺼낼 수 있습니다.

#### **`GlobalExceptionHandler.java` — 예외를 JSON 응답으로 바꿔주는 곳**

이게 핵심입니다. Spring의 `@RestControllerAdvice`를 사용해서, **컨트롤러에서 예외가 터지면 자동으로 여기로 들어옵니다.**

```
[클라이언트 요청]
    → [Controller]
        → [Service] → throw new GeneralException(SurveyErrorCode.SURVEY_NOT_FOUND)
    ← [GlobalExceptionHandler가 자동으로 잡음]
← [클라이언트에게 정해진 JSON 형식으로 응답]
```

4가지 종류의 예외를 잡습니다:

| **잡는 예외** | **언제 발생** | **어떻게 처리** |
| --- | --- | --- |
| `GeneralException` | 우리가 의도적으로 던진 비즈니스 에러 | 에러코드에 담긴 HTTP 상태 + 코드 + 메시지 반환 |
| `MethodArgumentNotValidException` | `@Valid` 검증 실패 (이메일 형식 틀림 등) | 400 + COMMON_400 반환 |
| `DataIntegrityViolationException` | DB 제약조건 위반 (unique 중복 등) | 409 반환. 예: 같은 설문에 두 번 응답 시도 |
| `Exception` | 위에서 못 잡은 모든 예외 | 500 + COMMON_500 반환 (최후 방어선) |

**없으면?** Spring이 기본으로 뱉는 에러 JSON이 나갑니다. 우리의 `{ isSuccess, code, message, result }` 형식이 아니라 `{ timestamp, status, error, path }` 형식이라 프론트에서 처리할 수 없습니다. 또한 매 컨트롤러마다 try-catch를 써야 하는데, 그러면 코드가 지저분해지고 에러 형식 통일이 깨집니다.

---

## **3. Entity — 데이터베이스 테이블을 자바 클래스로**

### **Entity가 뭔가?**

JPA(Hibernate)에서 **Entity = 데이터베이스 테이블 1개**입니다. `@Entity`가 붙은 자바 클래스를 만들면, JPA가 이 클래스를 보고 자동으로 DB 테이블을 만들거나 매핑해줍니다.

```
User.java (Entity)  ←→  users 테이블 (MySQL)
  Long id;           ←→  id BIGINT AUTO_INCREMENT
  String email;      ←→  email VARCHAR(255)
  Gender gender;     ←→  gender VARCHAR(10)  ← enum이 문자열로 저장됨
```

### **엔티티별 상세 설명**

#### **`User.java` — 회원 (`users` 테이블)**

**역할:** 회원 정보를 저장합니다. 이메일, 비밀번호(해시), 성별, 나이, 직업, **토큰 잔액**.

**핵심 메서드:**

- `updatePassword(String password)` — 비밀번호 재설정 시 사용
- `updateTokenBalance(int balance)` — TokenService가 토큰 잔액을 변경할 때 사용

**`tokenBalance` 필드가 왜 User에 있는가?** 토큰 잔액은 `token_transactions` 테이블의 모든 거래를 합산하면 계산할 수 있습니다. 하지만 매번 전체 거래를 합산하면 느리기 때문에, **캐시**로 User에 현재 잔액을 들고 있습니다. 토큰이 이동할 때마다 `token_transactions`에 기록하면서 동시에 `users.token_balance`도 갱신합니다.

#### **`EmailVerification.java` — 이메일 인증 (`email_verifications` 테이블)**

**역할:** 회원가입/비밀번호 찾기 시 이메일로 보낸 인증번호를 저장합니다.

**흐름:**

1. 사용자가 이메일 입력 → 서버가 6자리 코드 생성 → 이 테이블에 저장 + 이메일 발송
2. 사용자가 코드 입력 → 이 테이블에서 조회 → 만료/불일치 확인 → 일치하면 `verified = true`
3. 회원가입 API에서 이 테이블의 `verified`가 `true`인지 확인 → 아니면 가입 거부

#### **`RefreshToken.java` — 리프레시 토큰 (`refresh_tokens` 테이블)**

**역할:** 로그인한 회원의 refresh 토큰 해시를 저장합니다 (화이트리스트 방식).

**왜 해시만 저장하는가?** 원문을 저장하면 DB가 해킹당했을 때 토큰을 그대로 사용할 수 있습니다. SHA-256 해시만 저장하면, DB가 유출돼도 원래 토큰을 복원할 수 없어서 안전합니다.

**화이트리스트란?** "이 테이블에 **있는** 토큰만 유효하다"는 뜻입니다. 로그아웃하면 해당 행을 삭제합니다. → 삭제된 토큰으로 재발급 시도하면 실패합니다.

#### **`Survey.java` — 설문 (`surveys` 테이블)**

**역할:** 설문의 기본 정보를 저장합니다.

**핵심 필드:**

- `creator` — 이 설문을 만든 회원 (User와 연결)
- `startDate` / `endDate` — 설문 기간. **진행중/종료 상태는 이 날짜로 계산합니다** (별도 status 컬럼 없음)
- `sharedToArchive` — 아카이브에 공유했는지 여부. `true`이면 삭제 불가
- `isDeleted` — 소프트 삭제. 실제로 DB에서 지우지 않고 이 플래그만 `true`로 바꿈

**핵심 메서드:**

- `isClosed()` — `LocalDate.now().isAfter(this.endDate)` → 오늘이 마감일 지났으면 종료
- `softDelete()` — `isDeleted = true`로 변경
- `shareToArchive()` — `sharedToArchive = true` + 공유 시각 기록

**`@OneToMany(mappedBy = "survey") List<Question> questions`가 뭔가?** "이 설문에 속한 문항 목록"을 의미합니다. DB에 실제 컬럼이 생기는 건 아니고, JPA가 Question 테이블에서 `survey_id`가 이 설문인 것들을 자동으로 모아줍니다.

- `cascade = CascadeType.ALL` → 설문을 저장하면 문항도 함께 저장됨
- `orphanRemoval = true` → 설문에서 문항을 빼면 DB에서도 삭제됨

#### **`Question.java` — 문항 (`questions` 테이블)**

**역할:** 설문의 개별 질문을 저장합니다.

**핵심 필드:**

- `survey` — 이 문항이 속한 설문 (`@ManyToOne`으로 연결)
- `questionOrder` — 문항 순서 (1번, 2번, 3번...)
- `type` — `MULTIPLE_CHOICE`(객관식) 또는 `SUBJECTIVE`(주관식)
- `isRequired` — 필수 응답 여부. `true`면 이 문항에 답하지 않으면 제출 거부
- `allowMultiple` — 복수 선택 허용 (객관식에서만 의미 있음)

#### **`QuestionOption.java` — 보기 (`question_options` 테이블)**

**역할:** 객관식 문항의 선택지를 저장합니다.

예시: "좋아하는 과일은?" 문항의 보기들: | option_order | content | |-------------|---------| | 1 | 사과 | | 2 | 바나나 | | 3 | 포도 |

주관식 문항에는 보기가 없으므로, 해당 Question에 연결된 QuestionOption이 0개입니다.

#### **`SurveyResponse.java` — 설문 응답 (`survey_responses` 테이블)**

**역할:** "누가 어떤 설문에 응답했는지"를 기록합니다. 답변 내용은 아래 `Answer`에 따로 있고, 이건 **응답 건 자체**입니다.

**회원과 게스트를 어떻게 구분하는가?**

```java
// 회원이 응답할 때
SurveyResponse.ofMember(survey, user);
// → respondent = 회원, guestKey = null

// 게스트가 응답할 때  
SurveyResponse.ofGuest(survey, "abc123-session-key");
// → respondent = null, guestKey = "abc123-session-key"
```

둘은 **상호배타적**입니다. 회원이면 `respondent_id`가 채워지고 `guest_key`는 null. 게스트면 반대.

**중복 응답 방지 (UniqueConstraint):**

```java
@UniqueConstraint(name = "uk_response_member", columnNames = {"survey_id", "respondent_id"})
@UniqueConstraint(name = "uk_response_guest",  columnNames = {"survey_id", "guest_key"})
```

- 회원: 같은 설문에 같은 회원이 두 번 응답 불가 (DB 레벨에서 강제)
- 게스트: 같은 설문에 같은 세션키로 두 번 응답 불가

이걸 **코드에서만 if문으로 체크하면**, 동시 요청(버튼 더블클릭) 시 두 요청이 동시에 "아직 안 했네"로 판단하고 둘 다 저장될 수 있습니다. DB의 unique 제약은 그런 경우에도 하나만 성공시킵니다.

#### **`Answer.java` — 개별 답변 (`answers` 테이블)**

**역할:** 한 응답(SurveyResponse)의 각 문항별 답변을 저장합니다.

- 주관식이면 `answerText`에 텍스트가 들어감
- 객관식이면 `answerText`는 null이고, 선택한 보기는 아래 `AnswerOption`에 저장

#### **`AnswerOption.java` — 선택한 보기 (`answer_options` 테이블)**

**역할:** 객관식 답변에서 **실제로 선택한 보기**를 기록합니다.

예시: 복수 선택 가능한 문항에서 "사과"와 "포도"를 골랐다면: | answer_id | option_id | |-----------|-----------| | 42 | 1 (사과) | | 42 | 3 (포도) |

단일 선택이면 행이 1개, 복수 선택이면 N개 들어갑니다.

#### **`TokenTransaction.java` — 토큰 거래내역 (`token_transactions` 테이블)**

**역할:** 모든 토큰 이동을 기록하는 **원장(장부)**입니다.

**왜 원장이 필요한가?** `users.token_balance`만 있으면 "현재 잔액"은 알 수 있지만, "왜 이 잔액이 됐는지" 추적이 불가능합니다. 원장이 있으면:

- 사용자에게 "설문 참여로 +5토큰 획득" 같은 내역을 보여줄 수 있음
- 버그가 생겼을 때 원장을 합산해서 잔액이 맞는지 검증 가능
- "82 → 58" 같이 변경 전후 잔액을 보여줄 수 있음 (`balance_after` 필드)

**`of` 정적 팩토리 메서드:**

```java
TokenTransaction.of(user, TokenTxType.SURVEY_CREATE, -24, 76, surveyId, null);
// → "user가 설문 생성(SURVEY_CREATE)으로 -24토큰, 남은 잔액 76, 관련 설문 ID는 surveyId"
```

**`relatedSurveyId`, `relatedResponseId`를 왜 `@ManyToOne`이 아니라 `Long`으로 했는가?** 이 필드들은 조회 시 join이 거의 필요 없고, 참고용 기록입니다. `@ManyToOne`으로 하면 불필요한 join이 발생할 수 있고, null이 가능한 FK를 엔티티 연관으로 관리하면 복잡해집니다. 실용적으로 ID만 저장합니다. (DB에는 FK 제약이 걸려 있으므로 정합성은 보장됩니다.)

#### **`ArchiveView.java` — 아카이브 열람 기록 (`archive_views` 테이블)**

**역할:** "누가 어떤 설문 결과를 유료로 열람했는지"를 기록합니다.

**핵심:**

- `UNIQUE(viewer_id, survey_id)` → 같은 설문을 두 번 사면 안 되므로, 이미 있으면 과금하지 않음 (멱등)
- `tokenSpent` — 소모 토큰 (기본 15)

---

## **4. Enum — 고정된 선택지를 코드로**

Enum은 **값이 정해져 있고 바뀌지 않는 것**을 표현합니다.

| **Enum** | **위치** | **값** | **용도** |
| --- | --- | --- | --- |
| `Gender` | user | `MALE`, `FEMALE` | 회원 성별 |
| `Job` | user | `STUDENT` 등 11종 | 회원 직업 |
| `Purpose` | auth | `SIGNUP`, `PASSWORD_RESET` | 이메일 인증 목적 |
| `Category` | survey | `CAREER` 등 9종 | 설문 카테고리 |
| `QuestionType` | survey | `MULTIPLE_CHOICE`, `SUBJECTIVE` | 문항 유형 (객관식/주관식) |
| `TokenTxType` | token | `SIGNUP_BONUS` 등 6종 | 토큰 거래 유형 |

**왜 enum인가? 문자열로 그냥 저장하면 안 되나?**

- 오타 방지: `"MALE"`이 아니라 `"MAL"`로 저장되는 사고를 컴파일 타임에 차단
- IDE 자동완성: `Gender.` 치면 `MALE`, `FEMALE`만 뜸
- DB에는 문자열로 저장됨 (`@Enumerated(EnumType.STRING)`) → MySQL에서 직접 봐도 `MALE`이 보임
  - `EnumType.ORDINAL`(숫자 0, 1로 저장)은 **금지** → enum 순서가 바뀌면 데이터가 깨짐

---

## **5. 도메인별 계층 뼈대 — Controller / Service / Repository**

각 도메인에 Controller, Service, Repository 빈 클래스를 만들어둔 상태입니다. 아직 메서드 구현은 없고, 어떤 엔드포인트가 들어갈지만 주석으로 표시되어 있습니다.

### **계층이 뭔가?**

Spring 백엔드는 요청을 3단계로 나눠서 처리합니다:

```
클라이언트 요청 → Controller → Service → Repository → DB
                  (입구)      (로직)     (DB 접근)
```

| **계층** | **역할** | **예시** |
| --- | --- | --- |
| **Controller** | HTTP 요청을 받고, 응답을 내보냄. 비즈니스 로직 없음 | "POST /surveys 요청이 왔으니 SurveyService에 전달" |
| **Service** | 비즈니스 규칙 검증, 데이터 조합, 트랜잭션 관리 | "마감일이 시작일보다 앞서면 에러, 토큰 차감, 설문 저장" |
| **Repository** | DB와 직접 소통 (JPA가 SQL을 자동 생성해줌) | "surveys 테이블에서 id=1 조회" |

### **도메인별 현황**

| **도메인** | **Controller** | **Service** | **Repository** | **담당 엔드포인트** |
| --- | --- | --- | --- | --- |
| **user** | UserController | UserService | UserRepository | USER-01~03 (내 정보, 등록 설문, 열람 설문) |
| **survey** | SurveyController | SurveyService | SurveyRepository, QuestionRepository, QuestionOptionRepository | SURVEY-01~05 (목록, 상세, 문항, 생성, 삭제) |
| **response** | ResponseController | ResponseService | SurveyResponseRepository, AnswerRepository, AnswerOptionRepository | RESPONSE-01 (응답 제출) |
| **archive** | ArchiveController | ArchiveService | ArchiveViewRepository | ARCHIVE-01~06 (목록, 열람정보, 구매, 결과, 공유목록, 공유) |
| **token** | (없음) | TokenService | TokenTransactionRepository | 다른 Service들이 호출하는 공용 부품 |

**auth 도메인**은 서진님이 feature/signup에서 작업 중이라 여기서는 생성하지 않았습니다.

**token 도메인**에는 Controller가 없습니다. TokenService는 외부에서 직접 API로 호출하는 게 아니라, 다른 Service(AuthService, SurveyService, ResponseService, ArchiveService)가 내부에서 호출하는 **공용 부품**이기 때문입니다.

### **UserRepository에만 메서드가 있는 이유**

UserRepository에는 3개 메서드가 미리 정의되어 있습니다:

- `findByEmail` — 로그인, 회원가입 중복 확인 등 여러 곳에서 사용
- `existsByEmail` — 이메일 존재 여부만 빠르게 확인
- `findByIdForUpdate` — TokenService에서 잔액 변경 시 동시성 제어용 (비관적 락)

이 메서드들은 여러 도메인에서 공통으로 쓰이기 때문에 뼈대 단계에서 미리 만들어둔 것입니다. 나머지 Repository들은 구현 단계에서 필요한 쿼리를 추가하면 됩니다.

---

## **6. DDL 및 설정 파일**

### **`schema.sql`**

전체 테이블 생성 SQL을 한 파일에 모아둔 **참고용** DDL입니다. 실제로는 `spring.jpa.hibernate.ddl-auto=update` 설정으로 JPA가 Entity를 보고 자동으로 테이블을 만들어주므로, 이 파일이 자동 실행되지는 않습니다.

**왜 만들었나?**

- Entity만 보면 DB에 실제로 어떤 테이블/컬럼/제약이 생기는지 한눈에 파악하기 어려움
- DDL 파일이 있으면 "이 프로젝트의 DB 구조"를 SQL로 바로 볼 수 있음
- 필요 시 직접 MySQL에서 실행해서 테이블을 만들 수도 있음

### **`application-local.yaml`**

로컬 개발 시 MySQL 접속 정보입니다.

```yaml
spring.datasource.url: jdbc:mysql://localhost:3306/loopa
spring.jpa.hibernate.ddl-auto: update   # Entity 보고 테이블 자동 생성/변경
spring.jpa.show-sql: true               # 실행되는 SQL을 콘솔에 출력
```

**`ddl-auto: update`란?** 애플리케이션 시작 시 Entity 클래스와 실제 DB 테이블을 비교해서, 없는 테이블/컬럼이 있으면 자동으로 추가합니다. 기존 데이터는 건드리지 않습니다. 운영에서는 위험하지만 개발 중에는 편리합니다.

---

## **7. 엔티티 간 관계도**

```
users ─────────────────────────────────────────────────────┐
  │                                                         │
  │ 1:N                                                     │ 1:N
  ▼                                                         ▼
refresh_tokens                                        token_transactions
email_verifications (FK 없음, email로만 연결)
  │
  │ 1:N (creator)
  ▼
surveys ──────────────────────────────────────────────┐
  │                                                    │
  │ 1:N                                                │
  ▼                                                    ▼
questions                                        survey_responses ←── users (respondent, nullable)
  │                                                    │
  │ 1:N                                                │ 1:N
  ▼                                                    ▼
question_options                                   answers
                                                       │
                                                       │ 1:N
                                                       ▼
                                                   answer_options ──→ question_options

archive_views ──→ users (viewer)
              ──→ surveys
```

**읽는 법:**

- `users → surveys` (1:N) = "한 회원이 여러 설문을 만들 수 있다"
- `surveys → questions` (1:N) = "한 설문에 여러 문항이 있다"
- `survey_responses → users` (N:1, nullable) = "응답에 회원이 연결될 수도 있고(회원), 안 될 수도 있다(게스트)"

---

## **8. 코드에서 반복되는 패턴 설명**

### **패턴 1: `@NoArgsConstructor(access = AccessLevel.PROTECTED)`**

모든 Entity에 있는 이 어노테이션은 **"파라미터 없는 생성자를 만들되, 외부에서 직접 호출은 막는다"**는 뜻입니다.

```java
// 이렇게 쓰면 안 됨 (protected라서 컴파일 에러)
User user = new User();

// 이렇게 써야 함 (우리가 정의한 생성자)
User user = new User("email@test.com", "hashedPw", Gender.MALE, 25, Job.STUDENT);
```

JPA는 내부적으로 기본 생성자가 필요하지만, 개발자가 실수로 빈 객체를 만드는 걸 방지하기 위해 `protected`로 잠가둡니다.

### **패턴 2: `@ManyToOne(fetch = FetchType.LAZY)`**

모든 연관관계에 `LAZY`가 붙어 있습니다. 이건 **"실제로 접근할 때까지 DB 조회를 미룬다"**는 뜻입니다.

```java
// EAGER (기본값): survey를 조회하면 creator(User)도 무조건 함께 조회 → JOIN 발생
// LAZY (우리 설정): survey를 조회해도 creator는 안 가져옴. survey.getCreator() 호출 시 그때 조회

Survey survey = surveyRepository.findById(1L);  // SELECT * FROM surveys WHERE id=1
// 아직 creator는 안 가져옴
survey.getCreator().getEmail();  // 이때 SELECT * FROM users WHERE id=... 실행
```

**왜 LAZY인가?** 설문 목록을 20개 조회할 때 EAGER면 작성자 정보도 20번 조회합니다 (N+1 문제). LAZY면 필요할 때만 가져오므로, 불필요한 쿼리를 줄일 수 있습니다. 목록 조회 시에는 나중에 fetch join으로 한번에 가져오는 전략을 씁니다.

### **패턴 3: `@PrePersist`**

```java
@PrePersist
protected void onCreate() {
    this.createdAt = LocalDateTime.now();
}
```

JPA가 이 엔티티를 **처음 DB에 저장(INSERT)하기 직전에** 자동으로 호출합니다. 매번 `entity.setCreatedAt(LocalDateTime.now())` 하지 않아도 됩니다.

비슷하게 User에는 `@PreUpdate`도 있어서, 수정(UPDATE) 시 `updatedAt`이 자동 갱신됩니다.

### **패턴 4: 정적 팩토리 메서드 (`of`, `ofMember`, `ofGuest`)**

```java
// 생성자 대신 의미 있는 이름의 메서드를 제공
TokenTransaction.of(user, type, amount, balanceAfter, surveyId, responseId);
SurveyResponse.ofMember(survey, user);
SurveyResponse.ofGuest(survey, guestKey);
```

`new SurveyResponse(survey, user, null, 0, LocalDateTime.now())`처럼 생성자에 null을 끼워넣는 것보다, `ofMember(survey, user)`처럼 **의도가 드러나는 이름**을 쓰는 게 읽기 좋습니다. "아, 이건 회원 응답을 만드는 거구나"를 바로 알 수 있습니다.

### **패턴 5: `cascade = CascadeType.ALL, orphanRemoval = true`**

Survey → Question, Question → QuestionOption 관계에 이게 붙어 있습니다.

```java
Survey survey = new Survey(...);
Question q1 = new Question(survey, 1, MULTIPLE_CHOICE, "질문1", true, false);
survey.addQuestion(q1);

surveyRepository.save(survey);  // survey만 save해도 q1도 함께 INSERT됨 (cascade)
```

- `cascade = ALL` → 부모(Survey)를 저장/삭제하면 자식(Question)도 따라감
- `orphanRemoval = true` → 부모의 리스트에서 자식을 빼면(`questions.remove(q1)`) DB에서도 삭제됨

**없으면?** 설문 생성 시 `surveyRepository.save(survey)` + `questionRepository.saveAll(questions)` + `questionOptionRepository.saveAll(options)`를 각각 호출해야 합니다. cascade가 있으면 survey만 저장하면 끝입니다.

---

## **9. 서진님 feature/signup과의 관계**

서진님이 `feature/signup`에서 이미 만든 파일:

- `User.java`, `Gender.java`, `Job.java`
- `EmailVerification.java`, `Purpose.java`
- `AuthErrorCode.java`, `GeneralException.java`, `GlobalExceptionHandler.java`
- `AuthController.java`, `AuthService.java` + 관련 DTO/Repository

이번 작업에서 **같은 이름의 파일을 develop에도 만들었습니다.** 이유:

- Survey, Response 등의 Entity가 User를 참조(`@ManyToOne`)하기 때문에, User Entity가 없으면 **컴파일이 안 됩니다**
- develop 브랜치에서 독립적으로 빌드·테스트가 가능해야 합니다

**머지 시 주의점:** 

| 파일 | 충돌 가능성 | 대응 |
| --- | --- | --- |
| User.java | 낮음 | develop에서 updateTokenBalance() 메서드만 추가됨. 서진님 코드에 없는 메서드라 자동 머지 가능 |
| Gender.java, Job.java, Purpose.java | 없음 | 내용 동일 |
| EmailVerification.java | 없음 | 내용 동일 |
| AuthErrorCode.java | 높음 | 코드 할당이 다름 (서진님: AUTH_002=인증번호 발송내역 없음, 문서: AUTH_002=인증번호 불일치). 문서 기준으로 통일 필요 |
| GlobalExceptionHandler.java | 중간 | develop에서 DataIntegrityViolationException 핸들러 추가됨. 서진님 것에 합치면 됨 |
| GlobalErrorCode.java | 중간 | develop에서 UNAUTHORIZED, FORBIDDEN, NOT_FOUND 추가됨 |

**머지 전략 제안:** develop의 global + entity를 기반으로, feature/signup의 AuthController/AuthService/DTO만 가져오는 방식이 깔끔합니다.
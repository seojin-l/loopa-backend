# Auth 도메인 개발 기록

---

## 1. Auth 도메인이 뭔가?

사용자를 **가입시키고, 로그인시키고, 누가 누군지 확인**하는 도메인입니다. 다른 모든 도메인의 기반이 됩니다:

```
Auth (가입/로그인 → JWT 발급)
  ↓ JWT가 있어야
Survey (설문 생성/삭제는 회원만)
Response (응답 제출 시 회원이면 토큰 지급)
Archive (결과 구매/공유는 회원만)
User (내 정보 조회)
```

한 줄 요약: **"이 사람이 누구인지 증명하는 시스템"**

---

## 2. 먼저 알아야 할 개념

### JWT가 뭔가?

JWT(JSON Web Token)는 **서버가 발급한 신분증**입니다.

일상생활로 비유하면:
- 로그인 = 여권 사무소에서 신분 확인 후 여권 발급
- JWT = 발급받은 여권
- API 호출 시 JWT 전송 = 공항에서 여권 보여주기
- 서버가 JWT 검증 = 공항 직원이 여권 진위 확인

**여권 안에는 이런 정보가 들어있습니다:**

```json
{
  "sub": "42",           ← userId (이 여권의 주인이 42번 유저)
  "email": "user@example.com",
  "type": "ACCESS",      ← 액세스 토큰인지 리프레시 토큰인지
  "iat": 1720000000,     ← 발급 시각
  "exp": 1720003600      ← 만료 시각
}
```

이 정보는 서버의 **비밀키(secret)로 서명**되어 있습니다. 누가 내용을 조작하면 서명이 안 맞아서 바로 들킵니다. 그래서 JWT는 위조가 불가능합니다.

### 왜 액세스 토큰과 리프레시 토큰, 두 개인가?

| | 액세스 토큰 | 리프레시 토큰 |
|--|-----------|-------------|
| 용도 | API 호출할 때 "나 42번이야" 증명 | 액세스 토큰이 만료되면 새로 발급받기 |
| 수명 | 짧음 (1시간) | 김 (7일) |
| 어디에 보냄 | 매 요청마다 Authorization 헤더에 | `/token/refresh` 호출할 때만 |

**왜 이렇게 나눴나?**

액세스 토큰을 7일짜리로 만들면 편하지만, 만약 탈취당하면 7일 동안 악용됩니다. 그래서:
- 액세스 토큰은 짧게 → 탈취돼도 1시간 뒤에 만료
- 리프레시 토큰은 길게 → 액세스 토큰 만료되면 새로 발급받는 데만 사용

프론트엔드 입장에서는:
```
1. 로그인 → 액세스 토큰 + 리프레시 토큰 둘 다 받음
2. API 호출할 때마다 액세스 토큰을 헤더에 넣음
3. 1시간 후 → 서버가 "토큰 만료됨" 응답
4. 리프레시 토큰으로 /token/refresh 호출 → 새 액세스 토큰 받음
5. 다시 API 호출 계속
6. 7일 후 → 리프레시 토큰도 만료 → 다시 로그인
```

### Stateless(무상태)가 뭔가?

서버가 **세션을 저장하지 않는다**는 뜻입니다.

**세션 방식 (전통적):**
```
서버 메모리: { "세션ID_abc": { userId: 42, loginTime: ... } }
클라이언트: 쿠키에 세션ID_abc 저장
→ 서버가 요청받을 때마다 메모리에서 세션 찾아봄
→ 서버가 재시작되면 전부 날아감
→ 서버가 여러 대면 세션 공유 문제 발생
```

**JWT 방식 (우리 프로젝트):**
```
서버: 아무것도 저장 안 함 (리프레시 토큰만 DB에 저장)
클라이언트: JWT를 직접 들고 다님
→ 서버가 토큰 안의 정보만 보고 판단
→ 서버 재시작해도 상관없음
→ 서버가 여러 대여도 상관없음 (같은 비밀키만 공유하면 됨)
```

---

## 3. 파일 구조

```
domain/auth/
├── controller/
│   └── AuthController.java              ← HTTP 요청 받는 입구 (6개 엔드포인트)
├── service/
│   └── AuthService.java                 ← 비즈니스 로직 (검증, 토큰 발급, 저장)
├── repository/
│   ├── EmailVerificationRepository.java ← email_verifications 테이블 조회
│   └── RefreshTokenRepository.java      ← refresh_tokens 테이블 조회
├── entity/
│   ├── EmailVerification.java           ← 이메일 인증 레코드
│   ├── RefreshToken.java                ← 리프레시 토큰 레코드
│   └── Purpose.java                     ← 인증 목적 (SIGNUP, PASSWORD_RESET)
└── dto/
    ├── request/
    │   ├── EmailVerificationSendRequest.java    ← 인증번호 발송
    │   ├── EmailVerificationVerifyRequest.java  ← 인증번호 확인
    │   ├── SignupRequest.java                   ← 회원가입
    │   ├── LoginRequest.java                    ← 로그인
    │   ├── TokenRefreshRequest.java             ← 토큰 재발급
    │   └── LogoutRequest.java                   ← 로그아웃
    └── response/
        ├── AuthMessageResponse.java      ← 메시지만 반환 (가입, 인증, 로그아웃)
        ├── LoginResponse.java            ← 액세스 토큰 + 리프레시 토큰
        └── TokenRefreshResponse.java     ← 새 액세스 토큰

global/config/
└── SecurityConfig.java                   ← 접근제어, CORS, 필터 등록

global/security/
├── JwtProvider.java                      ← JWT 생성/검증/파싱
├── JwtAuthenticationFilter.java          ← 매 요청마다 JWT 확인하는 필터
└── CustomAuthenticationEntryPoint.java   ← 인증 실패 시 JSON 에러 응답
```

---

## 4. 전체 그림: 요청이 들어오면 어떤 일이 일어나는가?

모든 HTTP 요청은 컨트롤러에 도달하기 전에 **필터 체인**을 거칩니다.

```
클라이언트 요청
    ↓
[CORS 필터]                    ← 다른 도메인(localhost:3000)에서 온 요청 허용
    ↓
[JwtAuthenticationFilter]      ← Authorization 헤더에서 JWT 꺼내서 검증
    ↓
[SecurityConfig 접근제어]       ← 이 URL은 로그인 필수인가? 게스트도 되는가?
    ↓
컨트롤러
```

### JwtAuthenticationFilter가 하는 일

```java
// 1. Authorization 헤더에서 토큰 꺼냄
String bearerToken = request.getHeader("Authorization");
// "Bearer eyJhbGciOi..." → "eyJhbGciOi..." (앞의 "Bearer " 제거)

// 2. 토큰이 있고, 유효하고, 액세스 토큰이면
if (token != null && jwtProvider.validateToken(token) && jwtProvider.isAccessToken(token)) {

    // 3. 토큰에서 userId 꺼냄
    Long userId = jwtProvider.getUserId(token);  // 예: 42

    // 4. Spring Security에 "42번 유저가 인증됨"이라고 등록
    UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
    SecurityContextHolder.getContext().setAuthentication(authentication);
}

// 5. 토큰이 없거나 유효하지 않으면? → 아무것도 안 함 (그냥 통과)
//    → 이후 SecurityConfig에서 authenticated() 엔드포인트면 401 에러
//    → permitAll() 엔드포인트면 정상 통과 (userId = null)
```

### SecurityConfig의 접근제어

```java
.authorizeHttpRequests(auth -> auth
    // 이 URL들은 누구나 접근 가능 (JWT 없어도 됨)
    .requestMatchers("/email-verifications", "/signup", "/login", ...).permitAll()
    .requestMatchers(HttpMethod.GET, "/surveys", "/surveys/{surveyId}", ...).permitAll()
    .requestMatchers(HttpMethod.POST, "/surveys/{surveyId}/responses").permitAll()

    // 나머지 전부는 JWT 필수
    .anyRequest().authenticated()
)
```

JWT 없이 인증 필수 URL에 접근하면? → `CustomAuthenticationEntryPoint`가 작동:

```json
{
  "success": false,
  "code": "COMMON_401",
  "message": "인증이 필요합니다."
}
```

### 컨트롤러에서 userId를 받는 방법

```java
@GetMapping("/me")
public ResponseEntity<ApiResponse<UserMeResponse>> getMyInfo(
        @AuthenticationPrincipal Long userId) {  // ← Spring이 SecurityContext에서 꺼내줌
    ...
}
```

`@AuthenticationPrincipal`은 위에서 `JwtAuthenticationFilter`가 등록한 userId를 자동으로 꺼내줍니다. 직접 토큰을 파싱하거나 헤더를 읽을 필요가 없습니다.

---

## 5. 엔드포인트 6개 전체 흐름

### AUTH-01 · 인증번호 발송 `POST /email-verifications`

**게스트 전용.** 회원가입 전에 이메일 인증을 시작합니다.

```
클라이언트 → POST /email-verifications { "email": "user@example.com" }
         → AuthController.sendSignupEmailVerification()
         → AuthService.sendSignupEmailVerification()
         → ① 이미 가입된 이메일인지 확인
         → ② 마지막 발송 후 1분 이내인지 확인 (Rate Limiting)
         → ③ 기존 인증 레코드 삭제
         → ④ 6자리 인증번호 생성 (SecureRandom)
         → ⑤ EmailVerification 엔티티 저장 (만료: 10분 후)
         ← { "message": "인증번호가 발송되었습니다" }
```

**Rate Limiting은 왜 있는가?**

같은 이메일로 무한 요청하면 이메일 스팸이 되고 DB에 레코드가 무한히 쌓입니다. 그래서 마지막 발송 후 1분 이내에 다시 요청하면 거부합니다. 1분이 지나면 재발송이 가능합니다.

**재발송하면 이전 인증번호는?**

삭제됩니다. `deleteAllByEmailAndPurpose()`로 해당 이메일의 기존 인증 레코드를 전부 지우고 새로 만듭니다. 항상 최신 인증번호 하나만 유효합니다.

**인증번호 생성:**

```java
private static final SecureRandom SECURE_RANDOM = new SecureRandom();

private String createVerificationCode() {
    int number = SECURE_RANDOM.nextInt(1_000_000);  // 0 ~ 999999
    return String.format("%06d", number);            // "042857" 형태
}
```

`SecureRandom`을 쓰는 이유: 일반 `Random`은 시드값을 알면 다음 숫자를 예측할 수 있습니다. `SecureRandom`은 운영체제의 엔트로피 풀을 사용해서 예측이 불가능합니다.

---

### AUTH-02 · 인증번호 확인 `POST /email-verifications/verify`

**게스트 전용.** 발송된 인증번호를 입력해서 이메일 소유를 증명합니다.

```
클라이언트 → POST /email-verifications/verify { "email": "user@example.com", "code": "042857" }
         → AuthService.verifySignupEmail()
         → ① 해당 이메일의 인증 레코드 조회
         → ② 만료 시간 확인
         → ③ 시도 횟수 확인 (브루트포스 방어)
         → ④ 코드 비교
         → ⑤ 성공 시 verified = true, 만료시간 30분으로 재설정
         ← { "message": "인증번호가 일치합니다" }
```

**브루트포스 방어가 뭔가?**

6자리 숫자(000000~999999)를 처음부터 끝까지 전부 시도하면 반드시 맞출 수 있습니다. 이걸 막기 위해 **5번까지만** 시도할 수 있습니다.

```
1번째 실패 → "인증번호가 일치하지 않습니다" (attemptCount = 1)
2번째 실패 → "인증번호가 일치하지 않습니다" (attemptCount = 2)
3번째 실패 → "인증번호가 일치하지 않습니다" (attemptCount = 3)
4번째 실패 → "인증번호가 일치하지 않습니다" (attemptCount = 4)
5번째 실패 → "인증번호가 일치하지 않습니다" (attemptCount = 5, 인증번호 무효화)
6번째 시도 → "인증 시도 횟수를 초과했습니다. 인증번호를 재발송해주세요."
```

5번째 실패 시 `invalidate()`가 호출되어 만료시간을 현재로 당깁니다. 이 인증번호는 더 이상 사용할 수 없고, 새로 발급받아야 합니다.

**인증 성공 시 만료시간 재설정:**

```java
public void verify() {
    this.verified = true;
    this.expiresAt = LocalDateTime.now().plusMinutes(30);
}
```

인증번호 원래 만료시간은 10분이지만, 인증에 성공하면 **30분으로 재설정**합니다. 이 30분 안에 회원가입을 완료해야 합니다. 인증만 해놓고 며칠 뒤에 가입하는 걸 방지합니다.

---

### AUTH-03 · 회원가입 `POST /signup` → 201 Created

**게스트 전용.** 이메일 인증이 완료된 상태에서 가입합니다.

```
클라이언트 → POST /signup {
               "email": "user@example.com",
               "password": "mypassword123",
               "gender": "MALE",
               "age": 25,
               "job": "UNIVERSITY_STUDENT"
             }
         → AuthService.signup()
         → ① 이메일 중복 확인
         → ② 이메일 인증 완료 + 만료 전인지 확인
         → ③ 비밀번호 암호화 (BCrypt)
         → ④ User 엔티티 생성 및 저장 (tokenBalance = 0)
         → ⑤ 인증 레코드 삭제 (정리)
         ← 201 Created { "message": "회원가입이 완료되었습니다" }
```

**비밀번호 검증:**

```java
@NotBlank(message = "비밀번호를 입력해주세요.")
@Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
String password
```

8자 이상만 허용합니다. "1"이나 "abc" 같은 약한 비밀번호로 가입할 수 없습니다.

**비밀번호 암호화:**

```java
String encodedPassword = passwordEncoder.encode(request.password());
// "mypassword123" → "$2a$10$N9qo8uLOickgx2ZMRZoMy..." (BCrypt 해시)
```

DB에는 원본 비밀번호가 아니라 **BCrypt로 해시된 값**이 저장됩니다. 해시는 되돌릴 수 없어서, DB가 유출되어도 원본 비밀번호를 알 수 없습니다.

**가입 가능한 성별/직업:**

```java
// Gender
MALE, FEMALE

// Job
STUDENT, UNIVERSITY_STUDENT, GRAD_STUDENT, EMPLOYEE, TEACHER,
PROFESSOR, FREELANCER, SELF_EMPLOYED, PUBLIC_OFFICIAL, UNEMPLOYED, ETC
```

---

### AUTH-04 · 로그인 `POST /login`

**게스트 전용.** 이메일/비밀번호로 로그인하고 토큰을 받습니다.

```
클라이언트 → POST /login { "email": "user@example.com", "password": "mypassword123" }
         → AuthService.login()
         → ① 이메일로 User 조회 (없으면 에러)
         → ② 비밀번호 비교 (BCrypt 해시 비교)
         → ③ 액세스 토큰 생성 (userId + email + type=ACCESS)
         → ④ 리프레시 토큰 생성 (userId + email + type=REFRESH)
         → ⑤ 기존 리프레시 토큰 삭제 + flush
         → ⑥ 새 리프레시 토큰 DB 저장
         ← { "accessToken": "eyJ...", "refreshToken": "eyJ..." }
```

**비밀번호 비교 과정:**

```java
passwordEncoder.matches("mypassword123", "$2a$10$N9qo8uLOickgx2ZMRZoMy...")
// → 입력값을 같은 방식으로 해시해서 저장된 해시와 비교
// → true면 일치, false면 불일치
```

원본 비밀번호를 복원하는 게 아니라, 입력한 비밀번호를 해시해서 저장된 해시와 **비교**합니다.

**이메일이 틀려도, 비밀번호가 틀려도 같은 에러를 반환하는 이유:**

```java
// 이메일 없음 → AUTH_005: "이메일 또는 비밀번호가 일치하지 않습니다."
// 비번 틀림  → AUTH_005: "이메일 또는 비밀번호가 일치하지 않습니다."
```

"이메일이 존재하지 않습니다"와 "비밀번호가 틀렸습니다"를 구분하면, 공격자가 어떤 이메일이 가입되어 있는지 알아낼 수 있습니다. 보안상 둘을 구분하지 않습니다.

**기존 리프레시 토큰을 삭제하는 이유:**

한 유저당 리프레시 토큰은 하나만 유효합니다. 새로 로그인하면 이전 토큰은 무효화됩니다. 다른 기기에서 로그인하면 이전 기기의 세션이 끊어지는 것과 같습니다.

**flush()를 쓰는 이유:**

```java
refreshTokenRepository.deleteAllByUser(user);   // DELETE 예약
refreshTokenRepository.flush();                  // DELETE 즉시 실행

RefreshToken savedRefreshToken = new RefreshToken(...);
refreshTokenRepository.save(savedRefreshToken);  // INSERT
```

JPA는 SQL 실행 순서를 최적화합니다. `flush()`가 없으면 JPA가 INSERT를 DELETE보다 먼저 실행할 수 있습니다. 리프레시 토큰의 `token` 컬럼에 unique 제약이 있어서, 삭제 전에 삽입하면 unique 위반 에러가 날 수 있습니다. `flush()`로 DELETE를 먼저 확실히 실행합니다.

---

### AUTH-05 · 토큰 재발급 `POST /token/refresh`

**게스트 허용 (permitAll).** 액세스 토큰이 만료되었을 때 새로 발급받습니다.

```
클라이언트 → POST /token/refresh { "refreshToken": "eyJ..." }
         → AuthService.refresh()
         → ① JWT 서명 검증 (유효한 토큰인지)
         → ② type이 REFRESH인지 확인 (액세스 토큰으로 재발급 시도 방지)
         → ③ DB에 저장된 토큰인지 확인 (로그아웃한 토큰이면 없음)
         → ④ 만료 여부 확인
         → ⑤ 새 액세스 토큰 발급
         ← { "accessToken": "eyJ..." }
```

**왜 permitAll인가?**

토큰 재발급을 호출하는 시점은 **액세스 토큰이 만료된 상태**입니다. 이 URL을 `authenticated()`로 설정하면, 만료된 액세스 토큰 때문에 401이 나와서 재발급 자체가 불가능해집니다. 그래서 이 엔드포인트는 JWT 없이도 접근 가능해야 합니다.

대신 리프레시 토큰 자체를 **3중으로 검증**합니다:
1. JWT 서명이 유효한가? (위조 방지)
2. type이 REFRESH인가? (액세스 토큰 도용 방지)
3. DB에 존재하는가? (로그아웃한 토큰 재사용 방지)

---

### AUTH-06 · 로그아웃 `POST /logout`

**회원 전용.** 리프레시 토큰을 무효화합니다.

```
클라이언트 → POST /logout { "refreshToken": "eyJ..." }
         → AuthService.logout()
         → ① JWT 서명 검증
         → ② type이 REFRESH인지 확인
         → ③ DB에서 해당 리프레시 토큰 조회
         → ④ DB에서 삭제
         ← { "message": "로그아웃되었습니다." }
```

**로그아웃하면 어떤 일이 일어나는가?**

리프레시 토큰이 DB에서 삭제됩니다. 이후에 이 리프레시 토큰으로 `/token/refresh`를 호출하면, DB에 없으니까 실패합니다.

액세스 토큰은? JWT 특성상 **즉시 무효화가 불가능**합니다. 이미 발급된 액세스 토큰은 만료될 때까지(1시간) 유효합니다. 하지만 리프레시 토큰이 삭제되었으므로 액세스 토큰이 만료된 후에는 재발급을 받을 수 없어서, 사실상 1시간 내에 완전히 로그아웃됩니다.

---

## 6. DB 테이블

### email_verifications

이메일 인증번호를 저장하는 테이블입니다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 생성 |
| email | VARCHAR(255) | 인증 대상 이메일 |
| code | VARCHAR(10) | 6자리 인증번호 |
| purpose | VARCHAR(20) | SIGNUP 또는 PASSWORD_RESET |
| verified | BOOLEAN | 인증 성공 여부 (기본 false) |
| attempt_count | INT | 시도 횟수 (기본 0, 최대 5) |
| expires_at | DATETIME | 만료 시각 (발송 후 10분, 인증 후 30분) |
| created_at | DATETIME | 생성 시각 |

**생애주기:**
```
생성 (verified=false, attemptCount=0, 만료=10분후)
  ↓ 인증 성공
verified=true, 만료=30분후로 재설정
  ↓ 가입 완료 또는 재발송
삭제 (deleteAllByEmailAndPurpose)
```

### refresh_tokens

리프레시 토큰을 저장하는 테이블입니다.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT PK | 자동 생성 |
| user_id | BIGINT FK | users 테이블 참조 |
| token | VARCHAR(500) UNIQUE | JWT 리프레시 토큰 문자열 |
| expires_at | DATETIME | 만료 시각 |
| created_at | DATETIME | 생성 시각 |

**왜 리프레시 토큰만 DB에 저장하는가?**

액세스 토큰은 저장하지 않습니다. 매 요청마다 DB를 조회하면 성능이 떨어지니까요. 액세스 토큰은 JWT 서명만으로 검증합니다.

리프레시 토큰은 DB에 저장합니다. 이유는:
- 로그아웃 시 토큰을 **삭제**해서 무효화할 수 있어야 하니까
- 새 로그인 시 기존 토큰을 **삭제**해서 한 기기만 유효하게 할 수 있어야 하니까

---

## 7. 엔티티

### EmailVerification

```java
@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    private static final int MAX_ATTEMPTS = 5;

    private Long id;
    private String email;
    private String code;
    private Purpose purpose;       // SIGNUP or PASSWORD_RESET
    private boolean verified;      // 인증 성공 여부
    private int attemptCount;      // 실패 횟수
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    // 생성자: verified=false, attemptCount=0
    public EmailVerification(String email, String code, Purpose purpose, LocalDateTime expiresAt) { ... }

    // 인증 성공
    public void verify() {
        this.verified = true;
        this.expiresAt = LocalDateTime.now().plusMinutes(30);  // 가입 유효기간
    }

    // 실패 횟수 증가
    public void incrementAttempt() { this.attemptCount++; }

    // 5번 이상 실패했는지
    public boolean isMaxAttemptExceeded() { return this.attemptCount >= MAX_ATTEMPTS; }

    // 인증번호 무효화 (만료시간을 현재로 당김)
    public void invalidate() { this.expiresAt = LocalDateTime.now(); }
}
```

### RefreshToken

```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    private Long id;
    private User user;           // @ManyToOne (한 유저가 여러 토큰 가질 수 있지만, 로그인마다 삭제 후 재발급)
    private String token;        // JWT 문자열 (unique)
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
```

---

## 8. DTO 구조

### Request

| DTO | 필드 | 검증 |
|-----|------|------|
| EmailVerificationSendRequest | email | @NotBlank + @Email |
| EmailVerificationVerifyRequest | email, code | @NotBlank + @Email, @NotBlank |
| SignupRequest | email, password, gender, age, job | @Email, @Size(min=8), @NotNull, @Min(1), @NotNull |
| LoginRequest | email, password | @NotBlank + @Email, @NotBlank |
| TokenRefreshRequest | refreshToken | @NotBlank |
| LogoutRequest | refreshToken | @NotBlank |

### Response

| DTO | 필드 | 용도 |
|-----|------|------|
| AuthMessageResponse | message | 가입, 인증, 로그아웃 결과 메시지 |
| LoginResponse | accessToken, refreshToken | 로그인 성공 시 토큰 2개 |
| TokenRefreshResponse | accessToken | 재발급된 액세스 토큰 |

---

## 9. Security 설정 상세

### SecurityConfig — 무엇이 열려있고 무엇이 잠겨있는가

```
[게스트 허용 — JWT 없이도 접근 가능]
  POST /email-verifications        ← 인증번호 발송
  POST /email-verifications/verify ← 인증번호 확인
  POST /signup                     ← 회원가입
  POST /login                      ← 로그인
  POST /token/refresh              ← 토큰 재발급
  GET  /surveys                    ← 설문 목록 조회
  GET  /surveys/{id}               ← 설문 상세 조회
  GET  /surveys/{id}/questions     ← 문항 조회
  POST /surveys/{id}/responses     ← 설문 응답 제출
  GET  /swagger-ui/**              ← API 문서
  GET  /v3/api-docs/**             ← API 문서
  GET  /actuator/**                ← 모니터링

[회원 전용 — JWT 필수]
  POST   /surveys                  ← 설문 생성
  DELETE /surveys/{id}             ← 설문 삭제
  POST   /logout                   ← 로그아웃
  GET    /users/me                 ← 내 정보 조회
  GET    /archive/surveys          ← 아카이브 목록
  GET    /archive/surveys/{id}     ← 아카이브 상세
  POST   /archive/surveys/{id}/views  ← 결과 구매
  GET    /archive/surveys/{id}/results ← 결과 조회
  GET    /archive/my-surveys       ← 내 공유 가능 설문
  POST   /archive/shares           ← 아카이브 공유
  (그 외 모든 엔드포인트)
```

### CORS 설정 — 프론트엔드 연동

```java
config.setAllowedOrigins(List.of("http://localhost:3000"));
config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
config.setAllowedHeaders(List.of("*"));
config.setAllowCredentials(true);
```

프론트엔드(localhost:3000)에서 백엔드(localhost:8080)로 API를 호출하면, 브라우저가 "다른 도메인"이라고 차단합니다. CORS 설정은 "이 도메인에서 오는 요청은 허용해도 돼"라고 브라우저에게 알려주는 것입니다.

---

## 10. JwtProvider — 토큰 생성과 검증

```java
@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secret;                   // application.yaml에서 주입

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;       // 밀리초 (예: 3600000 = 1시간)

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;      // 밀리초 (예: 604800000 = 7일)
}
```

**토큰 생성:**

```java
public String createToken(Long userId, String email, String tokenType, long expirationTime) {
    return Jwts.builder()
            .subject(String.valueOf(userId))    // "42"
            .claim("email", email)              // "user@example.com"
            .claim("type", tokenType)           // "ACCESS" 또는 "REFRESH"
            .issuedAt(now)                      // 발급 시각
            .expiration(expiration)             // 만료 시각
            .signWith(secretKey)                // 비밀키로 서명
            .compact();                         // JWT 문자열 생성
}
```

**토큰 검증:**

```java
public boolean validateToken(String token) {
    try {
        parseClaims(token);   // 서명 검증 + 만료 확인
        return true;
    } catch (Exception e) {
        return false;          // 위조, 만료, 형식 오류 등
    }
}
```

`parseClaims()`는 비밀키로 서명을 검증합니다. 토큰이 조작되었거나 만료되었으면 예외가 발생합니다.

---

## 11. 에러 발생 시나리오 정리

| 상황 | 에러코드 | HTTP | 어디서 발생 |
|------|---------|------|------------|
| 이미 가입된 이메일 | AUTH_001 | 409 | sendSignup, signup |
| 인증번호 불일치 | AUTH_002 | 400 | verifySignupEmail |
| 인증번호 만료 | AUTH_003 | 400 | verifySignupEmail |
| 이메일 인증 미완료 | AUTH_004 | 400 | signup |
| 이메일/비밀번호 불일치 | AUTH_005 | 401 | login |
| 유효하지 않은 리프레시 토큰 | AUTH_006 | 401 | refresh, logout |
| 등록되지 않은 이메일 | AUTH_007 | 404 | (비밀번호 재설정용, 미구현) |
| 만료된 리프레시 토큰 | AUTH_008 | 401 | refresh |
| 인증 시도 횟수 초과 | AUTH_009 | 429 | verifySignupEmail |
| 인증번호 발송 제한 (1분) | AUTH_010 | 429 | sendSignup |
| JWT 없이 인증 필수 API 접근 | COMMON_401 | 401 | CustomAuthenticationEntryPoint |

---

## 12. 프론트엔드 연동 가이드

### 회원가입 흐름

```
[이메일 입력] → POST /email-verifications
  ↓ 200: 인증번호 입력 UI 표시
  ↓ 429 (AUTH_010): "잠시 후 다시 시도해주세요" 표시

[인증번호 입력] → POST /email-verifications/verify
  ↓ 200: 비밀번호/개인정보 입력 UI 표시
  ↓ 400 (AUTH_002): "인증번호가 틀렸습니다" 표시
  ↓ 400 (AUTH_003): "만료되었습니다. 재발송해주세요" 표시
  ↓ 429 (AUTH_009): "5회 초과. 재발송해주세요" 표시

[가입 정보 입력] → POST /signup
  ↓ 201: 가입 완료, 로그인 화면으로 이동
```

### 토큰 관리

```javascript
// 로그인 후 토큰 저장
const { accessToken, refreshToken } = await login(email, password);
localStorage.setItem('accessToken', accessToken);
localStorage.setItem('refreshToken', refreshToken);

// 모든 API 요청에 액세스 토큰 첨부
headers: { 'Authorization': `Bearer ${accessToken}` }

// 401 응답 받으면 토큰 재발급 시도
if (response.status === 401) {
    const { accessToken: newToken } = await refresh(refreshToken);
    localStorage.setItem('accessToken', newToken);
    // 원래 요청 재시도
}

// 재발급도 실패하면 → 로그인 화면으로 이동
```

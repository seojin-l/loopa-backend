# Auth 코드 리뷰 수정 기록

---

## 1. 배경: 왜 이 작업을 했는가?

서진님이 인증 기능(회원가입, 로그인, 로그아웃, 토큰 재발급)을 구현해서 develop에 병합했습니다. 코드 리뷰를 진행한 결과, 보안 취약점과 로직 누락이 총 18건 발견되었습니다.

크게 세 종류입니다:

| 분류 | 건수 | 의미 |
|------|------|------|
| **Critical** | 5건 | 보안 구멍. 이대로 배포하면 공격에 노출됨 |
| **Major** | 10건 | 장애나 데이터 정합성 문제를 일으킬 수 있음 |
| **Minor** | 3건 | 코드 품질. 당장 터지진 않지만 정리 필요 |

---

## 2. 수정한 파일 목록

```
global/config/SecurityConfig.java          ← 접근제어 + CORS
global/security/CustomAuthenticationEntryPoint.java  ← 신규 생성
domain/auth/entity/EmailVerification.java  ← 브루트포스 방어 필드
domain/auth/repository/EmailVerificationRepository.java  ← 삭제 메서드
global/error/code/AuthErrorCode.java       ← 에러코드 2개 추가
domain/auth/service/AuthService.java       ← 핵심 로직 전체
domain/auth/controller/AuthController.java ← ResponseEntity + 201
domain/auth/dto/request/SignupRequest.java ← 비밀번호 검증
domain/survey/controller/SurveyController.java     ← @AuthenticationPrincipal
domain/response/controller/ResponseController.java ← @AuthenticationPrincipal
domain/archive/controller/ArchiveController.java   ← @AuthenticationPrincipal
domain/user/controller/UserController.java         ← @AuthenticationPrincipal + 정리
domain/user/service/UserService.java               ← 에러코드 + 정리
```

---

## 3. 수정 내용 상세

4단계로 나눠서 진행했습니다. 각 단계마다 빌드 성공을 확인한 뒤 다음으로 넘어갔습니다.

---

### 1단계: 보안 설정 (SecurityConfig + CORS + EntryPoint)

#### [C1] SecurityConfig — 접근제어가 사실상 열려있었음

**문제가 뭐였나:**

```java
// 수정 전
.authorizeHttpRequests(auth -> auth
    .anyRequest().permitAll()
)
```

`anyRequest().permitAll()`이면 **모든 요청을 인증 없이 허용**한다는 뜻입니다. JWT 필터를 만들어놨는데 SecurityConfig에서 전부 열어버리면, JWT가 없어도 모든 API에 접근할 수 있습니다. 설문 생성, 삭제, 내 정보 조회까지 누구나 가능한 상태였습니다.

**어떻게 고쳤나:**

```java
// 수정 후
.authorizeHttpRequests(auth -> auth
    // Swagger, 모니터링
    .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                     "/v3/api-docs/**", "/actuator/**").permitAll()

    // 인증 관련 (비로그인 상태에서 호출하는 것들)
    .requestMatchers("/email-verifications",
                     "/email-verifications/verify",
                     "/signup", "/login", "/token/refresh").permitAll()

    // 설문 조회 (비로그인도 가능)
    .requestMatchers(HttpMethod.GET,
                     "/surveys", "/surveys/{surveyId}",
                     "/surveys/{surveyId}/questions").permitAll()

    // 설문 응답 제출 (비로그인도 가능)
    .requestMatchers(HttpMethod.POST,
                     "/surveys/{surveyId}/responses").permitAll()

    // 나머지는 전부 인증 필수
    .anyRequest().authenticated()
)
```

**핵심:** `anyRequest().authenticated()`로 바꾸고, **게스트도 써야 하는 엔드포인트만** 명시적으로 `permitAll()`로 열었습니다. 이렇게 하면 새 API를 추가해도 기본이 "인증 필수"이기 때문에 실수로 열어놓을 일이 없습니다.

또한 `/logout`은 원래 permitAll에 포함되어 있었는데 제거했습니다. 로그아웃은 **로그인한 사용자만** 할 수 있어야 하니까요.

---

#### [M7] CustomAuthenticationEntryPoint — 401 에러가 HTML로 나오던 문제

**문제가 뭐였나:**

JWT 없이 인증 필수 API에 접근하면, Spring Security 기본 동작은 HTML 에러 페이지를 반환합니다. 프론트엔드 입장에서는 JSON을 기대했는데 HTML이 오면 파싱이 깨집니다.

**어떻게 고쳤나:**

`CustomAuthenticationEntryPoint`를 새로 만들어서, 인증 실패 시 우리 프로젝트의 표준 에러 형식인 `ApiResponse`로 응답하게 했습니다.

```java
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);    // 401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> body = ApiResponse.fail(GlobalErrorCode.UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
```

이렇게 하면 JWT 없이 인증 필수 API를 호출했을 때:

```json
{
  "success": false,
  "code": "COMMON_401",
  "message": "인증이 필요합니다."
}
```

이런 깔끔한 JSON이 돌아옵니다.

SecurityConfig에 연결도 해줬습니다:

```java
.exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
```

---

#### [M8] CORS 설정 — 프론트엔드에서 API 호출이 차단되는 문제

**문제가 뭐였나:**

CORS(Cross-Origin Resource Sharing) 설정이 없으면, 프론트엔드(`localhost:3000`)에서 백엔드(`localhost:8080`)로 API를 호출할 때 **브라우저가 차단**합니다. 도메인이 다르기 때문입니다.

**어떻게 고쳤나:**

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:3000"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

그리고 SecurityFilterChain에 연결:

```java
.cors(cors -> cors.configurationSource(corsConfigurationSource()))
```

나중에 Vercel에 배포하면 `setAllowedOrigins`에 Vercel 도메인도 추가하면 됩니다.

---

### 2단계: 엔티티 / 리포지토리 / 에러코드

#### [C4] EmailVerification — 브루트포스 방어 필드 추가

**문제가 뭐였나:**

인증번호를 틀려도 아무 제한이 없어서, 6자리 숫자(000000~999999)를 전부 시도하면 뚫립니다. 자동화 스크립트로 100만 번 요청하면 끝입니다.

**어떻게 고쳤나:**

EmailVerification 엔티티에 시도 횟수를 추적하는 필드와 메서드를 추가했습니다:

```java
private static final int MAX_ATTEMPTS = 5;

@Column(name = "attempt_count", nullable = false)
private int attemptCount;

// 생성 시 0으로 초기화
public EmailVerification(String email, String code, Purpose purpose, LocalDateTime expiresAt) {
    ...
    this.attemptCount = 0;
}

// 틀릴 때마다 +1
public void incrementAttempt() {
    this.attemptCount++;
}

// 5번 이상 틀렸는지 확인
public boolean isMaxAttemptExceeded() {
    return this.attemptCount >= MAX_ATTEMPTS;
}

// 인증번호를 무효화 (만료시간을 현재로 당김)
public void invalidate() {
    this.expiresAt = LocalDateTime.now();
}
```

5번 틀리면 그 인증번호는 폐기되고, 새로 발급받아야 합니다.

---

#### [M4] verify() 시 만료시간 재설정

**문제가 뭐였나:**

인증번호는 발송 후 10분이 만료시간입니다. 그런데 인증번호를 확인한 뒤 가입까지의 시간 제한이 없었습니다. 인증을 해놓고 3일 뒤에 가입해도 되는 상태였습니다.

**어떻게 고쳤나:**

```java
public void verify() {
    this.verified = true;
    this.expiresAt = LocalDateTime.now().plusMinutes(30);  // 인증 후 30분 이내에 가입해야 함
}
```

인증번호를 확인하면 만료시간을 **지금부터 30분 후**로 재설정합니다. 30분 안에 가입을 완료하지 않으면 인증이 무효가 됩니다.

---

#### [M3] EmailVerificationRepository — 삭제 메서드 추가

**문제가 뭐였나:**

인증번호를 재발송하면 이전 인증 레코드가 DB에 계속 쌓입니다. 가입 완료 후에도 인증 레코드가 남아있습니다. 불필요한 데이터가 무한히 쌓이는 구조였습니다.

**어떻게 고쳤나:**

```java
void deleteAllByEmailAndPurpose(String email, Purpose purpose);
```

이 메서드를 두 군데에서 사용합니다:
1. **재발송 시:** 기존 인증 레코드를 삭제한 뒤 새로 발급
2. **가입 완료 후:** 해당 이메일의 인증 레코드를 전부 정리

---

#### 에러코드 2개 추가

브루트포스 방어와 Rate Limiting에 필요한 에러코드를 추가했습니다:

```java
VERIFICATION_ATTEMPT_EXCEEDED("AUTH_009",
    "인증 시도 횟수를 초과했습니다. 인증번호를 재발송해주세요.",
    HttpStatus.TOO_MANY_REQUESTS),       // 429

EMAIL_RATE_LIMITED("AUTH_010",
    "잠시 후 다시 시도해주세요.",
    HttpStatus.TOO_MANY_REQUESTS),       // 429
```

---

### 3단계: AuthService 핵심 로직

#### [C3] 인증번호 생성 — Random → SecureRandom

**문제가 뭐였나:**

```java
// 수정 전
private final Random random = new Random();
```

`java.util.Random`은 시드값을 알면 다음에 나올 숫자를 예측할 수 있습니다. 보안에 관련된 난수(인증번호, 토큰 등)에는 쓰면 안 됩니다. 실제로 보안 감사에서 바로 지적되는 항목입니다.

**어떻게 고쳤나:**

```java
// 수정 후
private static final SecureRandom SECURE_RANDOM = new SecureRandom();
```

`SecureRandom`은 운영체제의 엔트로피 풀을 사용하므로 예측이 불가능합니다. `static final`로 선언한 이유는 인스턴스를 매번 만들 필요가 없고, `SecureRandom`은 스레드 세이프하기 때문입니다.

---

#### [C5] 인증번호 발송 Rate Limiting

**문제가 뭐였나:**

같은 이메일로 인증번호를 무한 요청할 수 있었습니다. 악의적으로 반복 요청하면:
- 이메일 스팸이 발생하고
- DB에 인증 레코드가 무한히 쌓입니다

**어떻게 고쳤나:**

`sendSignupEmailVerification()` 메서드에 두 가지를 추가했습니다:

```java
// 1. 마지막 발송 후 1분 이내면 거부
emailVerificationRepository
    .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, Purpose.SIGNUP)
    .ifPresent(latest -> {
        if (latest.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(1))) {
            throw new GeneralException(AuthErrorCode.EMAIL_RATE_LIMITED);
        }
    });

// 2. 재발송 시 기존 레코드 삭제 (DB 무한 적재 방지)
emailVerificationRepository.deleteAllByEmailAndPurpose(email, Purpose.SIGNUP);
```

**1분 이내 재요청이면 거부하지만**, 1분이 지나면 재발송을 허용합니다. "인증번호가 안 왔어요" 같은 정상적인 재발송은 1분만 기다리면 됩니다.

---

#### [C4] 인증번호 검증 — 브루트포스 방어 로직

**문제가 뭐였나:**

인증번호를 틀려도 아무 제한이 없어서 무한 시도가 가능했습니다.

**어떻게 고쳤나:**

`verifySignupEmail()` 메서드에 시도 횟수 검증을 추가했습니다:

```java
// 1. 이미 5번 이상 틀렸으면 바로 거부
if (emailVerification.isMaxAttemptExceeded()) {
    throw new GeneralException(AuthErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);
}

// 2. 코드가 틀리면 시도 횟수 증가
if (!emailVerification.getCode().equals(request.code())) {
    emailVerification.incrementAttempt();

    // 5번째 실패면 인증번호 자체를 무효화
    if (emailVerification.isMaxAttemptExceeded()) {
        emailVerification.invalidate();
    }
    throw new GeneralException(AuthErrorCode.VERIFICATION_CODE_MISMATCH);
}
```

흐름을 정리하면:
1. 1~4번째 실패 → "인증번호가 일치하지 않습니다" + 시도 횟수 +1
2. 5번째 실패 → 인증번호 무효화 + "인증번호가 일치하지 않습니다"
3. 6번째 이후 시도 → "인증 시도 횟수를 초과했습니다. 인증번호를 재발송해주세요."
4. 새로 발급받으면 시도 횟수 0부터 다시 시작

---

#### [M3] 가입 완료 후 인증 레코드 정리

**문제가 뭐였나:**

가입이 끝나도 인증 레코드가 DB에 남아있었습니다. 쓸모없는 데이터가 계속 쌓이는 구조였습니다.

**어떻게 고쳤나:**

`signup()` 메서드 마지막에 정리 코드를 추가했습니다:

```java
userRepository.save(user);

// 가입 완료 → 인증 레코드 삭제 (더 이상 필요 없음)
emailVerificationRepository.deleteAllByEmailAndPurpose(request.email(), Purpose.SIGNUP);

return new AuthMessageResponse("회원가입이 완료되었습니다");
```

---

#### [M1] refresh() 트랜잭션 모드 변경

**문제가 뭐였나:**

클래스에 `@Transactional(readOnly = true)`가 걸려있고, `refresh()` 메서드에는 별도 `@Transactional`이 없었습니다. readOnly 트랜잭션에서는 DB 쓰기(UPDATE, DELETE)가 무시될 수 있습니다.

현재 refresh()는 읽기만 하지만, 나중에 토큰 로테이션(기존 리프레시 토큰 삭제 후 새로 발급)을 추가하면 쓰기가 필요합니다. 그때 `readOnly = true` 때문에 DB 반영이 안 되는 문제가 생길 수 있습니다.

**어떻게 고쳤나:**

```java
// 수정 전: 클래스 레벨 @Transactional(readOnly = true)가 적용됨
public TokenRefreshResponse refresh(TokenRefreshRequest request) { ... }

// 수정 후: 쓰기 가능한 트랜잭션으로 명시
@Transactional
public TokenRefreshResponse refresh(TokenRefreshRequest request) { ... }
```

---

#### [M2] login() — flush 추가

**문제가 뭐였나:**

로그인 시 기존 리프레시 토큰을 삭제하고 새로 저장합니다. 그런데 JPA는 SQL 실행 순서를 최적화하기 때문에, **삭제 전에 삽입이 먼저 실행**될 수 있습니다.

만약 리프레시 토큰 테이블에 unique 제약조건이 있으면:
1. JPA가 INSERT를 먼저 실행 → unique 위반으로 에러
2. 그 다음에 DELETE를 실행하려 했지만 이미 에러

**어떻게 고쳤나:**

```java
refreshTokenRepository.deleteAllByUser(user);
refreshTokenRepository.flush();  // DELETE를 여기서 확실히 DB에 반영

RefreshToken savedRefreshToken = new RefreshToken(...);
refreshTokenRepository.save(savedRefreshToken);  // 그 다음에 INSERT
```

`flush()`를 호출하면 JPA가 쌓아둔 SQL을 즉시 DB로 보냅니다. DELETE가 확실히 실행된 후에 INSERT가 되므로 순서가 보장됩니다.

---

#### [M10] System.out.println 제거

**문제가 뭐였나:**

코드에 `System.out.println()`으로 디버깅용 출력이 남아있었습니다. 운영 환경에서는 로그 프레임워크(SLF4J/Logback)를 써야 합니다. `System.out`은 로그 레벨 제어, 파일 출력, 포맷팅이 안 됩니다.

**어떻게 고쳤나:**

해당 `System.out.println()` 호출을 삭제했습니다.

---

### 4단계: 컨트롤러 + DTO + 에러코드

#### [C1] 모든 컨트롤러 — X-User-Id 헤더 → @AuthenticationPrincipal 전환

**문제가 뭐였나:**

```java
// 수정 전
@PostMapping
public ApiResponse<SurveyCreateResponse> create(
        @RequestHeader("X-User-Id") Long userId, ...) { ... }
```

`@RequestHeader("X-User-Id")`는 HTTP 헤더에서 값을 꺼냅니다. 이건 **클라이언트가 보내는 값**입니다. 즉, 누구나 이 헤더에 다른 사람의 userId를 넣으면 그 사람인 척 할 수 있습니다.

예를 들어:
```
POST /surveys
X-User-Id: 42    ← 내가 42번 유저인 척
```

이러면 42번 유저의 권한으로 설문을 만들거나 삭제할 수 있습니다. **사용자 위장이 가능한 심각한 보안 취약점**입니다.

**어떻게 고쳤나:**

```java
// 수정 후
@PostMapping
public ResponseEntity<ApiResponse<SurveyCreateResponse>> create(
        @AuthenticationPrincipal Long userId, ...) { ... }
```

`@AuthenticationPrincipal`은 **JWT 토큰에서 추출한 userId**를 주입합니다. 이 값은 서버가 검증한 것이므로 위조할 수 없습니다.

JWT 토큰은 서버의 비밀키로 서명되어 있기 때문에:
- 토큰을 조작하면 서명 검증에서 실패 → 401 에러
- 다른 사람의 토큰을 쓰려면 그 사람의 토큰을 알아야 함

**이 흐름이 어떻게 동작하는지:**

```
1. 클라이언트가 로그인 → 서버가 JWT 발급 (userId가 안에 들어있음)
2. 클라이언트가 API 호출 시 Authorization 헤더에 JWT를 담아서 보냄
3. JwtAuthenticationFilter가 JWT를 검증하고, userId를 SecurityContext에 저장
4. 컨트롤러의 @AuthenticationPrincipal이 SecurityContext에서 userId를 꺼냄
```

변경한 컨트롤러 목록:

| 컨트롤러 | 변경한 메서드 |
|----------|-------------|
| SurveyController | `getList`, `create`, `delete` |
| ResponseController | `submit` |
| ArchiveController | `getViewInfo`, `purchaseView`, `getResults`, `getMySurveys`, `share` |
| UserController | `getMyInfo` |

UserController는 추가로 `Authentication authentication` → `@AuthenticationPrincipal Long userId`로 바꿨습니다. 원래는 `Authentication` 객체를 받아서 직접 `(Long) authentication.getPrincipal()`로 캐스팅하고 있었는데, `@AuthenticationPrincipal`이 이걸 자동으로 해줍니다.

---

#### [M9] SignupRequest — 비밀번호 최소 길이 검증

**문제가 뭐였나:**

```java
// 수정 전
@NotBlank(message = "비밀번호를 입력해주세요.")
String password,
```

`@NotBlank`만 있으면 **"1"** 한 글자로도 가입이 됩니다. 비밀번호 강도 검증이 전혀 없는 상태였습니다.

**어떻게 고쳤나:**

```java
// 수정 후
@NotBlank(message = "비밀번호를 입력해주세요.")
@Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
String password,
```

8자 이상은 보안의 기본 중의 기본입니다. OWASP(웹 보안 표준 기관)에서도 최소 8자를 권장합니다.

---

#### [m5] UserService — 에러코드 의미 불일치

**문제가 뭐였나:**

```java
// 수정 전
User user = userRepository.findById(userId)
        .orElseThrow(() -> new GeneralException(GlobalErrorCode.INVALID_INPUT_VALUE));
```

`INVALID_INPUT_VALUE`는 HTTP 400(Bad Request)입니다. 그런데 이 상황은 "userId에 해당하는 유저가 DB에 없음"이지, "입력값이 잘못됨"이 아닙니다. 클라이언트가 보낸 값이 잘못된 게 아니라 리소스가 없는 겁니다.

**어떻게 고쳤나:**

```java
// 수정 후
User user = userRepository.findById(userId)
        .orElseThrow(() -> new GeneralException(GlobalErrorCode.NOT_FOUND));
```

`NOT_FOUND`는 HTTP 404입니다. "대상을 찾을 수 없습니다"라는 메시지와 함께 404가 반환됩니다. 의미에 맞는 상태코드입니다.

---

#### [m6] AuthController — 회원가입 201 응답 + ResponseEntity 적용

**문제가 뭐였나:**

```java
// 수정 전
@PostMapping("/signup")
public ApiResponse<AuthMessageResponse> signup(...) {
    return ApiResponse.success(authService.signup(request));
}
```

두 가지 문제가 있었습니다:
1. `ApiResponse`를 직접 반환하면 HTTP 상태코드가 항상 200입니다
2. 회원가입은 새 리소스(User)를 생성하는 것이므로 201 Created가 맞습니다

**어떻게 고쳤나:**

```java
// 수정 후
@PostMapping("/signup")
public ResponseEntity<ApiResponse<AuthMessageResponse>> signup(...) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(authService.signup(request)));
}
```

AuthController의 모든 메서드에 `ResponseEntity`를 적용해서 다른 컨트롤러와 패턴을 통일했고, 회원가입만 201, 나머지는 200으로 설정했습니다.

---

#### [m2~m4] 중복 import 정리

**문제가 뭐였나:**

여러 파일에서 같은 클래스가 두 번 import되어 있었습니다:

```java
// UserController.java — UserService, RequiredArgsConstructor 중복
import com.example.loopa.domain.user.service.UserService;
...
import com.example.loopa.domain.user.service.UserService;  // 중복

// UserService.java — UserRepository 중복
import com.example.loopa.domain.user.repository.UserRepository;
...
import com.example.loopa.domain.user.repository.UserRepository;  // 중복

// AuthController.java — import 정리 안 됨
```

**어떻게 고쳤나:**

중복 import를 제거하고, AuthController는 와일드카드 import로 깔끔하게 정리했습니다.

---

## 4. 수정 후 전체 구조

인증 흐름을 처음부터 끝까지 정리하면 이렇습니다:

### 회원가입 흐름

```
1. POST /email-verifications          ← 인증번호 발송
   - 이미 가입된 이메일이면 거부 (AUTH_001)
   - 1분 이내 재요청이면 거부 (AUTH_010)
   - 기존 인증 레코드 삭제 후 새로 발급

2. POST /email-verifications/verify   ← 인증번호 확인
   - 만료되었으면 거부 (AUTH_003)
   - 5번 이상 틀렸으면 거부 (AUTH_009)
   - 코드 불일치 시 시도 횟수 +1 (AUTH_002)
   - 5번째 실패 시 인증번호 무효화
   - 성공 시 verified = true, 만료시간 30분 연장

3. POST /signup                       ← 회원가입 (201 Created)
   - 이메일 중복 확인
   - 인증 완료 + 만료 전인지 확인
   - 비밀번호 8자 이상 검증
   - User 저장 후 인증 레코드 삭제
```

### 로그인/로그아웃 흐름

```
4. POST /login                        ← 로그인
   - 이메일/비밀번호 확인
   - 기존 리프레시 토큰 삭제 + flush
   - 새 액세스 토큰 + 리프레시 토큰 발급

5. POST /token/refresh                ← 토큰 재발급
   - 리프레시 토큰 유효성 검증
   - DB에 저장된 토큰인지 확인
   - 만료 여부 확인
   - 새 액세스 토큰 발급

6. POST /logout                       ← 로그아웃 (인증 필수)
   - 리프레시 토큰 유효성 검증
   - DB에서 리프레시 토큰 삭제
```

### 인증이 필요한 API 호출 시

```
요청 → JwtAuthenticationFilter
        ├─ JWT 있음 + 유효 → SecurityContext에 userId 저장 → 컨트롤러
        ├─ JWT 없음 + permitAll 엔드포인트 → 컨트롤러 (userId = null)
        └─ JWT 없음 + authenticated 엔드포인트 → CustomAuthenticationEntryPoint → 401 JSON
```

---

## 5. 에러코드 전체 목록

| 코드 | 이름 | 메시지 | HTTP |
|------|------|--------|------|
| AUTH_001 | ALREADY_EXISTS | 이미 사용 중인 이메일입니다. | 409 |
| AUTH_002 | VERIFICATION_CODE_MISMATCH | 인증번호가 일치하지 않습니다. | 400 |
| AUTH_003 | VERIFICATION_CODE_EXPIRED | 인증번호가 만료되었습니다. | 400 |
| AUTH_004 | EMAIL_NOT_VERIFIED | 이메일 인증이 완료되지 않았습니다. | 400 |
| AUTH_005 | LOGIN_FAILED | 이메일 또는 비밀번호가 일치하지 않습니다. | 401 |
| AUTH_006 | INVALID_REFRESH_TOKEN | 유효하지 않은 리프레시 토큰입니다. | 401 |
| AUTH_007 | EMAIL_NOT_REGISTERED | 등록되지 않은 이메일입니다. | 404 |
| AUTH_008 | EXPIRED_REFRESH_TOKEN | 만료된 리프레시 토큰입니다. | 401 |
| AUTH_009 | VERIFICATION_ATTEMPT_EXCEEDED | 인증 시도 횟수를 초과했습니다. 인증번호를 재발송해주세요. | 429 |
| AUTH_010 | EMAIL_RATE_LIMITED | 잠시 후 다시 시도해주세요. | 429 |

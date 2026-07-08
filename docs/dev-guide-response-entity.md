# ResponseEntity + ApiResponse 개발 기록

---

## 1. 배경: 원래 어떻게 되어 있었는가?

원래 컨트롤러는 `ApiResponse<T>`를 바로 반환하고 있었습니다.

```java
@PostMapping
public ApiResponse<SurveyCreateResponse> create(...) {
    return ApiResponse.success(surveyService.create(userId, request));
}
```

이렇게 하면 Spring이 반환값을 JSON으로 직렬화해서 보내줍니다. **문제는 HTTP 상태코드가 무조건 200으로 고정된다**는 점입니다.

설문을 "생성"했는데 200이 오고, 설문을 "조회"했는데도 200이 오면, 클라이언트 입장에서는 이게 새로 만들어진 건지 기존 걸 가져온 건지 HTTP 레벨에서 구분이 안 됩니다.

---

## 2. 왜 바꿨는가?

REST 관례에서는 HTTP 상태코드로 의미를 구분합니다.

| 상황 | 상태코드 | 의미 |
|------|---------|------|
| 조회 성공 | **200 OK** | 요청한 리소스를 정상 반환 |
| 리소스 생성 | **201 Created** | 새 리소스가 만들어짐 |
| 4xx | 400, 403, 404, 409 | 클라이언트 측 문제 |
| 5xx | 500 | 서버 측 문제 |

기존 코드에서 에러 응답은 `GlobalExceptionHandler`가 `ResponseEntity`로 감싸서 적절한 4xx/5xx를 반환하고 있었습니다. 그런데 **성공 응답만 항상 200**이었던 거죠.

```
성공 흐름 (기존):  Controller → ApiResponse → 무조건 200
에러 흐름 (기존):  Exception → GlobalExceptionHandler → ResponseEntity → 적절한 4xx/5xx
```

성공 흐름도 `ResponseEntity`로 감싸면 상태코드를 직접 지정할 수 있습니다.

---

## 3. 어떻게 바꿨는가?

### 핵심: 컨트롤러 반환 타입만 변경

`ApiResponse<T>` → `ResponseEntity<ApiResponse<T>>`

```java
// 200 응답 (조회, 삭제 등)
@GetMapping("/{surveyId}")
public ResponseEntity<ApiResponse<SurveyDetailResponse>> getDetail(...) {
    return ResponseEntity.ok(ApiResponse.success(...));
}

// 201 응답 (생성)
@PostMapping
public ResponseEntity<ApiResponse<SurveyCreateResponse>> create(...) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(...));
}
```

### 달라진 점과 안 달라진 점

| 항목 | 달라짐? | 설명 |
|------|---------|------|
| HTTP 상태코드 | O | 생성 API는 201, 나머지는 200 |
| body의 JSON 구조 | X | `isSuccess`, `code`, `message`, `result` 그대로 |
| body의 `code` 값 | X | 성공 시 `COMMON_200` 유지 |
| Service 코드 | X | Service는 DTO만 반환, ResponseEntity를 모름 |
| 에러 처리 | X | GlobalExceptionHandler가 그대로 처리 |

---

## 4. 우리 프로젝트의 규칙

### 201을 쓰는 엔드포인트 (5개)

새 리소스를 만드는 POST 요청입니다.

| 엔드포인트 | 설명 |
|-----------|------|
| `POST /auth/signup` | 회원가입 |
| `POST /surveys` | 설문 생성 |
| `POST /surveys/{id}/responses` | 응답 제출 |
| `POST /archive/surveys/{id}/views` | 열람 구매 |
| `POST /archive/shares` | 공유 |

### 200을 쓰는 엔드포인트 (나머지 전부)

GET(조회), DELETE(삭제), 그 외 POST 중 리소스 생성이 아닌 것(로그인 등).

### 컨트롤러 작성 패턴

```java
// 200 패턴
return ResponseEntity.ok(ApiResponse.success(결과));

// 201 패턴
return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(결과));
```

---

## 5. 자주 할 수 있는 실수

### (1) Service에서 ResponseEntity를 반환하면 안 됨

```java
// 나쁜 예 — Service가 HTTP 계층을 알게 됨
public ResponseEntity<ApiResponse<T>> create(...) {
    return ResponseEntity.status(HttpStatus.CREATED).body(...);
}
```

Service는 비즈니스 로직만 담당합니다. HTTP 상태코드는 컨트롤러의 책임입니다. Service는 DTO만 반환하고, 컨트롤러에서 `ResponseEntity`로 감싸세요.

### (2) 에러를 컨트롤러에서 직접 처리하면 안 됨

```java
// 나쁜 예 — 에러를 컨트롤러에서 잡아서 4xx로 반환
@PostMapping
public ResponseEntity<ApiResponse<T>> create(...) {
    try {
        ...
    } catch (GeneralException e) {
        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(ApiResponse.fail(e.getErrorCode()));
    }
}
```

에러는 `GlobalExceptionHandler`가 일괄 처리합니다. 컨트롤러에서는 예외를 던지기만 하면 됩니다.

### (3) body의 code와 HTTP 상태코드를 혼동하면 안 됨

```
HTTP 상태코드 201  ←  "HTTP 레벨에서 리소스가 생성되었다"
body의 code "COMMON_200"  ←  "우리 앱에서 성공했다"
```

이 두 가지는 별개입니다. body의 `COMMON_200`은 앱 레벨 성공 코드이고, HTTP 201은 프로토콜 레벨 상태코드입니다. 성공 시 body의 code는 항상 `COMMON_200`입니다.

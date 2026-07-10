# 인프라 아키텍처 가이드

---

## 1. 전체 구조 한눈에 보기

```
[사용자 브라우저]
       ↓
[Vercel - 프론트엔드]
       ↓  API 호출
[AWS Cloud]
  ├─ VPC
  │   ├─ Public Subnet
  │   │   └─ EC2
  │   │       ├─ Nginx (리버스 프록시)
  │   │       └─ Spring Boot (API 서버)
  │   │
  │   └─ Private Subnet
  │       └─ RDS MySQL (데이터베이스)
```

요청 흐름을 한 줄로 요약하면:

```
사용자 → Vercel → Nginx(:80) → Spring Boot(:8080) → RDS MySQL
```

---

## 2. 각 구성 요소 설명

### 2-1. Vercel (프론트엔드 호스팅)

**그게 뭔가?**

Vercel은 프론트엔드 전용 호스팅 플랫폼이다. Next.js 같은 프론트엔드 프레임워크를 배포하면, 전 세계 CDN에 자동으로 배포해준다. GitHub에 push하면 자동 배포되고, 도메인 연결도 클릭 몇 번이면 끝난다.

**실무에서 무슨 역할?**

- 프론트엔드 정적 파일(HTML, CSS, JS) 서빙
- CDN으로 전 세계 사용자에게 빠른 응답
- 서버리스 함수(SSR) 지원
- GitHub 연동 자동 배포 (Preview URL 포함)

**만약 Vercel 없이 프론트도 EC2에 올렸다면?**

- Nginx에서 정적 파일 서빙 + API 프록시를 동시에 설정해야 한다
- CDN이 없으니 해외 사용자에게 느리다
- 프론트 배포할 때마다 EC2에 SSH 접속해서 수동으로 빌드해야 한다
- 프론트엔드 트래픽이 백엔드 서버 자원을 잡아먹는다 (프론트 트래픽 폭증 → API 서버도 느려짐)

**프론트/백 분리의 핵심 이유:**

프론트엔드와 백엔드는 배포 주기, 스케일링 단위, 기술 스택이 전부 다르다. 분리하면 프론트는 프론트대로, 백은 백대로 독립적으로 배포하고 확장할 수 있다.

---

### 2-2. VPC (Virtual Private Cloud)

**그게 뭔가?**

VPC는 AWS 안에 만드는 "나만의 가상 네트워크"다. 실제 사무실에 내부 네트워크(LAN)를 구축하는 것처럼, AWS 클라우드 안에 논리적으로 격리된 네트워크 공간을 만드는 것이다.

**실무에서 무슨 역할?**

- 내 리소스(EC2, RDS 등)를 외부 인터넷이나 다른 AWS 계정에서 격리
- 서브넷, 라우팅, 보안 그룹 등으로 네트워크 레벨 접근 제어
- 어떤 리소스가 인터넷에 노출되고, 어떤 리소스는 내부에서만 접근 가능한지 결정

**만약 VPC가 없다면?**

- 모든 리소스가 같은 네트워크에 있게 된다
- DB를 인터넷에서 직접 접근할 수 있는 위험 (누구나 DB 포트에 접속 시도 가능)
- 다른 AWS 사용자의 리소스와 네트워크가 섞일 수 있다
- "이 서버는 외부 접근 허용, 이 DB는 내부만" 같은 구분이 불가능

---

### 2-3. Public Subnet vs Private Subnet

**그게 뭔가?**

서브넷은 VPC 안을 다시 쪼갠 작은 네트워크 구역이다.

- **Public Subnet**: 인터넷 게이트웨이(IGW)와 연결되어 **외부에서 접근 가능**한 구역
- **Private Subnet**: 인터넷 게이트웨이와 연결되지 않아 **외부에서 접근 불가능**한 구역

Loopa에서는:
- **Public Subnet** → EC2 (API 서버) — 사용자 요청을 받아야 하니까
- **Private Subnet** → RDS (데이터베이스) — 외부 접근이 필요 없으니까

**실무에서 무슨 역할?**

네트워크 레벨에서 보안 경계를 나누는 것이다. 집으로 비유하면:
- Public Subnet = 현관 (방문자가 올 수 있는 곳)
- Private Subnet = 금고방 (집 안에서만 접근 가능)

**만약 서브넷을 나누지 않고 전부 Public에 뒀다면?**

- RDS가 인터넷에 노출된다. Security Group으로 막더라도 네트워크 레벨에서는 접근 경로가 열려 있다
- Security Group 설정 실수 하나로 DB가 전 세계에 공개될 수 있다
- 보안 감사에서 "DB가 퍼블릭 서브넷에 있다"는 것만으로 지적 사항이 된다

**서브넷 분리 = 실수해도 DB가 인터넷에 노출되지 않는 구조적 안전장치**

---

### 2-4. EC2 (Elastic Compute Cloud)

**그게 뭔가?**

EC2는 AWS에서 제공하는 가상 서버(컴퓨터)다. 리눅스 서버 한 대를 빌려서, 그 위에 원하는 소프트웨어를 설치하고 실행할 수 있다.

**실무에서 무슨 역할?**

- API 서버(Spring Boot), 웹 서버(Nginx) 등 애플리케이션 실행 환경
- SSH로 접속해서 직접 관리 가능
- 필요에 따라 사양(CPU, 메모리) 조절 가능

**Loopa에서의 EC2:**

EC2 안에서 Docker Compose로 두 개의 컨테이너를 실행한다:

```
EC2 인스턴스
├─ Nginx 컨테이너 (nginx:alpine)     ← :80 외부 노출
├─ Spring Boot 컨테이너 (loopa-api)  ← :8080 내부만
└─ Docker bridge network (app-network)
```

**만약 EC2 대신 로컬 PC에서 서버를 돌렸다면?**

- PC 꺼지면 서비스 중단
- 가정집 IP는 유동 IP라 주소가 계속 바뀐다
- 보안 업데이트, 방화벽 관리를 직접 해야 한다
- 트래픽 증가 시 사양 업그레이드가 불가능 (물리적으로 부품 교체 필요)

---

### 2-5. Nginx (리버스 프록시)

**그게 뭔가?**

Nginx는 웹 서버이자 리버스 프록시다. 리버스 프록시란, 클라이언트의 요청을 받아서 뒤에 있는 실제 서버(Spring Boot)로 전달해주는 중간 역할이다.

```
클라이언트 → Nginx(:80) → Spring Boot(:8080)
              중간 전달자      실제 처리
```

**현재 Nginx 설정:**

```nginx
server {
    listen 80;

    location / {
        proxy_pass http://loopa-api:8080;       # Spring Boot로 전달
        proxy_set_header Host $host;             # 원래 호스트 정보 유지
        proxy_set_header X-Real-IP $remote_addr; # 실제 클라이언트 IP 전달
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

**실무에서 무슨 역할?**

- **리버스 프록시**: 클라이언트가 Spring Boot 포트(8080)를 직접 알 필요 없이, 80포트 하나로 접근
- **SSL Termination**: HTTPS 인증서 처리를 Nginx가 담당 (향후 적용 예정)
- **정적 파일 서빙**: 이미지, CSS 등을 Spring Boot 거치지 않고 직접 전달
- **로드 밸런싱**: Spring Boot 서버를 여러 대로 늘릴 때, 요청을 분산
- **보안**: Spring Boot를 외부에 직접 노출하지 않음

**만약 Nginx 없이 Spring Boot를 직접 노출했다면?**

- Spring Boot가 80포트를 직접 점유해야 한다 (리눅스에서 1024 이하 포트는 root 권한 필요)
- HTTPS 설정을 Spring Boot 안에서 해야 한다 (인증서 갱신할 때마다 앱 재시작)
- Spring Boot 서버를 2대로 늘리고 싶으면 앞에 로드밸런서를 새로 구성해야 한다
- DDoS 등 악의적 요청이 애플리케이션 레이어까지 직접 도달한다

**Nginx = Spring Boot 앞에서 방패 역할을 하는 문지기**

---

### 2-6. Spring Boot (API 서버)

**그게 뭔가?**

Spring Boot는 Java 기반 웹 애플리케이션 프레임워크다. Loopa의 모든 비즈니스 로직(설문 생성, 응답 제출, 토큰 관리 등)이 여기서 실행된다.

**현재 Docker 빌드 방식 (Multi-stage Build):**

```dockerfile
# 1단계: 빌드 (JDK 필요)
FROM eclipse-temurin:17-jdk-alpine AS builder
COPY . .
RUN ./gradlew build -x test

# 2단계: 실행 (JRE만 있으면 됨)
FROM eclipse-temurin:17-jre-alpine
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**왜 Multi-stage 빌드인가?**

- 1단계(builder): 빌드에는 JDK + Gradle + 소스코드가 필요하다 → 이미지가 크다
- 2단계(실행): 실행에는 JRE + jar 파일만 있으면 된다 → 이미지가 작다
- 최종 이미지에는 소스코드, JDK, Gradle이 포함되지 않는다

**만약 단일 스테이지로 빌드했다면?**

- 최종 이미지에 JDK, Gradle, 소스코드가 전부 포함된다
- 이미지 크기: ~150MB (JRE) vs ~400MB+ (JDK + 소스)
- 배포할 때마다 불필요한 데이터를 ECR에 push하고 EC2에서 pull → 느려진다
- 소스코드가 이미지에 포함 → 보안 위험

---

### 2-7. RDS MySQL (관계형 데이터베이스)

**그게 뭔가?**

RDS(Relational Database Service)는 AWS가 관리해주는 데이터베이스 서비스다. MySQL 서버를 직접 설치하고 관리할 필요 없이, AWS가 설치, 패치, 백업, 장애 복구를 대신 해준다.

**실무에서 무슨 역할?**

- 데이터 영구 저장 (사용자 정보, 설문, 응답, 토큰 거래 등)
- 자동 백업 (매일 스냅샷)
- 자동 패치 (보안 업데이트)
- 모니터링 (CPU, 메모리, 커넥션 수 등)

**만약 RDS 대신 EC2에 MySQL을 직접 설치했다면?**

- MySQL 설치, 버전 업그레이드, 보안 패치를 직접 해야 한다
- 백업 스크립트를 직접 작성하고, 복원 테스트도 직접 해야 한다
- EC2가 죽으면 DB도 같이 죽는다 (데이터 유실 위험)
- EC2 사양을 올리려면 DB도 같이 내렸다 올려야 한다 (서비스 중단)
- 디스크 용량 관리도 직접 해야 한다 (꽉 차면 서비스 장애)

**RDS = "DB 관리는 AWS한테 맡기고, 우리는 애플리케이션 개발에 집중"**

---

### 2-8. ECR (Elastic Container Registry)

**그게 뭔가?**

ECR은 Docker 이미지 저장소다. Docker Hub의 AWS 버전이라고 보면 된다. 빌드된 Docker 이미지를 push하면 저장해두고, 배포할 때 pull해서 사용한다.

```
GitHub Actions에서 빌드 → ECR에 push → EC2에서 pull → 실행
```

**실무에서 무슨 역할?**

- Docker 이미지 버전 관리 (태그별 저장)
- AWS IAM과 연동된 접근 제어 (아무나 pull 불가)
- EC2와 같은 AWS 네트워크라 pull 속도가 빠르다

**만약 ECR 대신 Docker Hub를 썼다면?**

- 무료 플랜은 private 레포 제한이 있다
- AWS 외부 네트워크를 경유하므로 pull 속도가 느리다
- AWS IAM 대신 별도의 Docker Hub 인증 관리가 필요하다
- 기능상 큰 차이는 없지만, AWS 생태계 안에서 ECR이 가장 자연스럽다

---

### 2-9. Docker Compose (컨테이너 오케스트레이션)

**그게 뭔가?**

Docker Compose는 여러 개의 Docker 컨테이너를 한 번에 정의하고 실행하는 도구다. `docker-compose.yml` 파일 하나에 어떤 컨테이너를 어떻게 실행할지 적어두면, `docker compose up -d` 한 줄로 전부 실행된다.

**현재 Loopa의 docker-compose.yml:**

```yaml
services:
  loopa-api:                    # Spring Boot 컨테이너
    image: {ECR주소}/loopa-api:latest
    expose:
      - "8080"                  # 외부 노출 X, Nginx만 접근
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: ${DB_URL}         # .env 파일에서 주입
      DB_USERNAME: ${DB_USERNAME}
      DB_PASSWORD: ${DB_PASSWORD}

  nginx:                        # Nginx 컨테이너
    image: nginx:alpine
    ports:
      - "80:80"                 # 외부 노출 O
    depends_on:
      - loopa-api               # Spring Boot가 먼저 올라가야 함
```

**`expose` vs `ports` 차이:**

| 설정 | 의미 | 외부 접근 |
|------|------|----------|
| `expose: "8080"` | 컨테이너 간 통신만 허용 | 불가 |
| `ports: "80:80"` | 호스트(EC2)의 80포트에 매핑 | 가능 |

Spring Boot는 `expose`만 했으므로 외부에서 8080포트로 직접 접근할 수 없다. 오직 같은 `app-network` 안에 있는 Nginx만 접근 가능하다.

**만약 Docker Compose 없이 직접 실행했다면?**

- `docker run` 명령어를 컨테이너마다 따로 실행해야 한다
- 네트워크 연결, 환경변수 주입, 실행 순서를 매번 수동으로 관리
- 배포 스크립트가 복잡해지고, 실수 가능성이 높아진다
- "이 서버에서 뭐가 돌아가고 있지?" 확인하려면 `docker ps`로 일일이 봐야 한다

---

### 2-10. 도메인 (가비아, 예정)

**그게 뭔가?**

도메인은 사람이 읽을 수 있는 서버 주소다. IP 주소(예: 13.124.xx.xx) 대신 `loopa.kr` 같은 이름으로 접속할 수 있게 해준다.

**연결 구조 (예정):**

```
loopa.kr → 가비아 DNS → EC2 Public IP
```

가비아에서 도메인을 구매한 뒤, DNS A 레코드에 EC2의 퍼블릭 IP(또는 Elastic IP)를 등록하면 된다.

**만약 도메인이 없다면?**

- 사용자가 `http://13.124.xx.xx`로 접속해야 한다 (외우기 어렵고 신뢰도 낮음)
- HTTPS 인증서 발급이 어렵다 (Let's Encrypt 등은 도메인 기반)
- IP가 바뀌면 프론트엔드 API 주소도 전부 바꿔야 한다

---

## 3. 네트워크 보안 흐름

외부에서 내부로 접근할 때 거치는 보안 계층:

```
[인터넷]
   ↓
[Security Group - EC2용]  ← 80포트만 허용
   ↓
[Nginx]                   ← 80 → 8080 프록시
   ↓
[Spring Boot]             ← 비즈니스 로직 + 인증/인가
   ↓
[Security Group - RDS용]  ← EC2의 Security Group에서만 3306 허용
   ↓
[RDS MySQL]
```

**Security Group이란?**

AWS의 가상 방화벽이다. "어떤 IP에서, 어떤 포트로 접근할 수 있는지"를 규칙으로 정의한다.

| 대상 | 인바운드 규칙 | 의미 |
|------|-------------|------|
| EC2 | 80포트 - 0.0.0.0/0 | 모든 곳에서 80포트 접근 허용 |
| RDS | 3306포트 - EC2 SG만 | EC2에서만 DB 접근 허용 |

**만약 Security Group을 설정하지 않았다면?**

- 모든 포트가 열린 상태 → 8080, 22(SSH) 등 전부 외부 노출
- DB 포트(3306)에 전 세계에서 접속 시도 가능 (무차별 대입 공격)
- 해킹 시도의 첫 단계가 열린 포트 스캔이다 — 열려 있는 포트가 많을수록 공격 표면이 넓어진다

---

## 4. 환경변수 관리

DB 접속 정보 같은 민감한 값은 코드에 넣지 않고, 환경변수로 주입한다.

**흐름:**

```
GitHub Secrets (원본)
       ↓  CD 파이프라인에서
EC2의 .env 파일에 기록
       ↓  docker-compose가 읽어서
Spring Boot 컨테이너에 주입
       ↓  application.yml에서
${DB_URL} 등으로 참조
```

**주입되는 환경변수:**

| 변수 | 용도 |
|------|------|
| `DB_URL` | RDS 접속 주소 (jdbc:mysql://...) |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `SPRING_PROFILES_ACTIVE` | 프로필 (prod) |

**만약 환경변수 없이 코드에 직접 넣었다면?**

- DB 비밀번호가 GitHub에 올라간다 → 보안 사고
- 로컬/개발/운영 환경마다 코드를 수정해야 한다
- 비밀번호 변경 시 코드 수정 + 재빌드 + 재배포가 필요하다

**`.env` 파일은 `.gitignore`에 포함되어 있어서 GitHub에 절대 올라가지 않는다.**

---

## 5. 왜 이 구조인가? (정리)

| 선택 | 이유 |
|------|------|
| 프론트/백 분리 (Vercel + EC2) | 배포 주기, 스케일링, 기술 스택이 다르다 |
| VPC + 서브넷 분리 | DB를 네트워크 레벨에서 격리 (실수해도 안전) |
| Nginx 리버스 프록시 | Spring Boot 직접 노출 방지, 향후 HTTPS/로드밸런싱 확장 |
| Docker Compose | 멀티 컨테이너를 선언적으로 관리, 배포 자동화 |
| Multi-stage 빌드 | 이미지 크기 절감, 소스코드 미포함 |
| RDS (관리형 DB) | 백업/패치/복구를 AWS에 위임 |
| ECR | AWS 생태계 내 이미지 저장소, IAM 연동 |
| 환경변수 주입 | 민감 정보를 코드에서 분리 |

---

## 6. 향후 개선 가능한 부분

현재 구조에서 추가하면 좋을 것들 (당장 필요한 건 아니지만, 서비스가 커지면 고려):

| 개선 | 설명 |
|------|------|
| **HTTPS** | Nginx에 Let's Encrypt 인증서 적용 (certbot). 도메인 연결 후 적용 예정 |
| **Elastic IP** | EC2 재시작 시 IP 변경 방지. 도메인 연결 전 필수 |
| **Health Check** | Nginx에서 Spring Boot 상태 확인 후 요청 전달 |
| **로깅/모니터링** | Actuator + Prometheus 메트릭 수집 기반은 구축됨. Grafana 대시보드 및 알림 설정은 향후 적용 |
| **Auto Scaling** | 트래픽 증가 시 EC2 자동 증설 (현재는 단일 인스턴스) |

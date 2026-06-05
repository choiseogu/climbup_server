# CLAUDE.md — ClimbUp Backend (Spring Boot + MySQL)

자연 암벽 등반의 상대 고도 세션을 저장·조회·집계하는 **REST API 서버**.
프론트엔드(React Native + Expo, Android)는 별도 레포이며, 이 서버의 **클라이언트**다.
이 레포는 **백엔드만** 다룬다.

---

## 절대 원칙 (어기지 말 것)

1. **서버는 저장·조회·집계만**: 최고 고도·누적 상승·최저 속도 구간은 앱이 계산한 결과로 들어온다. 서버는 원시 샘플로 재계산하지 않고, 받은 결과 필드를 그대로 저장한다. (집계 쿼리만 서버 책임)
2. **멱등 업로드**: 세션 저장은 `(user_id, client_uuid)` 기준 멱등. 오프라인 재전송이 중복 레코드를 만들면 안 된다.
3. **시크릿은 서버에만**: 카카오 `client_secret`, 토큰 교환, `JWT_SECRET`은 전부 백엔드. 앱은 `code`만 보낸다.
4. **소유권 검증 필수**: 모든 세션 조회/삭제는 JWT의 `userId`와 리소스 `user_id` 일치 확인. 불일치 → 403.
5. **비밀번호 평문 금지**: BCrypt 해싱만. `password_hash`는 BCrypt 결과(60자).
6. **MVP 범위 사수**: 소셜 / TourAPI / 그레이드DB / 리프레시 토큰 / 계정 통합은 Out-of-Scope. 요청 없이 추가하지 않는다.

---

## 기술 스택 (이 의존성만 사용)

- **웹** — Spring Web (REST)
- **보안** — Spring Security + JWT (`jjwt` 또는 `java-jwt`)
- **영속성** — Spring Data JPA + MySQL Connector/J
- **검증** — Spring Validation (Jakarta Bean Validation)
- **비밀번호** — Spring Security `BCryptPasswordEncoder`
- **외부 HTTP** — `RestClient` / `WebClient` (카카오 토큰 교환·사용자 조회)

### 환경 변수 / 시크릿 (리포지토리 커밋 금지)

```
KAKAO_CLIENT_ID
KAKAO_CLIENT_SECRET        # 앱에 절대 포함 안 함
KAKAO_REDIRECT_URI
JWT_SECRET                 # 충분히 긴 랜덤값
JWT_ACCESS_EXP_MINUTES
DB_URL / DB_USERNAME / DB_PASSWORD
```

---

## 패키지 구조 (제안)

```
com.climbup
├─ config/      # SecurityConfig, CORS, RestClient, 전역 예외 핸들러
├─ auth/        # 회원가입/로그인/카카오, JWT 발급, JwtAuthenticationFilter
├─ user/        # User 엔티티/리포지토리, /users/me
├─ session/     # ClimbSession·AltitudeSample 엔티티, CRUD, 멱등 저장
├─ stats/       # 일일 집계 쿼리
└─ common/      # 에러 envelope, 공통 응답, 예외 타입
```

- 컨트롤러는 얇게(검증·위임), 비즈니스 로직은 서비스에.
- DTO ↔ 엔티티 변환은 서비스/매퍼에서. 엔티티를 응답에 그대로 노출하지 않는다(특히 `password_hash`).

---

## 데이터 모델 (MySQL DDL)

```sql
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NULL,
    password_hash VARCHAR(60)  NULL,            -- BCrypt 60자, 소셜 전용이면 NULL
    nickname      VARCHAR(50)  NOT NULL,
    provider      ENUM('LOCAL','KAKAO') NOT NULL,
    kakao_id      VARCHAR(50)  NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_kakao_id (kakao_id)
);

CREATE TABLE climb_sessions (
    id                     BIGINT   NOT NULL AUTO_INCREMENT,
    user_id                BIGINT   NOT NULL,
    client_uuid            CHAR(36) NOT NULL,   -- 멱등성 키(앱 생성)
    started_at             DATETIME NOT NULL,
    ended_at               DATETIME NOT NULL,
    duration_sec           INT      NOT NULL,
    max_altitude_m         FLOAT    NOT NULL,
    cumulative_gain_m      FLOAT    NOT NULL,
    start_lat              DOUBLE   NULL,
    start_lng              DOUBLE   NULL,
    region_name            VARCHAR(255) NULL,
    slowest_band_from_m    FLOAT    NULL,        -- 산출 불가 시 NULL (셋 다)
    slowest_band_to_m      FLOAT    NULL,
    slowest_band_speed_mps FLOAT    NULL,
    created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_sessions_client_uuid (user_id, client_uuid),  -- 중복 업로드 방지
    KEY idx_sessions_user_started (user_id, started_at),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE altitude_samples (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    session_id   BIGINT NOT NULL,
    t_offset_sec INT    NOT NULL,    -- 세션 시작 기준 경과 초
    altitude_m   FLOAT  NOT NULL,    -- 스무딩된 상대 고도 (앱에서 다운샘플된 값)
    PRIMARY KEY (id),
    KEY idx_samples_session (session_id),
    CONSTRAINT fk_samples_session FOREIGN KEY (session_id) REFERENCES climb_sessions(id)
);
```

**설계 메모**

- `altitude_samples`는 앱이 다운샘플한 값만 들어온다. 서버는 추가 가공하지 않는다.
- "하루 누적 상승"은 별도 컬럼 없이 집계 쿼리로 산출한다(아래 stats).
- 세션 삭제 시 자식 샘플 함께 삭제(cascade 또는 애플리케이션 레벨 명시).
- 개발은 `ddl-auto=update` 허용, 운영은 마이그레이션 도구(Flyway 권장, Post-MVP 정식화).

---

## API 명세

**공통 규약**

- Base URL: `/api`
- 인증 헤더: `Authorization: Bearer <JWT>` (`/api/auth/**` 제외 전 엔드포인트 인증 필수)
- 상태 코드: 200 / 201 / 400 / 401 / 403 / 404 / 409
- 에러 envelope:

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "이메일 형식이 올바르지 않습니다." } }
```

**엔드포인트**

- `POST /api/auth/signup` — `{email, password, nickname}` → 201 `{accessToken, user}`. 오류: 409 EMAIL_DUPLICATED / 400 VALIDATION_ERROR
- `POST /api/auth/login` — `{email, password}` → 200 동일 형식. 오류: 401 INVALID_CREDENTIALS
- `POST /api/auth/kakao` — `{code, redirectUri}` → 200 동일 형식(provider=KAKAO). 오류: 401 KAKAO_AUTH_FAILED
- `GET /api/users/me` — 200 `{id, email, nickname, provider, createdAt}`
- `GET /api/sessions?date=&page=&size=` — 200 페이징 목록 `{content, page, size, totalElements}`
- `POST /api/sessions` — 세션 저장(멱등). 201 `{id, clientUuid}`
- `GET /api/sessions/{id}` — 200 상세 + samples 배열. 오류: 404 / 403
- `DELETE /api/sessions/{id}` — 204. 자식 샘플 함께 삭제. 오류: 404 / 403
- `GET /api/stats/daily?date=YYYY-MM-DD` — 200 `{date, totalGainM, sessionCount, maxAltitudeM}`

### `POST /api/sessions` 요청 바디 (앱 계산 결과 + 다운샘플)

```json
{
  "clientUuid": "uuid-v4",
  "startedAt": "2026-06-01T08:00:00+09:00",
  "endedAt": "2026-06-01T09:30:00+09:00",
  "durationSec": 5400,
  "maxAltitudeM": 82.4,
  "cumulativeGainM": 120.5,
  "startLat": 37.66,
  "startLng": 127.01,
  "regionName": "서울 도봉구 / 북한산 인근",
  "slowestBandFromM": 30,
  "slowestBandToM": 40,
  "slowestBandSpeedMps": 0.042,
  "samples": [
    { "tOffsetSec": 0, "altitudeM": 0 },
    { "tOffsetSec": 5, "altitudeM": 1.2 }
  ]
}
```

- `slowestBand*` 세 필드는 null 허용(앱에서 산출 불가 시).
- `regionName`도 null 허용(역지오코딩 실패).

---

## 멱등 저장 (`POST /api/sessions` 핵심 로직)

오프라인 재전송이 안전해야 한다("전송은 됐는데 응답을 못 받아 앱이 재전송").

- `(user_id, client_uuid)`로 기존 레코드 조회.
- 있으면 새로 만들지 않고 기존 id 반환(409 던지지 말고 멱등 처리 → 200 또는 기존 id로 201 응답).
- 없으면 신규 저장 후 201.
- 동시성으로 UNIQUE 위반이 나도 잡아서 기존 레코드 반환으로 처리.
- 세션은 종료 후 불변(편집 없음) → 업데이트 경로 불필요. 업로드는 단방향(앱→서버)만.

---

## 인증 / 보안

### 이메일

- 회원가입: 이메일 중복 검사 → BCrypt 해싱 → `provider=LOCAL` 저장.
- 로그인: 이메일 조회 → BCrypt 매칭 → JWT 발급.
- Bean Validation: 이메일 형식, 비밀번호 최소 8자, 닉네임 2~20자.

### 카카오 (OAuth 웹) — 서버 책임

1. 앱이 `{code, redirectUri}` 전달.
2. 서버가 `client_secret`으로 카카오 토큰 교환 → `/v2/user/me` 사용자 조회.
3. `kakao_id`로 find-or-create(없으면 `provider=KAKAO`, `password_hash=null`, nickname=카카오 닉네임).
4. 자체 JWT 발급해 반환.

- **MVP 정책(코드 주석으로 명시)**: LOCAL/KAKAO 동일 이메일은 `kakao_id` 기준 별도 계정으로 처리. 계정 통합은 Post-MVP.
- 카카오 토큰 교환 실패 → 401 KAKAO_AUTH_FAILED. 내부 상세 오류는 로그만, 응답에 노출 금지.

### JWT

- 액세스 토큰(Bearer), 알고리즘 HS256, `JWT_SECRET` 서명.
- 클레임: `sub(userId)`, `iat`, `exp`. 만료 예: 수 시간~1일.
- 검증: 커스텀 `JwtAuthenticationFilter`에서 `Authorization` 파싱·검증 후 `SecurityContext`에 인증 주입.
- MVP는 리프레시 토큰 없음 → 만료 시 재로그인. (Post-MVP 후보)

### 인가 규칙

- `/api/auth/**`만 공개(`permitAll`).
- `/api/sessions/**`, `/api/users/me`, `/api/stats/**`는 인증 필수.
- 세션 조회/삭제 시 소유권(`userId == resource.user_id`) 검증 → 불일치 403 FORBIDDEN.
- 전 구간 HTTPS(종단은 리버스 프록시/호스팅 TLS).
- CORS: 앱(Dev Build) origin 허용 설정 필요.

---

## 집계 (stats)

- `GET /api/stats/daily?date=`: 해당 날짜 세션들의 `cumulative_gain_m` 합 = `totalGainM`, `sessionCount`, 그 날 `max_altitude_m`의 최댓값.
- 날짜 경계 타임존: 클라이언트가 date 파라미터를 명시하는 방식과 서버 타임존 변환 방식 중 하나로 통일해야 함(열린 이슈). 정해지면 주석으로 고정.

---

## 예외 처리

- 전역 `@RestControllerAdvice`로 공통 에러 envelope 반환.
- 검증 실패 → 400 VALIDATION_ERROR(필드별 메시지).
- 인증 실패 → 401, 권한 없음 → 403, 없는 리소스 → 404, 중복 → 409.
- 카카오 등 외부 연동 실패의 내부 스택트레이스는 응답에 노출하지 않는다.

---

## 테스트

- 단위: 인증·세션 서비스, 멱등 저장 로직 → JUnit + Mockito.
- 통합: API E2E, JWT 필터, JPA → Spring Boot Test + Testcontainers(MySQL) 또는 H2.
- 핵심 검증:
    - 동일 `clientUuid` 2회 POST → 레코드 1건.
    - 타 사용자 세션 조회/삭제 → 403.
    - 만료/위조 토큰 → 401.
    - 일일 집계 합산 정확성.

---

## 빌드 / 배포

- 실행 가능한 jar(Spring Boot), 프로파일 분리(`dev`/`prod`).
- 시크릿은 환경 변수 주입(위 목록), 커밋 금지.
- DB: MySQL. 개발 `ddl-auto=update`, 운영은 Flyway 마이그레이션(Post-MVP 정식화).

---

## 코드 작성 시 주의

- 위 의존성 외 임의 추가 금지(다른 인증/ORM 라이브러리로 교체하지 않는다).
- 엔티티를 컨트롤러 응답에 직접 노출 금지 — 특히 `password_hash`는 어떤 응답에도 포함하지 않는다.
- 서버에서 고도/밴드 재계산 코드를 작성하지 않는다(앱 책임).
- 새 엔드포인트/필드 추가 전 MVP 범위(F1 인증 / F3 세션 저장·조회·집계)인지 확인.

---

## 열린 이슈 (구현 중 결정)

- 일일 집계 날짜 경계: 클라이언트 date 파라미터 vs 서버 타임존 변환 — 통일 필요.
- 리프레시 토큰 도입 시점(액세스 만료 UX가 불편하면 Post-MVP 우선순위 상향).
- LOCAL/KAKAO 동일 이메일 계정 통합(MVP는 분리).
- Flyway 등 마이그레이션 도구 정식 도입 시점.
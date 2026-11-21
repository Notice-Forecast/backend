# Notice Forecast Backend

공지예보(Notice Forecast) 백엔드 서버입니다.

## 기술 스택
- Java 21 / Gradle / Spring Boot 4.1.x
- Spring Web, Spring Data JPA, Spring Validation, Spring Actuator
- MySQL 8 (`utf8mb4` / `utf8mb4_unicode_ci`, H2 미사용)
- Springdoc OpenAPI 3 (Swagger UI)
- Jsoup / Web Push (`nl.martijndwars:web-push`, Bouncy Castle)

## 로컬 실행 방법

1. 로컬 MySQL 실행
```bash
docker compose up -d
```

2. 로컬 설정 파일 생성
```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

3. 애플리케이션 실행 (`local` 프로파일)
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 기본 확인 URL
- Health Check: `GET http://localhost:8080/api/v1/health` → `{"status":"ok"}`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## 배포 환경변수
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `LLM_API_KEY`
- `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`
- `ADMIN_KEY`
- `CORS_ALLOWED_ORIGINS` (쉼표 구분, `http://localhost:3000`은 기본 허용)

## 도메인/DB 참고
- 모든 시간은 UTC 기준(`hibernate.jdbc.time_zone: UTC`)으로 저장합니다.
- 공지 카드 필드는 Hibernate `@JdbcTypeCode(SqlTypes.JSON)` JSON 컬럼을 사용합니다.
- 모든 API 컨트롤러는 `/api/v1` 하위에 배치하며, 오류 응답은 `{"status", "code", "message"}` 형식으로 통일됩니다.

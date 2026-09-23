# 테스트와 개발에도 PostgreSQL을 쓴다 (H2 미사용)

테스트는 Testcontainers + `postgres:18`, 개발은 Docker Compose PG 18, 운영은 Railway PG 18로 통일한다. 원래 계획은 테스트 H2 메모리 / 개발 H2 파일이었다.

이유: `MODE=PostgreSQL`을 걸어도 **Hibernate는 `PostgreSQLDialect`가 아니라 `H2Dialect`를 해석한다.** H2 유지관리자 본인이 "H2는 다른 DB의 에뮬레이터가 아니다"라고 밝히고 있다. 구체적으로 이 프로젝트에 걸리는 것은 `PostgreSQLDialect`가 기본 ID 생성 전략을 `SEQUENCE`로 오버라이드하는 반면 `H2Dialect`는 `IDENTITY`로 떨어진다는 점인데, 그게 정확히 이 서비스의 핵심인 Code 발급 경로다. 더해서 `DEFAULT_NULL_ORDERING`을 맞추지 않으면 nullable 컬럼의 `ORDER BY` 결과 순서가 조용히 달라지고, `DATABASE_TO_LOWER`는 DB 생성 후 변경할 수 없다.

**속도를 이유로 H2를 다시 들이지 말 것.** 로컬 속도는 컨테이너 재사용(`withReuse(true)`, CI에서는 자동 무효)과 Spring 컨텍스트 캐시로 확보한다. 마이그레이션은 Flyway 한 벌만 유지하고 벤더별로 쪼개지 않는다 — 쪼개는 순간 테스트하는 스키마와 배포하는 스키마가 달라진다.

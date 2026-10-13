# to2.at

## 기존 Target 수정과 캐시

로그인한 Owner는 목록의 **수정** 버튼에서 Target을 바꾸고 저장/취소할 수 있다. 저장 응답으로 해당 행과 같은 Link의 생성 결과 표시를 즉시 갱신하며 Code/shortUrl은 유지한다. Suspension된 Link의 Target을 바꿔도 Suspension은 풀리지 않는다.

Target API는 Spring Security의 `no-cache, no-store, max-age=0, must-revalidate`를 사용한다. 브라우저 API 요청과 Worker의 `/to/{code}` 조회도 `cache: no-store`다. 모든 Code 응답(Interstitial, 크롤러 302, 404/410/502)은 브라우저·CDN `no-store`이며 영구 리다이렉트나 Worker Cache API를 사용하지 않는다. 정적 응답은 `_headers`의 `max-age=0, must-revalidate`로 브라우저의 300초 상한보다 짧게 재검증한다. 다음 단축주소 접속은 저장된 최신 Target을 조회한다. 이미 열린 경유 화면의 타이머, 외부 Target 사이트, 카카오 등의 미리보기 카드 자체 캐시는 원격으로 변경하지 않는다.

## 외부 공유 주소 생성·복사

로그인된 카카오 Owner로 공유 주소를 단축하고 복사하려면 다음 주소를 연다:

```js
const entry = `https://to2.at/?action=create-and-copy#target=${encodeURIComponent(fullShareUrl)}`
```

`fullShareUrl`은 `#` 이하 콘텐츠를 포함한 전체 URL이다. Target을 한 번 인코딩한다. 쿼리 `?target=...&action=create-and-copy`도 지원하지만 긴 공유 URL은 fragment를 사용한다(Cloudflare HTTP 요청 URL 16KB 제한). 로그인 전 Target을 보관하고 카카오 로그인 복귀 후 자동 생성·복사를 시도한다. 복사가 제한되면 결과와 복사 버튼을 표시한다. 동일 Owner가 같은 외부 공유 주소를 다시 열거나 새로고침하면 같은 Link를 재사용한다. 삭제·Suspension·Target 수정된 Link를 새 Code로 자동 우회하지 않는다.

API `POST /links`의 JSON은 선택적으로 UUID `requestId`를 받는다. 최초 201, 동일 Owner/요청의 재사용 200, Target 불일치 409다. requestId 없는 일반 생성은 기존처럼 같은 Target도 별도 Link를 만든다. Target은 PostgreSQL TEXT/JSON 본문으로 저장·반환하며, 128KiB 이상 hash 포함 공유 주소 왕복을 테스트한다.

카카오 계정으로 만드는 짧은 주소. 용어는 [CONTEXT.md](./CONTEXT.md), 결정 배경은 [docs/adr/](./docs/adr)에 있습니다.

```
back/   Kotlin · Spring Boot 4.1 · JDK 25 · PostgreSQL 18   → Railway (api.to2.at)
front/  Vite · React · TanStack Router · Cloudflare Workers → Cloudflare (to2.at)
```

## 로컬 실행

Docker와 JDK 25, Node가 필요합니다.

```bash
cd back && ./gradlew bootRun
```

PostgreSQL 18은 `back/compose.yaml`에서 자동으로 뜹니다.

```bash
cd front && pnpm install && pnpm dev
```

`http://localhost:5173`에서 열립니다. `pnpm dev`는 Worker까지 함께 돌리므로
`http://localhost:5173/1` 같은 Code 경로도 실제와 같은 경로를 탑니다.

개발용 샘플 계정(`user1`/`user1` 등)을 쓰려면 `dev` 프로필로 기동하세요:

```bash
cd back && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

## Owner API 키

카카오 로그인 후 대시보드의 **API 키**에서 이름을 입력하고 키를 발급합니다.
원문은 최초 발급 화면에서만 표시됩니다. 키를 가진 도구는 해당 Owner의 Link를
생성·조회·삭제할 수 있으며, 사이트에서 폐기하면 다음 요청부터 거부됩니다.
키 관리는 카카오 로그인이 필요합니다.

개인 스킬 소스는 `tools/to2-at-skill/`입니다. 설치된 스킬 폴더에서
`python scripts/to2.py auth-set`을 대화형 터미널로 실행해 숨김 입력으로 등록한 뒤
`python scripts/to2.py create https://example.com`을 사용합니다.
Windows에서는 개인 키를 DPAPI로 암호화해 저장합니다. `TO2_API_KEY` 또는
`TO2_API_KEY_FILE`로 준비된 비밀을 사용할 수도 있습니다.

API는 `Authorization: Bearer <key>`를 받습니다. 원문 대신 검증용 해시만
저장하며 기존 카카오 로그인과 Operator 경로는 그대로 유지합니다.
같은 단축주소의 Target을 수정하려면 `PATCH /links/{code}`에
`{"target":"https://example.com/new"}`를 보냅니다. 성공하면 200 LinkResponse를
반환하고 Code·단축주소·상태를 유지합니다. 본인 Link만 수정할 수 있고 삭제된
Link는 404로 거부합니다. Suspension 중인 Link는 Target을 바꿔도 중지 상태를 유지합니다.
배포 시 Flyway V5가 API 키 테이블을 추가합니다. 기존 Link/Owner 데이터는
변경하지 않습니다. 서버 배포 후 프론트 배포, 본인 카카오 로그인과 키 발급,
로컬 `auth-set` 순서로 운영 적용합니다.

## 테스트

```bash
cd back && ./gradlew test     # Seam A — 실제 PostgreSQL 18 컨테이너 위에서 전체 스택
cd front && pnpm test         # Seam B — 실제 Workers 런타임에서 fetch 핸들러
```

Seam은 이 둘뿐입니다. 서비스나 리포지토리를 직접 부르는 테스트는 두지 않습니다.

## 배포

### 백엔드 (Railway)

- 빌더는 **Dockerfile**로 둡니다. 기본 빌더는 Gradle toolchain을 읽지 않아 JDK 21로 빌드합니다
- PostgreSQL은 **`:18`로 명시 고정**합니다. 떠 있는 기본 태그는 더 낮은 메이저로 해석됩니다
- Root Directory는 `back/`, Watch Path는 `back/**` — watch path는 Root Directory와 무관하게 `/`부터 평가됩니다
- 환경변수:

  | 변수 | 설명 |
  |---|---|
  | `SPRING_PROFILES_ACTIVE` | `prod` |
  | `PGHOST` `PGPORT` `PGDATABASE` `PGUSER` `PGPASSWORD` | Postgres 서비스 참조 변수. **`DATABASE_URL`은 libpq URI라 그대로 쓸 수 없습니다** |
  | `KAKAO_CLIENT_ID` `KAKAO_CLIENT_SECRET` | 카카오 REST API 키와 시크릿 |
  | `ADMIN_PASSWORD` | Operator 비밀번호. 이 변수 외의 어디에도 두지 않습니다 |

### 프론트 (Cloudflare Workers)

```bash
cd front && npx wrangler deploy
```

운영 값은 `wrangler.jsonc` **최상위**에 둡니다. 빌드 플러그인이 이 파일로부터
자체 설정을 생성하는데 named environment를 그대로 넘기지 않아서, `env.production`
블록은 배포 시 조용히 무시됩니다. 로컬 전용 값은 `.dev.vars`에 둡니다.

### 배포 상태

- 백엔드: Railway, **Southeast Asia (Singapore)**. Root Directory `/back`이라
  `back/Dockerfile`이 자동으로 쓰입니다 — Railway의 `Builder` 설정에는 Dockerfile
  항목이 없고, 루트에 Dockerfile이 있으면 그걸 씁니다
- `watchPatterns`는 비워둡니다. GitHub 연동 배포용인데 CLI 업로드 배포까지
  가로채 빌드를 스킵시킵니다
- 리전 변경은 CLI/GraphQL로는 반영되지 않습니다. 대시보드의
  Settings → Scale → Regions & Replicas에서 바꿔야 합니다

### 카카오 개발자 콘솔에서 직접 해야 하는 것

코드로 대신할 수 없는 부분입니다.

1. **OpenID Connect를 켭니다.** 켜지 않으면 ID 토큰이 나오지 않고 로그인이 동작하지 않습니다
2. **Redirect URI를 정확히 등록합니다.** 끝 슬래시 하나만 달라도 다른 URI로 취급됩니다
   - 개발: `http://localhost:8080/login/oauth2/code/kakao`
   - 운영: `https://api.to2.at/login/oauth2/code/kakao`
3. 동의 항목은 `profile_nickname`, `account_email`입니다. `account_email`은 **Biz 앱 전용**이고,
   Biz 앱이어도 값이 비어 있을 수 있습니다

### 첫 공유 전에 확인할 것

카카오의 링크 미리보기 캐시는 URL당 약 1시간이고 **일괄 초기화 수단이 없습니다**.
초기화는 로그인이 필요한 공유 디버거에서 URL 하나씩만 가능하므로, 처음 공유하기 전에
`to2.at/<Code>`를 카카오톡에 붙여 Target의 카드가 뜨는지 확인하세요.

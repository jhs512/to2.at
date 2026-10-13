# API 계약

사이트: https://to2.at · API: https://api.to2.at

API 키 기능은 2026-10-13 운영에 배포됐다. 운영에서 키 발급 경로가 404이거나 Bearer 요청이 리다이렉트하면 서비스 배포 상태를 확인한다.

| CLI | API | 성공 | 인증 |
| --- | --- | --- | --- |
| health | GET /actuator/health | 200, status | 공개 |
| resolve Code | GET /to/{code} | 200, code/state/target? | 공개 |
| me | GET /me | 200, nickname/email? | Bearer API 키 |
| list | GET /links | 200, Link 배열 | Bearer API 키 |
| create Target [--name Name] | POST /links, target 문자열·선택 name | 201, Link | Bearer API 키 |
| update Code Target [--name Name] | PATCH /links/{code}, target·선택 name | 200, Link | Bearer API 키 |
| rename Code Name | PATCH /links/{code}, name만 | 200, Link | Bearer API 키 |
| delete Code | DELETE /links/{code} | 204 | Bearer API 키 |

Link: code, shortUrl, target, state, createdAt. 목록은 최신순이고 DELETED를 제외하며 SUSPENDED는 포함한다. 공개 resolve는 ACTIVE에만 Target을 제공한다. DELETED/SUSPENDED도 200으로 상태를 반환하며 없는 Code는 404다. 삭제 시 없는 Code·다른 Owner의 Code·이미 DELETED인 Code는 모두 404다.

대상 수정 API는 2026-10-13 운영 배포됐다(구현 커밋 6138c8c). code/shortUrl/createdAt과 기존 state를 유지한다. 없는 Code·다른 Owner·DELETED는 거부한다. 운영의 PATCH 배포 여부는 실제 응답으로 확인하며 405이면 미지원으로 보고한다. 개인 등록 키로 PATCH의 유효하지 않은 Code 거부(404)를 확인했고, 실제 운영 Link는 변경하지 않았다. 성공적인 수정과 소유권·상태 유지는 서버 통합 테스트에서 검증됐다.

Target은 http/https이며 to2.at 및 하위 도메인은 거부된다. 서버가 최종 검증하고 400의 message를 반환한다. Code는 양의 정수 정규형으로 앞자리 0을 허용하지 않는다.

## Link 이름

name은 Owner가 기억하기 위한 선택적 이름이다. Owner 목록과 생성·수정 응답에 name: string 또는 null이 포함되며 공개 resolve에는 노출되지 않는다. 앞뒤 공백을 제거하고 최대 100자로 제한한다. POST에서 생략하면 이름 없이 생성한다. PATCH에서 생략한 필드는 유지하고, name이 빈 문자열 또는 null이면 이름을 제거한다. 이름만 수정할 때 target을 보내지 않는다. 기존 외부 생성 requestId의 재시도는 이름을 덮어쓰지 않는다. 이름 지원의 운영 배포 상태는 실제 Owner 목록과 담당 작업의 배포 기록으로 확인한다.

## 키 수명주기

카카오 로그인한 Owner의 세션으로 POST /api-keys에 name(1~80자)을 보내면 201로 id/name/createdAt/key를 한 번 반환한다. GET /api-keys는 본인의 id/name/createdAt/revokedAt만 반환한다. DELETE /api-keys/{id}는 본인의 키를 폐기하고 204를 반환하며 다른 Owner의 키는 404다. 원문은 서버에 저장하지 않아 재조회할 수 없다.

Authorization: Bearer 키는 /me와 Link API에서만 사용할 수 있다. 키로 키를 관리할 수 없다. 무효·폐기된 키는 401이며 로그인으로 리다이렉트하지 않는다. 카카오 세션이 함께 있어도 잘못된 키에서 세션으로 넘어가지 않는다. 키에 기본 만료일은 없으며 사이트에서 명시적으로 폐기한다.

CLI는 TO2_API_KEY → TO2_API_KEY_FILE → 개인 저장소 순서로 읽는다. 파일에는 키 원문 한 줄을 넣고 읽기 권한을 제한한다. 기본 개인 저장소는 CODEX_HOME 또는 ~/.codex 아래 secrets/to2-at.key다. Windows에서는 DPAPI 암호문, POSIX에서는 권한 0600 파일이다. auth-clear는 개인 저장본만 제거하므로 환경변수 설정은 별도로 해제하고 서버 키는 사이트에서 폐기한다.

CLI는 키를 https://api.to2.at 또는 명시한 HTTP loopback 모의 서버에만 전송한다. OAuth 리다이렉트를 따라가지 않고 쓰기를 자동 재시도하지 않는다. 원문 키를 명령 인수·채팅·로그·스킬 소스에 넣지 않는다. 테스트에는 무작위 운영 키 대신 모의 키를 쓴다.

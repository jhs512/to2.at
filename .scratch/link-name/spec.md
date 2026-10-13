Status: ready-for-agent

# Owner가 Link의 Name을 기억하고 수정한다

사용자가 요청한 기억용 이름은 선택적 Name이며 Code를 바꾸지 않는다. 개인 스킬은 담당 작업이 수정한다. 후속 요청에 따라 SKILL.md/references/api.md/scripts/to2.py/scripts/test_to2.py 네 파일만 tools/to2-at-skill 복사본에 동기화한다. 자격 증명·비밀 파일은 복사하지 않는다.

- DB에 nullable name을 추가한다. 기존 Link는 이름 없는 상태를 유지한다. POST /links는 선택적 name을 받아 저장한다. 이름은 문자열 또는 null이며 trim 후 최대 100 Unicode code point이다. 빈 문자열/공백/null은 이름 없음으로 저장한다. 길이/타입 오류는 400이며 message를 반환한다.
- PATCH /links/{code}는 target/name 중 하나 이상을 받는다. target 생략은 유지, 전달한 null/잘못된 Target은 기존 검증대로 400. name 생략은 유지, 빈 문자열/공백/null은 제거. 둘을 전달하면 원자적으로 함께 저장한다. 알 수 없는 필드만 있거나 빈 객체는 400.
- GET Owner 목록·POST/PATCH 응답에 name:string|null을 항상 포함한다. 기존 응답 필드, 인증, 소유권 404, 삭제 거부, Suspension 및 생성 시각을 보존한다. 공개 /to/{code} 및 Interstitial은 Name을 노출하지 않는다. 기존 no-store 정책을 유지한다.
- requestId 재시도는 Target 충돌 규칙을 유지하고 기존 결과의 현재 Name을 반환한다. 재시도에 이름을 보내도 기존 이름을 덮어쓰지 않는다. 변경은 PATCH로 한다.
- UI는 목록에 Name을 평문으로 표시하고 기존 수정 폼에 선택적 이름 입력을 추가한다. 저장/취소/실패 동작과 즉시 갱신을 유지한다. 생성 폼 확장은 요청하지 않았다.

## 검증 seam

기존 PostgreSQL HTTP 경계에서 POST→목록→name-only PATCH→Target-only PATCH→삭제/권한/상태, 이름 제거·100자 경계·잘못된 타입·원자성·requestId 재시도를 확인한다. frontend fetch 요청 계약과 Chrome 로컬 모의 API로 이름 표시·수정·취소를 확인한다. 운영 Link는 변경하지 않고 Flyway/배포 로그·운영 읽기 응답으로 검증한다. Standards/Spec 리뷰 후 main 커밋·push와 Railway/Cloudflare 배포를 완료한다.

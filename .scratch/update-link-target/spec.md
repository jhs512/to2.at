Status: ready-for-agent

# Owner가 같은 Code의 Target을 수정한다

사용자 요청: 개인 스킬로 단축주소 생성 및 단축주소의 가리키는 주소 수정. 스킬과 실제 키 등록은 요청을 전달한 채팅에서 담당하며 여기서는 선행 서버 API를 구현·배포한다.

- PATCH /links/{code}, JSON {target: 문자열}, 성공 200 LinkResponse.
- 카카오 Owner와 유효한 API 키 모두 지원하며 기존 소유권·Target 검증을 재사용한다.
- Code, shortUrl, Owner, createdAt 및 상태는 유지하고 Target만 바꾼다. 삭제·타인·없는·비정규 Code는 404.
- SUSPENDED의 Target을 수정해도 Suspension을 유지하고 공개 resolve에 Target을 제공하지 않는다.
- 잘못된 Target은 400으로 거부하며 기존 Target을 유지한다. 무인증·무효/폐기 키는 기존 인증 계약대로 거부한다.
- 같은 Link에 대한 상태 변경과 경합하더라도 Target 수정이 상태를 덮어쓰지 않도록 Target 필드만 갱신한다.
- 카카오 세션의 PATCH를 위해 CORS preflight도 PATCH를 허용한다.

## 검증 seam 및 운영 범위

기존 Seam A (MockMvc + 실제 PostgreSQL 18)로 Bearer 수정·Code 보존·공개 resolve 갱신·소유권·삭제·Suspension·Target 검증·인증·CORS를 확인한다. 기존 Seam B의 Worker 테스트도 마지막에 돌린다.
사용자가 main 반영·푸시·운영 배포를 요청했다. 운영 검증은 키 원문 없이 응답·배포 상태로 확인하며 실제 사용자 Link를 수정하지 않는다.

Status: ready-for-agent

# Owner 목록에서 Target을 수정하고 즉시 이동에 반영한다

사용자 요청: 기존 PATCH /links/{code}를 Owner 대시보드에 연결하고 저장 성공 직후 목록 Target을 갱신한다. Code/shortUrl은 유지하며 기존 Owner 권한·Target 검증·Suspension/삭제 규칙을 따른다.

- 각 행에 수정 버튼과 기존 Target 입력, 저장/취소를 제공한다. 저장 중 중복 요청을 막고 서버 실패 시 입력과 오류를 유지한다. Suspension 상태는 유지하고 삭제된/다른 Owner의 Link는 서버의 거부를 따른다.
- PATCH 응답으로 행을 즉시 바꾸며 불필요한 GET 응답이 새 Target을 되돌리지 않게 한다. 서버가 trim한 Target을 표시한다. UI는 API 키 대신 기존 카카오 세션을 사용한다.
- Target을 포함하는 API/목록/PATCH 응답, Interstitial, 크롤러 302는 no-store. Spring Security의 기존 API no-store 정책은 HTTP 테스트로 확인한다. Worker fetch는 cache:no-store로 DB 최신 값을 조회하며 Cache API/영구 리다이렉트를 도입하지 않는다. Code 오류·삭제·Suspension 페이지도 no-store.
- Worker가 생성하는 응답에는 CDN-Cache-Control:no-store 및 Cloudflare-CDN-Cache-Control:no-store를 붙인다. to2.at 정적 응답의 브라우저 캐시도 max-age=0,must-revalidate로 고정하여 300초 상한을 지킨다. 서비스 밖 Target 사이트나 미리보기 서비스 자체 캐시는 제어 대상이 아니다.
- 기존 CDN의 오래된 Code/리다이렉트 캐시가 남지 않게 필요 시 to2.at 영역을 purge한다. 브라우저에 이미 열린 Interstitial의 과거 타이머를 원격 변경하는 기능은 추가하지 않으며 다음 접속은 새 Target을 조회한다.

## 검증 seam

실제 PostgreSQL HTTP PATCH 전후 조건부 GET의 최신 Target과 no-store, Worker 전후 HTML/302·조회 캐시 우회·모든 상태 응답 정책, frontend API 요청·저장 UI의 오류/취소/즉시 갱신. Chrome + 로컬 모의 API로 저장·재접속을 확인한다. 운영 Link는 수정하지 않고 운영 헤더로 배포를 검증한다. Standards/Spec 리뷰 후 main 반영·push·배포한다.

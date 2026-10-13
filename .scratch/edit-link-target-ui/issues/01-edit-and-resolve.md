Blocked by: none
Status: ready-for-agent

# Target 수정 UI와 최신 이동 응답을 연결한다

행 수정 UI·PATCH 호출·즉시 목록 갱신·Worker와 정적 헤더 정책·HTTP/Worker/브라우저 검증을 수직으로 구현한다. 기존 운영 Link를 수정하지 않는다.

## Comments

### 2026-10-13 — 구현·검증·운영 반영 완료

Resolution: complete

- 구현 커밋 `3e16142`, main push 완료. 각 Link 행에서 Target 수정·저장·취소를 제공한다. PATCH 응답을 즉시 반영하고 새 목록 조회로 생성/삭제와 겹치는 갱신도 보존한다. 외부 생성 결과 카드의 Target도 같은 Code의 최신 목록 값과 동기화한다.
- 지연 원인: 기존 크롤러 302에 Cache-Control이 없었다. Worker의 Code 응답 전체에 브라우저/CDN no-store를 적용하고 backend fetch 및 frontend API fetch도 cache:no-store로 요청한다. API는 기존 Spring Security no-store 정책을 유지한다. Worker/Cache API의 별도 캐시를 추가하지 않았다.
- 백엔드 전체 94개, frontend 34개 테스트 통과. typecheck/build 및 lint 통과(기존 Fast Refresh 경고 3개). Standards 리뷰 발견 사항 없음. Spec 리뷰의 생성/삭제 목록 갱신 경쟁 P2를 수정하고 재리뷰에서 해결 확인.
- Chrome + 로컬 모의 API: 잘못된 Target 실패 시 입력 유지, 취소 시 기존 값 유지, 저장 후 같은 Code의 새 Target 즉시 표시, 기존 Interstitial 방문 뒤 다음 방문의 새 Target 이동 확인. 생성→지연 GET→기존 Link 수정→최신 GET→지연 응답 순서에서도 새 Link와 새 Target 유지 확인. 실제 운영 Link는 변경하지 않았다.
- Cloudflare Worker `61939122-7b9d-4efc-b529-31f7cb60f398` 배포 완료. 운영 API_ORIGIN=https://api.to2.at 확인. backend 런타임 코드 변경 없음.
- to2.at Cloudflare Browser Cache TTL의 기존 4시간 설정을 '기존 헤더 유지'로 변경. 정적 응답은 max-age=0,must-revalidate로 300초 상한을 지킨다. CLI purge 인증 실패 후 로그인된 Cloudflare 대시보드에서 모두 제거를 실행해 성공 접수 및 5초 내 적용 안내 확인.
- 배포/purge 후 운영 GET: /1 일반 UA 200, crawler UA 302 모두 Cache-Control/CDN-Cache-Control/Cloudflare-CDN-Cache-Control:no-store 및 Expires:0. /to/1 조건부 GET 200 no-cache,no-store,max-age=0,must-revalidate 및 Expires:0. /, 최신 JS/CSS 200 public,max-age=0,must-revalidate. 새 routes-CGQZCaK3.js에 PATCH 및 운영 API origin 포함, localhost QA origin 없음. API health 200.
- 이미 열린 Interstitial의 이전 타이머와 외부 Target/미리보기 서비스 자체 캐시는 이 서비스가 원격 변경하지 않는다. 다음 단축주소 방문은 새 Target을 조회한다.

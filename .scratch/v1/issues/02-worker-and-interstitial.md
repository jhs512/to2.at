# 02: Worker와 Interstitial

**What to build:** 사람이 짧은 주소를 열면 도착지 호스트가 곧바로 화면에 뜨고, 최소 1초 머문 뒤 Target으로 이동한다. 이 티켓이 프론트 뼈대와 Worker, 그리고 두 번째 테스트 seam을 세운다.

**Blocked by:** 01 (Code 해석 API가 있어야 Worker가 물어볼 곳이 생긴다)

**Status:** done

- [x] SPA 뼈대가 빌드되고 Worker가 정적 자산을 서빙한다
- [x] **숫자로 된 경로를 Worker가 실제로 받는다** — 브라우저 주소창에 직접 입력한 경우를 포함해서. (기본 설정으로는 SPA 폴백이 가로채 Worker가 호출되지 않는다. 자산 디렉터리는 예외로 두어 번들 서빙을 무료로 유지한다)
- [x] Interstitial은 Worker가 직접 그린 HTML이다. SPA 번들 로딩을 기다리지 않고 즉시 칠해진다 — Visitor가 처음 보는 것이 빈 화면이어서는 안 된다
- [x] 도착지 **호스트**가 눈에 띄게 표시된다
- [x] 최소 1초 머문 뒤 이동한다
- [x] 도착지에서 뒤로가기를 누르면 Interstitial로 되돌아오지 않는다 (히스토리를 쌓지 않는 이동, ADR 0004)
- [x] JavaScript가 꺼진 환경에서도 Target으로 가는 링크가 보인다
- [x] 존재하지 않는 Code는 전용 안내 화면을 보여준다
- [x] Interstitial에는 Target이 사전 검사되었다는 뜻의 문구가 **없다** (사전 검사를 하지 않으므로)
- [x] Seam B가 선다: Worker의 fetch 핸들러를 실제 Workers 런타임에서 호출하고 백엔드는 fetch 경계에서 스텁한다
- [x] 숫자가 아닌 경로는 정적 자산으로 흘러간다

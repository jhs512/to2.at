# 프론트와 백엔드를 별도 도메인으로 분리한다

`to2.at`은 Cloudflare(정적 SPA + Worker), `api.to2.at`은 Railway(Spring)로 둔다. 처음 의도는 `to2.at/api/*` 한 도메인이었으나, Cloudflare에서 경로 하나를 외부 오리진으로 보내는 기능(Origin Rules의 DNS/Host 오버라이드)이 Enterprise 전용이라 Worker 프록시 없이는 불가능했다. Worker로 프록시하면 `Set-Cookie`의 `Domain=` 재작성과 다중 `Set-Cookie` 파싱 같은 부담이 따라오는데, 도메인을 나누면 그 전부가 사라진다.

대가로 CORS와 preflight가 생긴다. 두 도메인은 same-site이므로 세션 쿠키는 `SameSite=Lax`로 충분하다 — **`Strict`로 두면 카카오 콜백으로 돌아올 때 쿠키가 실리지 않아 로그인이 깨진다.**

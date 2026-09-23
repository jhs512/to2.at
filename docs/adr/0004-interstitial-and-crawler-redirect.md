# Code 경로는 즉시 리다이렉트 대신 Interstitial을 거치고, 크롤러만 302로 보낸다

`to2.at/<Code>`는 사람에게 최소 1초간 Interstitial을 보여준 뒤 Target으로 보낸다. Visitor가 어디로 가는지 눈으로 확인할 시간을 주고, Report 버튼을 놓을 자리를 만들기 위해서다. 이동은 반드시 `location.replace()`로 한다 — `location.href`를 쓰면 Interstitial이 히스토리에 남아 Target에서 뒤로가기를 눌렀을 때 다시 Target으로 튕겨 빠져나올 수 없다.

Interstitial이 200 HTML이므로 링크 미리보기 크롤러는 그 페이지를 긁는다. 런타임에 생성되는 URL에 개별 미리보기를 주는 방법은 정적 SPA에 없으므로, Worker에서 **크롤러 User-Agent를 골라 Target으로 302**한다. 카카오는 리다이렉트를 따라가 도착지를 스크랩하므로, 이 한 줄로 Target의 실제 카드가 그대로 나온다. OG 태그를 긁어 저장할 필요가 없다.

**구현하면서 범위를 넓혔다.** 원래는 `kakaotalk-scrap` 하나만 매칭할 계획이었다. 실제로는 Slack·Discord·X·Meta·LINE·iMessage·Telegram의 크롤러도 함께 매칭한다 — 짧은 주소는 카카오톡에만 붙는 게 아니고, 이들 전부 JS를 실행하지 않아 같은 302로 같은 이득을 본다. 좁게 잡으면 카카오 밖에서는 모든 링크가 동일한 미리보기로 보인다. 카카오 UA에 `facebookexternalhit` 접두사가 함께 들어 있다는 사실은 여전히 참이지만, 이제는 그것도 의도적으로 매칭 대상이다.

주의: **Suspension된 Link는 크롤러에게도 302를 주면 안 된다** — 차단해놓고 카드에는 Target이 그대로 뜬다. 그리고 Interstitial에 `og:url`을 요청 URL과 다르게 넣으면 카카오가 그 주소를 다시 긁으러 간다. 카카오 OG 캐시는 약 1시간이고 퍼지 API가 없어 수동 초기화뿐이므로, 첫 공유 전에 맞춰둬야 한다.

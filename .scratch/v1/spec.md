# to2.at v1

Status: ready-for-agent

## Problem Statement

긴 URL을 카카오톡이나 메신저로 공유할 때 주소가 길어 지저분하고, 어디로 가는지 한눈에 들어오지 않는다. 기존 단축 서비스를 쓰면 내가 만든 Link 목록을 관리할 수 없고, 누군가 그 서비스를 불법 사이트 유포에 쓰더라도 끊을 방법이 없다.

`to2.at` 도메인을 갖고 있으니, 카카오 계정으로 로그인해 짧은 주소를 만들고, 내가 만든 것만 모아 보고, 문제가 생기면 끊을 수 있는 최소한의 단축 서비스를 직접 운영하고자 한다.

## Solution

`to2.at/1`, `to2.at/2` 처럼 짧은 주소를 발급한다.

- **Owner**는 카카오 계정으로 로그인해 Target을 입력하고 Code를 발급받는다. 자기가 만든 Link만 목록으로 보고 지울 수 있다.
- **Visitor**는 로그인 없이 단축 주소를 연다. 곧바로 튕겨지는 대신 **Interstitial**에서 최소 1초간 도착지 호스트를 눈으로 확인하고 이동한다. 그 화면에 **Report** 버튼이 있다.
- **Operator**는 아이디/비밀번호로 로그인해 신고 목록을 보고, 문제가 있는 Link를 **Suspension**한다. 별도 알림은 받지 않고 직접 화면을 확인한다.
- 링크 미리보기 크롤러에게는 Interstitial 대신 Target으로 302를 보내, 카카오톡에 붙였을 때 Target의 실제 카드가 뜨게 한다.

## User Stories

### Owner

1. As an Owner, I want to log in with my Kakao account, so that I don't have to create or remember another password.
2. As an Owner, I want to be redirected straight to Kakao when I open a page that needs login, so that I don't have to click through an intermediate login screen.
3. As a first-time Owner, I want my account created automatically on first Kakao login, so that there is no separate sign-up step.
4. As an Owner, I want my Kakao nickname shown after login, so that I can tell I'm signed in as the right account.
5. As an Owner, I want to paste a Target and get a Code back, so that I can share a short address instead of a long one.
6. As an Owner, I want the full short address shown with a copy button, so that I can paste it into a chat without retyping.
7. As an Owner, I want an invalid Target rejected with a clear message, so that I don't create a Link that goes nowhere.
8. As an Owner, I want a Target pointing back at this service rejected, so that I can't accidentally create a redirect loop.
9. As an Owner, I want to see the Links I created, newest first, so that I can find one I shared earlier.
10. As an Owner, I want each row to show the Code, the Target and when it was created, so that I can tell my Links apart.
11. As an Owner, I want to see that one of my Links is Suspended, so that I understand why it stopped working.
12. As an Owner, I want to delete a Link I created, so that it stops resolving when I no longer want it shared.
13. As an Owner, I want a deleted Link's Code never reused for another Link, so that an address I already shared can never point somewhere unexpected.
14. As an Owner, I want to see only my own Links, so that other people's Targets aren't exposed to me.
15. As an Owner, I want another Owner unable to delete my Link, so that my Links are safe from other accounts.
16. As an Owner, I want to stay logged in across backend redeploys, so that I'm not signed out several times a day.
17. As an Owner, I want to log out, so that I can leave the session on a shared machine.

### Visitor

18. As a Visitor, I want a short address to work without logging in, so that anyone I share a Link with can open it.
19. As a Visitor, I want to see the destination host before I'm moved, so that I can decide whether I trust the link.
20. As a Visitor, I want to stay on the Interstitial for at least one second, so that I actually have time to read the destination.
21. As a Visitor, I want the Interstitial to appear immediately rather than after a blank screen, so that the wait doesn't feel broken.
22. As a Visitor, I want pressing Back at the destination to return me where I came from rather than into the Interstitial again, so that I don't get trapped in a loop.
23. As a Visitor, I want a clear "no such link" page for an unknown Code, so that I know the address is wrong rather than broken.
24. As a Visitor, I want a clear block page for a Suspended Link that does NOT show the Target, so that a blocked destination isn't advertised anyway.
25. As a Visitor, I want a Report button on the Interstitial, so that I can flag a suspicious Link at the moment I encounter it.
26. As a Visitor, I want to file a Report without logging in, so that reporting has no barrier.
27. As a Visitor, I want confirmation that my Report was received, so that I don't submit it repeatedly.
28. As a Visitor with JavaScript disabled, I want a visible link to the Target, so that the address still works.

### Link sharing

29. As someone pasting a short address into KakaoTalk, I want the preview card to show the Target's own title and image, so that recipients can see what they're being sent.
30. As an Operator, I want a Suspended Link to show no preview of its Target to crawlers either, so that Suspension is not trivially bypassed.

### Operator

31. As an Operator, I want to log in with a username and password, so that I can administer the service without tying it to a Kakao account.
32. As an Operator, I want the admin login on a path that isn't linked from the public site, so that bots don't find the form by crawling.
33. As an Operator, I want my account locked for a period after repeated failed logins, so that the password can't be brute-forced.
34. As an Operator, I want to see all Reports with the reported Link's Code and Target, so that I can judge whether to act.
35. As an Operator, I want to see how many Reports a Link has received, so that I can prioritise.
36. As an Operator, I want to Suspend a Link from the report list, so that acting on a Report takes one click.
37. As an Operator, I want to Suspend a Link that was never reported, so that I can act on something I found myself.
38. As an Operator, I want to lift a Suspension, so that I can undo a mistake.
39. As an Operator, I want a Link auto-Suspended once Reports arrive from several distinct sources, so that an obvious abuse case is stopped while I'm away.
40. As an Operator, I want to mark a Report as handled, so that the list shows what still needs attention.
41. As an Operator, I want to be unable to create Links as the Operator account, so that administration and ownership stay separate.

### Developer

42. As a developer, I want sample Owners seeded locally that I can log into with a username and password, so that I can exercise Owner flows without going through Kakao.
43. As a developer, I want those sample accounts impossible to create in production, so that they can never become a way in.
44. As a developer, I want the test suite to run against the same PostgreSQL major version as production, so that a green suite means something.
45. As a developer, I want one command to bring up a local database, so that setup is not a ritual.

## Implementation Decisions

### Deployment shape

- Two deployment targets on one registrable domain, per **ADR 0001**: `to2.at` is a Cloudflare Worker serving the SPA's static assets, `api.to2.at` is the Spring application on Railway. No Worker-to-Railway proxying.
- Browser calls to the API host are cross-origin but same-site. CORS allows exactly the site origin, with credentials. The session cookie is `SameSite=Lax` — **not `Strict`**, which would drop the cookie on the Kakao callback navigation and break login entirely.
- The Spring app serves from the root of its own host, so no `/api` context path and no OAuth base-URI overrides are needed.

### Short code resolution and the Worker

- The Worker must see the Code path itself. Cloudflare's SPA fallback serves the shell for browser navigations without invoking the Worker, so the assets config runs the Worker first for everything except the built asset directory, which keeps bundle serving free.
- Worker dispatch order: a path matching the Code pattern (digits only) → Code handling; everything else → static assets.
- Code handling branches on whether the request is a link-preview crawler, detected by a User-Agent substring match. Crawlers get a 302 to the Target. Humans get a small hand-written HTML Interstitial rendered by the Worker — not the SPA — so it paints without waiting for a bundle.
- The Interstitial moves the Visitor with a history-replacing navigation, never a history-pushing one, so it does not enter session history (story 22).
- Per **ADR 0004**: a Suspended Link returns the block page to humans **and** must not 302 crawlers. The Interstitial must not carry an Open Graph canonical URL differing from the requested URL, or Kakao will re-scrape that other address.

### Code allocation

- Per **ADR 0002**, a Code is a Link's primary key rendered in decimal, drawn from a database sequence.
- **Sequence allocation size is 1**, deliberately overriding the ORM default of 50. Allocation size 50 leaves gaps whenever the app restarts mid-block, and redeploys are frequent. One extra round trip per Link creation is irrelevant at this volume, and consecutiveness is the point of the scheme.
- Code rendering and parsing live behind a single function so the scheme can be swapped later without touching the schema.
- Deletion is a state change, never a row removal, so a Code is never reissued (story 13).

### Authentication

- Per **ADR 0005**, two filter chains. The admin chain carries a path matcher for the hidden admin path and enables form login only. The public chain has no matcher, is registered last, and enables OIDC login only. Merging them would make behaviour depend on configuration call order, because a single registered OAuth2 client causes the entry point to redirect straight to the provider.
- Kakao is configured through OIDC discovery with an issuer URI. **The client authentication method must not be set explicitly** — Kakao advertises only one, discovery selects it, and an explicit value silently breaks token exchange. Scopes are Kakao's own consent identifiers, not the standard OIDC names.
- Kakao's OIDC subject is the Owner's immutable identity. Email is optional even on a Biz app, is stored nullable, and is trusted only when Kakao marks it verified.
- Lockout is implemented against authentication failure events plus the account-non-locked flag, because the framework ships no brute-force protection. Threshold and window are configuration.
- The Operator's password originates from an environment variable only. No password, hashed or plain, is committed or placed in a migration.
- Sessions are stored in the database so redeploys don't sign Owners out (story 16). The session fixation strategy must remain the default; a strategy that discards session attributes would drop in-flight OAuth handshakes.

### Data model

- `owner` — Kakao subject (not null, unique), nickname, nullable email plus a verified flag, created timestamp.
- `link` — sequence-backed key doubling as the Code, Target, owning Owner, state (active / deleted / suspended), created and state-change timestamps.
- `local_account` — username, password hash including its algorithm prefix, nullable Owner reference, failed-attempt counter, lock expiry. The Operator row has no Owner reference; seeded developer accounts point at their Owner.
- `report` — reported Link, optional reason, reporter address, created timestamp, handled timestamp.
- Session storage uses the schema shipped by the session module.
- Per **ADR 0005**, the Kakao subject column stays not-null; developer seed Owners use synthetic subject values rather than nulls.

### API contract

Public, no authentication:

- Resolve a Code → the Target and the Link's state, or not-found. Returns JSON rather than a redirect, because a redirect cannot be read back from a cross-origin fetch.
- File a Report against a Code → accepted. Rate limited per source address.

Owner, authenticated:

- Current Owner's profile.
- Create a Link from a Target.
- List the current Owner's Links.
- Delete one of the current Owner's Links.

Operator, authenticated on the admin chain:

- List Reports with their Link and report counts.
- Suspend a Link, lift a Suspension, mark a Report handled.

Owner endpoints reject access to another Owner's Link with not-found rather than forbidden, so Link existence isn't disclosed.

### Target validation

- Only `http` and `https` schemes are accepted.
- A Target whose host is the service's own domain is rejected, preventing loops (story 8).
- No deduplication: the same Target submitted twice yields two Links. Simpler, and the two may be shared in different places.

### Seeding

- Developer sample accounts are created by a runner active only under the development profile, because hashing a password requires the password encoder. They are never written by a migration.
- Any development-only migrations live outside the production migration tree and in a disjoint version range, so production validation is unaffected.

### Build and deployment

- The backend builds from a Dockerfile rather than the platform's default builder, which ignores the Gradle toolchain and would build against the wrong JDK.
- The PostgreSQL service is pinned to an explicit major version; the floating tag resolves to an older major.
- The injected database URL is a libpq URI the framework cannot parse; the datasource URL is assembled from the individual connection variables.
- Monorepo: each deployment target sets its own root directory and watch paths, so a frontend change does not redeploy the backend — which would sign every Owner out.

## Testing Decisions

### What makes a good test here

A test asserts **externally observable behaviour** — the status, body, headers and database effect of a request — never which class was called or how a query was written.

Behaviour is only ever *invoked* through the two seams below. A repository may appear in a test to build a fixture or to observe the database effect of a request that already went through a seam; it must never be the thing that performs the behaviour under test. (An earlier draft of this section said no test may touch a repository at all, which contradicted "database effect" in the same sentence — this is the rule that was meant.)

### Seam A — backend, through the full framework stack

Requests are driven through the framework's mock request infrastructure against a context backed by a real PostgreSQL container pinned to the production major version, per **ADR 0003**. Migrations run during context startup, so every test also exercises the production migration set. This single seam covers routing, both filter chains, authorization, validation, ORM mapping and schema simultaneously.

Owner authentication is simulated with the security test module's OIDC login support rather than by contacting Kakao. Operator authentication goes through the real form login, so the lockout path is genuinely exercised.

Covered here:

- Code resolution for an active, deleted, suspended and unknown Code.
- Link creation, including Target validation rejections and the self-referencing Target rejection.
- Consecutive Code allocation across successive creations, and that a deleted Code is never reissued.
- Listing and deleting scoped to the current Owner, including that another Owner's Link is neither visible nor deletable.
- Report submission without authentication, and auto-Suspension once Reports arrive from enough distinct sources.
- Operator-only endpoints rejecting Owner sessions and anonymous requests.
- Form-login lockout after the configured number of failures, and release after the window.
- That the admin path and the public path are governed by the chain each expects.

### Seam B — Worker, through its fetch handler

The handler is invoked with a request in the real Workers runtime; the backend is stubbed at the fetch boundary. Assertions are on the returned response.

Covered here:

- A crawler User-Agent on an active Code receives a 302 to the Target.
- A crawler User-Agent on a suspended Code does **not** receive a 302 to the Target.
- A human request on an active Code receives HTML containing the destination host.
- Unknown and suspended Codes render their respective pages, and the suspended page does not contain the Target.
- Non-Code paths fall through to static assets.

### Prior art

None — this is a new repository. These two seams establish the pattern; later work extends them rather than adding lower ones.

### Not tested

The SPA has no dedicated tests in v1. The decision-carrying logic lives in the Worker and the backend, both covered above; the dashboard is presentational and its cost of breakage is low.

## Out of Scope

- Click counting or any per-visit analytics. It would force a write on the redirect path and prevent caching the Interstitial.
- Custom Codes. They would require a permanent reserved-word list against frontend routes.
- Link expiry, editing a Link's Target, and search over Links.
- An appeal flow for a Suspension. The Operator can be contacted directly.
- Proactive malware or phishing scanning of Targets. Safety is reactive, through Report and Suspension only — so **no user-facing copy may claim a Link has been checked**.
- Notification delivery for new Reports. The Operator checks the list directly.
- Multiple Operators, roles, or an audit log of Operator actions.
- Linking a Kakao account to an existing local account, or any account merging.
- A GitHub remote and PR-based review; the repository is local for now.

## Further Notes

- Kakao caches a link preview for roughly an hour per URL with no purge API — only a manual, login-gated debugger, one URL at a time. The Interstitial's markup should be correct before the first share.
- Kakao's Open Graph and OIDC behaviour both have console prerequisites that no amount of correct code substitutes for: OpenID Connect must be switched on, and every redirect URI must be registered exactly, including the development one.
- Cloudflare's free Workers tier allows 100,000 requests a day and 10 ms CPU per request. Running the Worker first for all non-asset paths makes every page request billable, which is ample here but is the number to watch. Exceeding the limit returns 429 rather than falling back to asset serving.
- The Interstitial's minimum dwell is a product decision, not a technical one; it exists so the destination can actually be read.

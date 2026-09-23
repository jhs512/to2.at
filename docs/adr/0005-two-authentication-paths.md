# 인증을 두 갈래로 둔다: Owner는 카카오, Operator는 아이디/비번

Owner(회원)는 카카오 OIDC로만 로그인한다. Operator(관리자)는 아이디/비밀번호로 로그인하며, 운영에도 존재한다. 원래 요구사항은 "스프링 시큐리티 OAuth2만으로"였고, Operator도 카카오 계정으로 지정하면 운영에 비밀번호 경로를 0개로 유지할 수 있다는 대안이 있었으나, 운영 주체가 비밀번호 로그인을 선택했다.

**그래서 `SecurityFilterChain`을 경로별로 두 개로 나눈다.** 하나로 합치면 안 되는 구체적인 이유가 있다: 등록된 OAuth2 클라이언트가 카카오 하나뿐이면 `OAuth2LoginConfigurer`가 진입점을 `/login`이 아니라 `/oauth2/authorization/kakao`로 직행시키고, 어느 진입점이 이기는지가 **DSL 호출 순서**로 정해진다. `oauth2Login{}`을 `formLogin{}`보다 먼저 쓰면 비밀번호 폼을 영영 볼 수 없다. 체인을 나누면 이 순서 의존이 사라지고, 공개 경로에서는 카카오 직행이 오히려 원하는 동작이 된다.

- 관리자 전용 체인: 숨김 경로에 `securityMatcher`, form login만
- 공개 체인: 매처 없이 마지막, `oauth2Login`만

## Consequences

- Spring Security에는 브루트포스 차단·계정 잠금이 **없다.** `AbstractAuthenticationFailureEvent`로 실패를 세고 `UserDetails.isAccountNonLocked()`로 막는 잠금을 직접 넣는다(실패 5회 → 15분).
- 관리자 비밀번호는 **환경변수에서만** 온다. 마이그레이션이나 코드에 평문·해시 어느 쪽도 넣지 않는다.
- 샘플 회원(`user1`~`user3`)은 **개발 전용**이며 운영 DB에 존재해서는 안 된다. 개발 전용 Flyway 경로는 마이그레이션 폴더 **밑이 아닌 별도 루트**에 두고 `V9000+` 번호대를 쓴다. 비밀번호가 들어가는 시드는 `PasswordEncoder`에 접근할 수 있는 `@Profile("dev")` `ApplicationRunner`로만 만든다.
- Operator는 Owner가 아니므로 `owner.kakao_sub`는 NOT NULL을 유지한다. 개발 시드 Owner는 `dev:user1` 같은 합성 값을 쓴다.

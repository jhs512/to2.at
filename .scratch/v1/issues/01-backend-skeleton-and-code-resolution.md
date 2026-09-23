# 01: 백엔드 뼈대와 Code 해석

**What to build:** Code 하나를 주면 그 Link의 Target과 상태를 돌려주는 공개 API가 선다. 저장소에 코드가 한 줄도 없으므로 이 티켓이 백엔드 뼈대와 테스트 seam을 함께 세운다. 끝나면 시드된 Link 하나를 명령줄에서 해석해 볼 수 있다.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] 명령 하나로 로컬 PostgreSQL 18이 뜨고 애플리케이션이 기동한다
- [x] 마이그레이션이 `owner`와 `link` 테이블을 만든다. `owner`의 카카오 주체 컬럼은 not-null·unique이고, `link`는 시퀀스에서 키를 받으며 상태 컬럼을 갖는다
- [x] 활성 Code를 해석하면 Target과 활성 상태가 JSON으로 온다 (리다이렉트가 아니라 JSON — 교차 출처 fetch에서 읽을 수 있어야 한다)
- [x] 존재하지 않는 Code는 not-found로 응답한다
- [x] 삭제 상태인 Code는 Target 없이 상태만 응답한다
- [x] 해석 엔드포인트는 인증 없이 열려 있다
- [x] Seam A가 선다: 운영과 같은 메이저 버전의 실제 PostgreSQL 컨테이너 위에서 전체 프레임워크 스택을 통과하는 요청으로 테스트하고, 마이그레이션은 컨텍스트 기동 중 실행된다
- [x] 동작은 오직 seam을 통해서만 호출된다. 리포지토리는 픽스처를 만들거나 요청이 남긴 DB 효과를 확인할 때만 등장하고, 검증 대상 동작을 직접 수행하지 않는다
- [x] Code를 문자열로 만들고 되읽는 변환이 한 군데 함수에 모여 있다 (ADR 0002)

# 06: Link 목록과 삭제

**What to build:** Owner가 자기가 만든 Link만 모아 보고, 필요 없어진 것을 지운다. 지운 Code는 다시 쓰이지 않는다.

**Blocked by:** 05 (지울 Link가 있어야 한다)

**Status:** done

- [x] 내가 만든 Link가 최신순으로 보인다
- [x] 각 줄에 Code, Target, 생성 시각이 보인다
- [x] 삭제하면 그 Code는 더 이상 Target으로 해석되지 않는다
- [x] **삭제는 행 제거가 아니라 상태 변경이다.** 그래서 지운 Code가 다른 Link에 재발급되지 않는다 — 이미 공유된 주소가 엉뚱한 곳을 가리키는 일을 막는다
- [x] 다른 Owner의 Link는 목록에 나타나지 않는다
- [x] 다른 Owner의 Link를 지우려 하면 **not-found로 응답한다** (forbidden이 아니라 — Link의 존재 자체를 알리지 않는다)
- [x] 로그인하지 않은 요청은 목록을 볼 수 없다

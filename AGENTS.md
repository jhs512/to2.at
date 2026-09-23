# url-shorts

## Agent skills

### Issue tracker

Issues live as markdown files under `.scratch/<feature-slug>/` in this repo. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, used verbatim as `Status:` values. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` at the repo root plus `docs/adr/`. See `docs/agents/domain.md`.

## 개발 워크플로

기능 하나를 **한 사이클**로 돌린다. 사이클을 건너뛰지 않는다 — 특히 설계 합의 없이 바로 구현으로 가지 않는다.

```
/grill-with-docs  →  /to-spec  →  /to-tickets  →  /implement  →  (반복)
```

### 1. `/grill-with-docs` — 설계 합의

설계를 캐물어 합의에 도달한다. 사실 확인은 사람에게 묻지 말고 서브에이전트로 직접 찾는다. 합의된 용어는 그 자리에서 `CONTEXT.md`에 기록하고, 되돌리기 어렵고·놀랍고·실제 트레이드오프가 있었던 결정만 `docs/adr/`에 남긴다. 사용자가 합의를 확인하기 전에는 구현에 손대지 않는다.

### 2. `/to-spec` — 스펙 발행

대화를 스펙으로 합성한다(재인터뷰 금지). 테스트 seam을 먼저 스케치해 사용자와 맞춘 뒤, `.scratch/<feature-slug>/spec.md`로 발행하고 `Status: ready-for-agent`를 단다.

### 3. `/to-tickets` — 티켓 분해

스펙을 **수직 슬라이스**(스키마·API·UI·테스트를 관통하는 얇고 완결된 경로)로 쪼갠다. 각 티켓은 `.scratch/<feature-slug>/issues/NN-<slug>.md`에 한 파일씩, 맨 위에 `Blocked by: NN, NN`을 명시한다. 티켓은 단계 목록이 아니라 **작업 그래프**다.

### 4. `/implement` — 구현

가능한 곳에서 `/tdd`를 쓰고, 타입체크와 단위 테스트는 자주·전체 테스트는 마지막에 한 번 돌린다. 끝나면 `/code-review`를 거쳐 현재 브랜치에 커밋한다.

### 이 저장소에서의 주의점

- **`/implement-spec`은 이 저장소에 맞지 않는다.** 브랜치와 draft PR을 전제로 하는데 이 저장소에는 GitHub 리모트가 없고 이슈 트래커도 로컬 마크다운이다. 순차 구현에는 `/implement`를 쓴다.
- 트래커가 로컬 마크다운이므로 triage 라벨은 이슈 파일 상단의 `Status:` 줄로 표현된다. 자세한 것은 `docs/agents/issue-tracker.md`.
- 스펙·티켓·커밋 메시지는 `CONTEXT.md`의 용어를 쓴다. Link / Code / Target / Owner / Visitor / Interstitial / Report / Suspension / Operator.
- 작업하려는 영역에 걸린 ADR을 먼저 읽고, 결과가 ADR과 충돌하면 조용히 덮지 말고 드러낸다.

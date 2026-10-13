Blocked by: none
Status: ready-for-agent

# Name 저장부터 Owner 목록 편집까지 연결한다

nullable DB migration·POST/PATCH 계약·Owner 응답·목록/수정 UI·검증을 수직 슬라이스로 구현한다. 스펙은 ../spec.md. 개인 스킬 자체는 변경하지 않고, 후속 요청으로 지정된 네 파일만 저장소 복사본에 동기화한다.

## Comments

### 2026-10-13 — 구현·검토·운영 반영 완료

Resolution: complete

- 구현 커밋 `0816101`, main push 완료. nullable Name 저장, 독립적인 PATCH, Owner 응답과 목록/편집 UI를 연결했다. 계약은 ../spec.md와 README.md에 기록했다. Unicode code point 100개 기준이며 이름 생략은 유지, 공백/빈 문자열/null은 제거한다. 공개 이동에는 Name을 노출하지 않는다.
- 기존 Target·Owner 권한·Code/생성 시각·Suspension/삭제 규칙과 no-store 유지. 필드만 원자적으로 갱신하는 SQL을 사용해 생략한 필드와 moderation 상태를 덮어쓰지 않는다. requestId 재시도는 현재 Name을 반환하고 덮어쓰지 않는다.
- 백엔드 전체 101개, frontend 35개, CLI 12개 테스트 통과. typecheck/build 및 lint 통과(기존 Fast Refresh 경고 3개). Standards/Spec 리뷰 각각 발견 사항 0건. quick_validate 통과.
- 개인 스킬의 SKILL.md/references/api.md/scripts/to2.py/scripts/test_to2.py 네 파일만 저장소 복사본에 동기화하고 바이트 동일 확인. 자격 증명·비밀 파일은 복사하지 않았다.
- Chrome + 로컬 모의 API에서 기존 Name 표시, 수정 입력, 취소, 101자 오류 시 입력 유지, trim 후 즉시 표시, 새로고침 유지, 빈 이름 제거를 확인했다. Code와 Target은 유지됐다. 실제 운영 Link는 변경하지 않았다.
- Railway 직접 Windows 체크아웃 업로드는 Gradle wrapper CRLF 때문에 실패했다. 커밋된 소스의 배포 파일에서 wrapper만 LF로 정규화한 후 재배포 성공: `c6f0c78a-d399-4b36-8a57-859d34743d23`. 운영 로그에서 V6→V7 link name migration 적용, schema v7, 정상 기동 및 healthcheck 성공 확인.
- Cloudflare Worker `9370cc25-8ae7-440d-9553-2f4be6c9333e` 배포 완료. 운영 API_ORIGIN=https://api.to2.at, 새 UI 자산 index-SokU3dES.js/routes-BVo_Yiw5.js 반영.
- 운영 읽기 검증: health 200 UP, 공개 /to/1 200 및 Name 필드 없음, /1 일반 200·crawler 302 모두 브라우저/CDN no-store 유지. 새 정적 화면은 max-age=0,must-revalidate이며 Name 입력 코드 포함, QA localhost origin 없음.
- 운영 인증된 /links 브라우저 탐색은 차단되어 검증으로 주장하지 않는다. 담당 원본 작업이 설치된 개인 스킬의 API 키로 읽기 전용 GET /links의 Name 필드를 별도로 확인하기로 했다. Owner 목록과 쓰기 응답 계약은 실제 PostgreSQL 통합 테스트로 검증했다.

### 2026-10-13 — 운영 Owner 목록 검증 추가

원본 담당 작업이 공식 개인 스킬에 등록된 API 키로 Bearer GET /links를 읽기 전용 호출하여 검증 성공을 보고했다. 반환된 Link 3개 모두 name 필드가 존재했다(`count: 3`, `allHaveNameField: true`). 비밀 키와 사용자 Link는 출력하지 않았으며 실제 운영 Link도 수정하지 않았다. 위 브라우저 점검 제한과 별개로 공식 API 계약의 운영 Name 필드 확인은 완료됐다.

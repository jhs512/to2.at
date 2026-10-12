---
name: to2-at
description: 투투엣(to2.at)에서 Link를 생성·조회·삭제하거나 공개 Code와 서비스 상태를 확인할 때 사용한다.
---

# to2.at

카카오 Owner가 발급한 API 키로 Link를 관리한다. 번들 CLI는 Python 표준 라이브러리만 사용한다.

## 인증

사용자가 사이트에 카카오 로그인한 뒤 **API 키 → 새 키 발급**으로 키를 얻는다. 로컬 대화형 터미널에서 `python scripts/to2.py auth-set`으로 숨김 입력해 한 번 등록한다. 등록된 키는 이후 명령에서 자동으로 사용한다. Windows에서는 현재 사용자에 묶인 DPAPI 암호화 저장소를 사용한다.

원문 키는 최초 발급 화면에서만 확인할 수 있다. 사용자에게 키를 채팅으로 보내도록 요구하지 않는다. 안전하게 준비된 `TO2_API_KEY` 또는 `TO2_API_KEY_FILE`도 지원한다. 실행 경로는 이 스킬 폴더 기준이다. 인증 계약 및 오류 판단은 [references/api.md](references/api.md)를 읽는다.

## 실행

`python scripts/to2.py --help`로 명령을 확인한다.

- `health`: API 상태 확인.
- `resolve <Code>`: 공개 Link 상태와 ACTIVE Target 조회.
- `me`, `list`: 키에 연결된 Owner와 그 Owner의 Link 목록 조회.
- `create <Target>`: Link 생성 후 반환된 `shortUrl`을 사용자에게 제공.
- `delete <Code>`: 해당 Owner의 Link를 DELETED 상태로 변경.
- `auth-status`: 로컬 키 설정 여부 확인. 유효성은 `me`로 확인.
- `auth-clear`: 로컬 저장본 제거. 서버 키 폐기는 사이트에서 한다.

출력은 JSON이며 실패 시 종료 코드가 0이 아니다. 공개 호출에는 키를 보내지 않는다. 인증 실패 시 서버 배포 여부와 키 폐기 여부를 확인하며 브라우저 쿠키를 추출하지 않는다.

## 작업 경계

사용자가 요청한 Link만 생성하거나 삭제한다. 같은 Target을 두 번 생성하면 서로 다른 Link가 생긴다. 생성 요청이 시간 초과 또는 서버 오류로 끝나면 결과가 불명확하므로 목록에서 확인하고, 사용자의 새 생성 의도 없이 반복하지 않는다.

삭제는 복구 API가 없는 상태 변경이며 Code가 재사용되지 않는다. Report는 Suspension을 일으킬 수 있어 CLI 지원 범위에서 제외한다. 키 관리와 Operator 작업도 CLI로 하지 않는다.

엄격한 읽기 전용 운영 점검은 `health`, 공개 `resolve`, 홈페이지 및 Interstitial을 사용한다. 카카오 세션의 /me와 /links는 첫 Owner를 생성할 수 있다. 검증하지 않은 기능과 운영 미배포 기능은 결과에서 구분한다.

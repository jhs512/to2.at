---
name: to2-at
description: 투투엣(to2.at) 단축주소를 생성하고 이름을 저장하거나 기존 링크의 대상 URL·이름을 수정하고, 링크 목록·상태를 조회할 때 사용한다.
---

# to2.at

카카오 Owner가 발급한 API 키로 Link를 관리한다. 번들 CLI는 Python 표준 라이브러리만 사용한다.

## 인증

사용자가 사이트에 카카오 로그인한 뒤 **API 키 → 새 키 발급**으로 키를 얻는다. 로컬 대화형 터미널에서 `python scripts/to2.py auth-set`으로 숨김 입력해 한 번 등록한다. 등록된 키는 이후 명령에서 자동으로 사용한다. Windows에서는 현재 사용자에 묶인 DPAPI 암호화 저장소를 사용한다.

원문 키는 최초 발급 화면에서만 확인할 수 있다. 사용자에게 키를 채팅으로 보내도록 요구하지 않는다. 안전하게 준비된 `TO2_API_KEY` 또는 `TO2_API_KEY_FILE`도 지원한다. 실행 경로는 이 스킬 폴더 기준이다. 인증 계약 및 오류 판단은 [references/api.md](references/api.md)를 읽는다.

## 실행

`python -X utf8 scripts/to2.py --help`로 명령을 확인한다. Windows에서도 한국어 응답을 UTF-8로 출력하려면 `-X utf8`을 사용한다.

- `health`: API 상태 확인.
- `resolve <Code>`: 공개 Link 상태와 ACTIVE Target 조회.
- `me`, `list`: 키에 연결된 Owner와 그 Owner의 Link 목록 조회.
- `create <Target> [--name <이름>]`: 이름과 함께 Link 생성 후 반환된 `shortUrl`을 사용자에게 제공.
- `update <Code 또는 https://to2.at/Code> <Target> [--name <이름>]`: 단축주소를 유지하고 대상 URL 수정. 이름 옵션을 생략하면 기존 이름을 유지. 공개 `resolve`로 대상 반영을 확인.
- `rename <Code 또는 https://to2.at/Code> <이름>`: 대상 URL을 유지하고 이름만 수정. 빈 문자열은 이름 제거. `list`로 저장된 이름을 확인.
- `delete <Code>`: 해당 Owner의 Link를 DELETED 상태로 변경.
- `auth-status`: 로컬 키 설정 여부 확인. 유효성은 `me`로 확인.
- `auth-clear`: 로컬 저장본 제거. 서버 키 폐기는 사이트에서 한다.

출력은 JSON이며 실패 시 종료 코드가 0이 아니다. 공개 호출에는 키를 보내지 않는다. 인증 실패 시 서버 배포 여부와 키 폐기 여부를 확인하며 브라우저 쿠키를 추출하지 않는다.

```powershell
python -X utf8 scripts/to2.py create "https://example.com/article" --name "수업 참고자료"
python -X utf8 scripts/to2.py update "https://to2.at/123" "https://example.com/new-article"
python -X utf8 scripts/to2.py rename "https://to2.at/123" "수정된 수업 참고자료"
```

예시 주소는 실제 사용자 요청의 URL로 바꾼다. 이름은 링크를 기억하기 위한 메모이며 단축주소의 Code를 바꾸지 않는다. 사용자가 이름을 지정하면 함께 저장하고, 지정하지 않았으면 대화에서 용도가 명확할 때 간결한 이름을 붙인다. 생성·수정 결과는 저장된 이름과 단축주소, 변경한 내용을 간결히 알려준다.

## 작업 경계

사용자가 지정한 대상 URL로 Link를 생성하거나 수정한다. 수정할 단축주소가 없으면 `list`로 후보를 확인하고, 후보가 여러 개면 사용자가 선택하게 한다. 수정 API가 미배포라면 새 Link 생성이나 기존 Link 삭제로 대체하지 않는다.

같은 Target을 두 번 생성하면 서로 다른 Link가 생긴다. 쓰기 요청이 시간 초과 또는 서버 오류로 끝나면 목록에서 결과를 먼저 확인하고 자동으로 반복하지 않는다.

삭제는 복구 API가 없는 상태 변경이며 Code가 재사용되지 않는다. Report는 Suspension을 일으킬 수 있어 CLI 지원 범위에서 제외한다. 키 관리와 Operator 작업도 CLI로 하지 않는다.

엄격한 읽기 전용 운영 점검은 `health`, 공개 `resolve`, 홈페이지 및 Interstitial을 사용한다. 카카오 세션의 /me와 /links는 첫 Owner를 생성할 수 있다. 검증하지 않은 기능과 운영 미배포 기능은 결과에서 구분한다.

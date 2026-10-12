Status: ready-for-agent

# 외부 공유 URL로 Link를 생성하고 복사한다

사용자가 확정한 UX: Java 비주얼라이저의 일반 공유는 원래 공유 URL을 복사하며, 별도 to2.at 버튼은 로그인된 Owner로 Link를 생성하고 단축 주소를 복사한다. 브라우저에 API 키를 전달하지 않는다.

## 진입 계약

권장: `https://to2.at/?action=create-and-copy#target=${encodeURIComponent(fullShareUrl)}`.
호환: `https://to2.at/?target=${encodeURIComponent(fullShareUrl)}&action=create-and-copy`.
Target은 한 번만 디코딩하고 원본 hash·query·percent encoding을 보존한다. Cloudflare 요청 URL은 16KB 제한이므로 긴 Target은 fragment로 전달하고 API JSON 본문으로 생성한다. 기존 DB Target은 TEXT이며 애플리케이션 Target 길이 제한은 없다. 128KiB 이상의 공유 Target 왕복을 테스트한다.

## 동작

- Target과 생성 요청 식별자를 로컬 저장소에 보관하고 주소를 짧은 `/?action=create-and-copy&request=<uuid>`로 치환한다. 카카오 로그인 복귀는 sessionStorage에 보관한 요청을 이어받는다. 결과에는 항상 현재 로그인된 Owner의 API 응답을 사용한다.
- 같은 Target의 외부 생성 요청 식별자는 SHA-256으로부터 결정적으로 만든다. 같은 주소 재진입·새로고침·동시 탭·네트워크 재시도 시 동일 Owner의 Link를 한 번만 생성한다. 일반 POST /links는 동일 Target을 중복 생성할 수 있다.
- POST /links JSON의 선택적 `requestId`는 UUID다. 해당 Owner의 동일 requestId는 같은 Link를 반환한다(최초 201, 재사용 200). 다른 Target 또는 이미 Target이 수정된 Link에는 409. 삭제·Suspension된 기존 결과는 새 Link로 우회하지 않으며 복사하지 않는다.
- 로그인 전에는 Target을 보존한 채 카카오 로그인 버튼을 표시한다. 로그인 후 자동 생성·복사를 시도한다. clipboard 제한 시 결과 주소와 복사 버튼·선택 가능한 입력을 제공한다. 생성 실패는 자동 반복하지 않으며 재시도 버튼을 제공한다.
- 저장소를 사용할 수 없거나 입력이 잘못되면 자동 생성하지 않고 오류를 보여준다. API 키·카카오 세션 정보는 저장소에 넣지 않는다.

## 검증 seam

Owner HTTP /links 생성·재사용·소유권·동시성·상태·긴 Target 왕복, 브라우저 외부 진입/로그인 복귀/생성·복사 워크플로, Worker Target hash 보존. 로컬 브라우저에서 생성·복사 실패/새로고침 UX를 확인한다. Standards/Spec 리뷰 후 main 반영·push·백엔드 및 frontend 배포와 운영 비변경 검증을 수행한다.

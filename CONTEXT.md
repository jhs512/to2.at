# to2.at

카카오 계정으로 로그인한 사용자가 긴 URL을 짧은 주소로 바꿔 공유하는 서비스.

## Language

**Link**:
사용자가 만든 단축 항목 하나. Code와 Target과 Owner를 묶은 것.
_Avoid_: 단축 URL(그건 Link가 아니라 Link를 가리키는 주소), 숏링크

**Name**:
Owner가 Link를 기억하기 위해 붙이는 선택적인 이름. Code와 Target과는 별개이며 Visitor에게 공개하지 않는다.
_Avoid_: 제목, 슬러그, Code 이름

**Code**:
`to2.at/` 뒤에 붙어 Link 하나를 가리키는 식별자.
_Avoid_: 슬러그, 해시, 키, ID

**Target**:
Visitor가 최종적으로 도착하는 원본 URL.
_Avoid_: 원본 링크, destination, 목적지 URL

**Owner**:
Link를 만든 사용자. 카카오 계정 하나가 Owner 하나에 대응하며, Operator는 Owner가 아니다.
_Avoid_: 유저, 계정, 회원

**Visitor**:
단축 주소를 눌러 들어온 사람. Owner일 수도 있고, 로그인하지 않은 남일 수도 있다.
_Avoid_: 클라이언트, 사용자(Owner와 섞이므로)

**Interstitial**:
Visitor가 Code로 들어왔을 때 Target으로 보내기 전에 잠깐 머무는 경유 화면.
_Avoid_: 리다이렉트 페이지, 대기 페이지, 스플래시

**Report**:
어떤 Link가 불법이거나 부적절하다는 신고. 누구나 넣을 수 있고, Owner의 동의와 무관하다.
_Avoid_: 제보, 클레임, 어뷰즈 리포트

**Suspension**:
Link를 Target으로 더 이상 보내지 않는 상태. Code는 계속 그 Link의 것이고 다른 Link에 재사용되지 않는다.
_Avoid_: 삭제, 차단, 비활성화

**Operator**:
Report를 검토하고 Suspension을 결정하는 사람. Owner가 아니며 Link를 만들지 않는다. 현재는 한 명뿐이다.
_Avoid_: 관리자, 어드민, 모더레이터

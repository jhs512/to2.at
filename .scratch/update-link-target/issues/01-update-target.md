Blocked by: none
Status: ready-for-agent
Resolution: complete

# Owner의 Target 수정 API를 제공한다

PATCH API·Target 필드 전용 DB 갱신·인증/CORS·HTTP 통합 테스트를 관통한다. 검토 후 main 반영·푸시·배포하고 원문 키 없는 운영 거부 응답을 확인한다.

## Comments

구현 완료: HTTP 통합 테스트 7개 추가, 백엔드 전체 85개 및 Worker 20개 통과. 타입 검사·빌드 통과. Standards/Spec 리뷰 각각 발견 사항 없음.

2026-10-13: 구현 커밋 `6138c8c`를 main에 반영하고 origin에 푸시했다. Railway production `to2-api` 배포 `f30bfe30-f5e1-42ed-aeff-04aac2b68b7c` SUCCESS. 운영 health UP, 잘못된 Bearer PATCH 401 및 UTF-8 메시지, CORS PATCH 허용을 확인했다. 운영 Link 수정 및 개인 스킬/키 변경 없이 검증했다.

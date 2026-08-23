# GovBiz 확장·적용 안내

GovBiz는 샘플 공고 검색을 시작점으로, 실제 지원사업 데이터와 기업 맞춤 추천을 단계적으로
연결하도록 구성했습니다. 다른 지원사업 데이터 소스나 유사한 공공 정보 서비스에 적용할 때도
계층의 책임은 유지하고 도메인 계약과 adapter를 기능 단위로 교체합니다.

## 1. GovBiz 브랜드와 서비스 계약 확정

서비스명, 표시 문구, 공개 URL, API 오류 URN과 Health service 값은 배포 전에 하나의 규칙으로
확정합니다. 사용자에게 노출되는 이름과 서비스 간 계약에 쓰이는 식별자를 혼용하지 않습니다.

다음 위치는 같은 변경에서 함께 검토합니다.

- 루트·서비스별 README와 브라우저 metadata
- Frontend 화면의 브랜드·샘플 데이터 모드 표시
- Core API와 AI Service의 Health 응답·예상 값
- `application/problem+json` type URN과 서비스 간 DTO
- Docker Compose project·service·container 이름과 smoke 검증 스크립트

## 2. 샘플 공고를 실제 데이터 소스로 교체

현재 채팅 화면은 `SupportProgramRepository`를 통해 공고를 검색합니다. 화면과 UseCase는 유지하고
`FixtureSupportProgramRepository`를 Core API HTTP adapter로 교체합니다.

권장 순서:

1. 검색어, 필터, 페이징과 정렬을 포함한 검색 요청·응답 계약을 확정합니다.
2. Core API에 공고 조회 API와 Repository를 구현합니다.
3. 기업마당·K-Startup 수집 adapter가 외부 응답을 GovBiz 공고 모델로 변환하게 합니다.
4. Frontend Data Layer에 Fetch·Zod DTO 경계와 HTTP Repository를 추가합니다.
5. `app/services.ts`의 Composition Root에서 샘플 Repository 대신 HTTP Repository를 선택합니다.
6. 동일한 검색 시나리오를 fixture·HTTP 계약 테스트와 Compose smoke로 검증합니다.

## 3. SampleItem 계층 패턴 재사용

SampleItem은 실제 GovBiz 도메인이 아니라 Frontend와 Core API 계층을 연결하는 최소 패턴 예제입니다.
새 기능에서는 파일을 이름만 바꿔 복사하지 말고, 필요한 상태와 계약을 먼저 정의합니다.

```text
Frontend
  View → ViewModel → RTK Query → AppServices → UseCase → Repository

Core API
  Controller → Service → Domain
```

폼 입력과 한 번의 요청으로 끝나는 기능은 SampleItem처럼 React Hook Form과 RTK Query mutation으로
구성할 수 있습니다. 채팅처럼 여러 컴포넌트가 공유하고 오래 유지할 작업 흐름은 Redux Slice와
selector·thunk를 함께 사용합니다.

## 4. 데이터베이스와 비동기 처리 추가 시점

SampleItem은 의도적으로 비영속입니다. 실제 공고 수집, 전체 대화 이력, 사용자별 기업 정보처럼
새로고침 후에도 남아야 하는 데이터가 생길 때 Core API에 Repository와 migration을 추가합니다.
외부 API 수집, PDF 분석, 임베딩처럼 시간이 오래 걸리는 작업은 재시도·중복 방지·상태 조회 규칙을
확정한 뒤 큐와 worker를 도입합니다.

## 5. 검증 순서

기능을 확장한 뒤에는 변경한 계약에서 시작해 전체 서비스 흐름으로 넓혀 갑니다.

```bash
cd frontend && pnpm test && pnpm lint && pnpm build
cd ../backend/core-api && ./gradlew clean build --no-daemon
cd ../ai-service && uv lock --check && uv run --locked --extra dev python -m pytest
cd ../.. && ./infrastructure/scripts/verify-compose.sh
```

Docker Compose smoke는 Frontend → Core API → AI Service 연결과 장애·복구까지 검증합니다. 외부 데이터
소스를 추가하면 실제 밀어넣기 대신 고정 fixture나 mock server로 재현 가능한 시나리오를 먼저
추가합니다.

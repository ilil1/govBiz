# GovBiz

자연어로 정부지원사업을 검색하고, 공고의 출처와 마감일을 확인할 수 있는 채팅형 웹앱입니다.
현재는 외부 API 연결 전 단계로, 샘플 공고 데이터로 검색 흐름을 검증합니다.

```text
React Web
  → Spring Boot Core API
      → FastAPI AI Service
```

브라우저는 Core API만 호출합니다. AI Service는 Core API가 내부 HTTP로 호출하는 서비스이며, 이후
프로젝트의 AI·문서 처리·외부 API 연동을 붙일 위치를 보여 줍니다.

## 현재 구현: 샘플 공고 검색 채팅

브라우저에서 `/`로 진입하면 바로 GovBiz 채팅 화면이 열립니다.

```text
사용자 메시지
  → ChatPage
      → useSupportProgramChatViewModel
          → Redux Toolkit chat slice
          → RTK Query
              → SearchSupportProgramsUseCase
                  → SupportProgramRepository
                      → 샘플 공고 검색
                          → 지원사업 카드·마감일·추천 이유 표시
```

현재 화면은 다음 질문을 처리할 수 있습니다.

- `서울 AI 창업지원 사업 찾아줘`
- `현재 접수 중인 수출 지원사업 알려줘`
- `제조기업 R&D 사업을 찾아줘`

외부 API 키나 LLM은 아직 필요하지 않습니다. `FixtureSupportProgramRepository` 뒤의
Repository 계약은 이후 기업마당·K-Startup 수집 adapter로 교체할 예정입니다.

### 채팅 상태 관리

React Hook은 사이드바·DOM 참조처럼 화면과 함께 사라지는 상태를, Redux Toolkit은 메시지·검색 진행
상태처럼 공유하는 작업 흐름을, RTK Query는 서버 요청과 캐시 lifecycle을 담당합니다. 검색 중 중복
전송을 막고, 새 대화를 시작한 뒤 도착한 이전 응답은 요청 ID를 비교해 무시합니다.

현재는 대화와 검색 결과를 브라우저 메모리에 보관합니다. 실제 API 연결 전에는 요청 취소, 메시지
보관 한도·서버 저장, 검색 캐시 정책을 추가합니다. 현재 구현 평가와 구체적인 확장 원칙은
[Frontend 상태 관리 설계](frontend/README.md#상태-관리-설계와-확장-원칙)를 참고하세요.

## 포함한 기반

- React의 View → ViewModel → UseCase → Repository → HTTP DTO 흐름
- Spring Boot의 Controller → Service → Domain 흐름
- Zod와 Bean Validation을 이용한 요청·응답 계약 검증
- FastAPI 내부 Health API와 Core API의 upstream 오류 변환
- Vite 프록시를 사용하는 Docker Compose 개발 환경
- 서비스별 테스트와 정상·장애·복구를 확인하는 Compose smoke 검증

## SampleItem 예제

스타터에는 최소 수직 슬라이스 하나가 포함되어 있습니다.

```text
SampleItemPage
  → useSampleItemViewModel
  → PrepareSampleItemUseCase
  → SampleItemRepository
  → POST /api/v1/sample-items/prepare
  → SampleItemPreparationService
```

`name`은 필수이고 `category`, `note`는 선택입니다. 성공 응답은 처리 시작 전의
`READY_FOR_PROCESSING` / `NOT_STARTED` 상태만 반환합니다. 즉 실제 비동기 작업이나 영속성은
의도적으로 포함하지 않습니다. 이 예제의 ViewModel Hook과 RTK Query mutation이 연결되는 방식은
[SampleItem Hook 구조 설명](frontend/README.md#sampleitem-viewmodel-hook-이해하기)을 참고하세요.

## 빠른 시작

Docker daemon과 Docker Compose가 준비되어 있어야 합니다.

```bash
docker compose --file infrastructure/compose.yaml up --build
```

브라우저에서 [http://127.0.0.1:5173](http://127.0.0.1:5173)을 열면 GovBiz 채팅 화면을 볼 수
있습니다. 화면 상단에 `샘플 데이터 모드`가 표시되면 외부 공고 API를 아직 사용하지 않는 상태입니다.

| 주소 | 용도 |
|---|---|
| `http://127.0.0.1:5173` | React 개발 서버 |
| `http://127.0.0.1:5173/api/v1/health` | Vite 프록시를 거친 Core API Health |
| `http://127.0.0.1:5173/api/v1/health/ai-service` | Core API를 거친 AI Service Health |
| `http://127.0.0.1:8080` | Core API 직접 디버깅 |

종료와 생성된 Compose volume 정리:

```bash
docker compose --file infrastructure/compose.yaml down --volumes --remove-orphans
```

## 검증

전체 컨테이너 경로와 AI Service 장애·복구까지 확인합니다.

```bash
./infrastructure/scripts/verify-compose.sh
```

각 서비스만 빠르게 검증하는 방법은 해당 README에 있습니다.

## 저장소 구조

```text
govBiz/
├── frontend/             # React Web과 Data Layer 예제
├── backend/
│   ├── core-api/         # Spring Boot 공개 API와 업무 흐름
│   └── ai-service/       # FastAPI 내부 서비스
├── infrastructure/       # Docker Compose와 통합 smoke
└── docs/                 # 구조, 계약, 확장 안내
```

## 문서

- [아키텍처와 의존성 규칙](docs/architecture.md)
- [SampleItem HTTP 계약](docs/sample-item-contract.md)
- [새 프로젝트로 바꾸는 방법](docs/customization-guide.md)
- [Frontend 실행·구조](frontend/README.md)
- [Core API 실행·구조](backend/core-api/README.md)
- [AI Service 실행·구조](backend/ai-service/README.md)
- [Docker Compose 안내](infrastructure/README.md)

## 다음 단계

1. 샘플 공고 검색 결과와 채팅 UX를 확정합니다.
2. Core API에 같은 검색 계약을 연결합니다.
3. PostgreSQL 또는 서버 저장소에 공고를 저장합니다.
4. 기업마당·K-Startup API adapter로 샘플 Repository를 교체합니다.
5. 이후 기업정보 기반 추천과 GovClause의 PDF·조건 판정을 결합합니다.

기존 스타터 계층과 확장 방법은 [커스터마이즈 안내](docs/customization-guide.md)를 참고하세요.

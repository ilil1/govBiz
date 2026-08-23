# Base Architecture Starter

React, Spring Boot, FastAPI를 하나의 저장소에서 연결하고, 작은 기능 하나를 끝까지 구현해 볼 수 있는
재사용 가능한 스타터 프로젝트입니다.

```text
React Web
  → Spring Boot Core API
      → FastAPI AI Service
```

브라우저는 Core API만 호출합니다. AI Service는 Core API가 내부 HTTP로 호출하는 서비스이며, 이후
프로젝트의 AI·문서 처리·외부 API 연동을 붙일 위치를 보여 줍니다.

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
의도적으로 포함하지 않습니다.

## 빠른 시작

Docker daemon과 Docker Compose가 준비되어 있어야 합니다.

```bash
docker compose --file infrastructure/compose.yaml up --build
```

브라우저에서 [http://127.0.0.1:5173](http://127.0.0.1:5173)을 열면 SampleItem 화면을 볼 수
있습니다.

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
baseArchitecture/
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

## 새 프로젝트로 사용할 때

`SampleItem`은 실제 도메인으로 교체하기 위한 예시입니다. 기능을 추가할 때는 기존 예제를
복사하기보다, `SampleItem` 수직 슬라이스를 참고해 자신의 Domain·DTO·Repository·화면을 새로
만드는 방식을 권장합니다. 자세한 순서는 [커스터마이즈 안내](docs/customization-guide.md)를
참고하세요.

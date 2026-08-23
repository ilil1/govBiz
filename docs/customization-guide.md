# Base Architecture Starter 커스터마이즈 안내

## 1. 프로젝트 식별자 변경

새 프로젝트 이름을 정한 뒤 다음 식별자를 한 번에 바꿉니다.

| 현재 | 역할 |
|---|---|
| `base-architecture` | Compose 프로젝트명 |
| `base-architecture-core-api` | Core API Health service 값 |
| `base-architecture-ai-service` | AI Service Health service 값 |
| `io.basearchitecture.core` | Java package·Gradle group |
| `basearchitecture` | Dockerfile의 non-root 사용자 |
| `urn:base-architecture:*` | 공개 ProblemDetail type |

Health service 값은 `application.properties`, FastAPI Health schema, Core API의 예상 값, 테스트,
Compose smoke script에서 같은 문자열을 사용하므로 함께 변경해야 합니다.

## 2. SampleItem을 실제 기능으로 교체

다음 흐름을 기능 단위로 새로 만듭니다.

```text
frontend/src/presentation/features/sample-item/
frontend/src/domain/entities/SampleItem.ts
frontend/src/domain/repositories/SampleItemRepository.ts
frontend/src/domain/usecases/PrepareSampleItemUseCase.ts
frontend/src/data/api/sampleItemApi.ts
frontend/src/data/models/SampleItemDto.ts
frontend/src/data/repositories/SampleItemRepositoryImpl.ts

backend/core-api/src/main/java/.../domain/sample/
backend/core-api/src/main/java/.../dto/sample/
backend/core-api/src/main/java/.../controller/SampleItemPreparationController.java
backend/core-api/src/main/java/.../service/SampleItemPreparationService.java
```

권장 순서:

1. Core API Domain과 상태를 먼저 정의합니다.
2. 요청·응답 DTO와 Controller 테스트를 추가합니다.
3. Frontend Domain type, Repository port, UseCase를 만듭니다.
4. Fetch API와 Zod DTO를 추가합니다.
5. ViewModel과 화면을 연결합니다.
6. Compose smoke에 실제 POST 흐름을 추가하거나 교체합니다.
7. 기존 `sample-item`은 더 이상 참고할 필요 없을 때 제거합니다.

## 3. 데이터베이스와 비동기 처리 추가 시점

SampleItem은 의도적으로 비영속입니다. 데이터베이스가 실제로 필요해질 때 Core API에 Repository와
마이그레이션을 추가하세요. 시간이 오래 걸리는 작업이 생길 때만 큐·worker·상태 조회 API를
도입하는 편이 구조를 불필요하게 키우지 않습니다.

## 4. 검증을 유지하는 방법

각 변경 후 다음 순서로 검증합니다.

```bash
cd frontend && pnpm test && pnpm lint && pnpm build
cd ../backend/core-api && ./gradlew clean build --no-daemon
cd ../ai-service && uv lock --check && uv run --locked --extra dev python -m pytest
cd ../.. && ./infrastructure/scripts/verify-compose.sh
```

Compose smoke는 기본적으로 5173과 8080을 사용합니다. 다른 프로젝트 스택이 실행 중이면 먼저
중지하거나 Compose 포트를 조정해야 합니다.

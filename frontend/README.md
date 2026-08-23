# Base Architecture Web

React, TypeScript, Vite 기반의 프런트엔드입니다. 화면의 사용자 상호작용과 입력 상태를 Data Layer의
HTTP 구현에서 분리하는 예제를 제공합니다.

## 실행

### Docker Compose

저장소 루트에서 실행합니다.

```bash
docker compose --file infrastructure/compose.yaml up --build
```

브라우저는 `http://127.0.0.1:5173`으로 접속합니다. Compose에서는 React가 `/api` 상대 주소를
호출하고 Vite가 `core-api:8080`으로 중계합니다.

### 네이티브 개발

Java Core API를 별도 터미널에서 먼저 실행합니다.

```bash
cd backend/core-api
./gradlew bootRun
```

다른 터미널에서 실행합니다.

```bash
cd frontend
pnpm install
pnpm dev
```

기본 Core API 주소는 `http://localhost:8080`입니다. 주소가 다를 때만 `.env.example`을
`.env.local`로 복사해 `VITE_CORE_API_BASE_URL`을 변경하세요. `VITE_` 값은 브라우저에 노출되므로
비밀값을 넣으면 안 됩니다.

## 구조

```text
src/
├── presentation/features/sample-item/  # View, ViewModel, 폼 검증
├── presentation/shared/                # Core API 연결 상태 UI
├── domain/                             # Entity, Repository port, UseCase
└── data/                               # Fetch API, Zod DTO, Repository 구현
```

`SampleItemPage`는 ViewModel이 반환한 상태와 행동만 사용합니다. Fetch 호출과 API 응답 검증은
`data/`에 두며, UI 컴포넌트가 직접 수행하지 않습니다.

## 검증

```bash
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm build
```

전체 서비스 연결은 루트의 `./infrastructure/scripts/verify-compose.sh`로 확인합니다.

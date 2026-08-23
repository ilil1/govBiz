# GovBiz Web

React, TypeScript, Vite 기반의 지원사업 검색 채팅입니다. Redux Toolkit이 대화 상태를 관리하고,
ViewModel 안의 Thunk가 주입된 서비스를 직접 실행합니다. 서버 요청의 로딩·성공·실패 상태도 각
ViewModel Hook이 명시적으로 관리합니다.

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
├── app/                                # Redux Store와 AppServices 공개 facade
│   └── di/                             # Repository·UseCase·AppServices 역할별 Awilix 등록
├── presentation/features/chat/         # View, 인라인 Thunk ViewModel, Redux slice·selector
├── presentation/features/sample-item/  # 계층 연결 예제 View와 ViewModel
├── presentation/shared/                # Core API 연결 상태 UI
├── domain/                             # Entity, Repository port, UseCase
└── data/                               # Fixture·Fetch API·Zod DTO·Repository 구현
```

`ChatPage`는 `useSupportProgramChatViewModel`이 반환한 상태와 행동만 사용합니다. ViewModel은 typed
Redux hook으로 selector를 읽고, `submitMessage` 안의 Thunk가 Store에 주입된 UseCase를 직접
실행합니다. 구체 Repository 등록과 singleton 수명주기는 `app/di`의 역할별 등록 모듈에서 관리하고,
`app/services.ts`는 완성된 `AppServices`만 Store에 제공합니다.

```text
ChatPage
  → useSupportProgramChatViewModel
      → submitMessage 안의 Redux Thunk
          → AppServices facade
              → Awilix가 생성한 SearchSupportProgramsUseCase
                  → Awilix가 주입한 SupportProgramRepository
                      → chat slice 성공·실패 상태
```

사이드바 열림과 DOM 스크롤처럼 화면에만 필요한 상태는 React 로컬 hook에 남깁니다. 공고 검색
workflow는 ViewModel의 Thunk에 명시하고, 대화 상태는 Redux에 둡니다. Health와 SampleItem처럼 한
화면에서 끝나는 요청 상태는 해당 ViewModel의 React 로컬 state가 관리합니다.

## 상태 관리 설계와 확장 원칙

현재 구현은 샘플 데이터 기반 프로토타입에 필요한 상태 분리, DI, 비동기 응답 방어를 갖추고 있습니다.
같은 상태를 React Hook과 Redux 양쪽에 중복 저장하지 않고 다음 기준으로 소유권을 나눕니다.

| 소유자 | 담당 상태 | 현재 예시 |
|---|---|---|
| React Hook | 화면과 함께 사라지는 UI·요청 상태 | 사이드바, 폼, Health·SampleItem 요청 상태 |
| Redux Toolkit | 여러 컴포넌트가 공유하는 작업 흐름 | 입력 초안, 메시지, 검색 상태, 활성 요청 ID |
| 서버 저장소 | 새로고침 후에도 남아야 하는 장기 데이터 | 이후 추가할 전체 대화 이력과 공고 데이터 |

Awilix가 완성한 `AppServices`를 Store에 주입하므로 테스트에서는 plain Fake 서비스나 별도의 테스트
컨테이너를 사용할 수 있습니다. View는 typed selector와 dispatch를 직접 조립하지 않고 ViewModel
Hook이 반환한 상태와 행동만 사용합니다. Redux에는 문자열·배열·일반 객체처럼 직렬화 가능한 값만
저장합니다.

현재 채팅 흐름은 다음 안전장치를 적용합니다.

- 검색 중 중복 전송을 차단합니다.
- 새 대화를 시작한 뒤 도착한 이전 성공·실패 응답은 `requestId`가 다르면 무시합니다.
- 내부 예외를 그대로 노출하지 않고 안전한 사용자 오류로 변환합니다.
- 성공, 중복 요청, 늦은 응답, 오류 흐름을 ViewModel Thunk → 주입 서비스 → Slice 통합 테스트로
  검증합니다.

실제 API와 장기 대화를 연결하기 전에는 다음 항목을 보완합니다.

1. Fixture Repository를 HTTP Repository로 교체해도 ViewModel Thunk와 UseCase 호출 순서를
   유지합니다.
2. 새 대화 시작과 화면 이탈 시 진행 중인 HTTP·LLM 요청을 `AbortSignal`로 취소합니다.
3. 전체 채팅 이력은 서버에 저장하고 Redux에는 현재 대화의 최근 메시지만 보관합니다.
4. 메시지마다 공고 객체 전체를 반복 저장하지 않고, 데이터 규모에 맞춰 공고 ID·요약 또는 정규화된
   상태를 저장합니다.
5. 요청 취소·입력 검증·네트워크·서버 오류를 구분할 안전한 오류 코드와 개발용 관측 정보를
   추가합니다.

따라서 현재 Redux 구조를 다시 만드는 것이 아니라, 실제 API 도입 시 캐시·취소·장기 보관 정책을
추가하는 방향으로 확장합니다. 더 넓은 계층 규칙은 [아키텍처 문서](../docs/architecture.md#frontend)를
참고하세요.

## Awilix DI 컨테이너 이해하기

DI 라이브러리를 사용해도 구현체 등록 자체는 필요합니다. 달라진 점은 `new`와 함수 `bind`를 직접
연결하는 대신 Awilix가 등록 정보를 바탕으로 객체 생성, 의존성 해석과 수명주기를 관리한다는 것입니다.

```text
createAppContainer()
  → registerRepositories()
  → registerUseCases()
  → registerAppServices()
  → PROXY 방식으로 의존성 해석
  → 앱 컨테이너 안에서 singleton 재사용
  → appServices facade 한 번 resolve
  → createAppStore(appServices)
```

컨테이너 조립과 등록은 `app/di`에만 존재하고, `app/services.ts`는 공개 facade 역할만 합니다.
ViewModel과 Domain은 Awilix를 import하거나 `container.resolve()`를 호출하지 않습니다. 따라서 DI
라이브러리를 다른 구현으로 바꾸더라도 화면·UseCase·Repository 계약은 영향을 받지 않습니다.

등록 책임은 다음과 같이 분리합니다.

- `registerRepositories.ts`: Data Layer Repository 구현체
- `registerUseCases.ts`: Domain UseCase와 Repository 연결
- `registerAppServices.ts`: 외부 서비스와 Redux Thunk용 `AppServices` facade
- `container.ts`: 위 등록 모듈을 하나의 컨테이너로 조립

현재 정책은 다음과 같습니다.

- 브라우저 minification에 안전하도록 `InjectionMode.PROXY`를 사용합니다.
- 수명주기 오류를 조기에 찾도록 Awilix `strict` 모드를 사용합니다.
- 상태가 없는 Repository·UseCase는 앱 컨테이너 안에서 singleton으로 관리합니다.
- 요청별 `AbortController`, 폼 상태와 Redux Store는 컨테이너에 등록하지 않습니다.
- 테스트는 전역 컨테이너를 수정하지 않고 매번 새 컨테이너 또는 plain `AppServices` Fake를 사용합니다.
- 현재는 정리할 외부 자원이 없으며, WebSocket·Worker 같은 disposable 서비스를 추가할 때는 컨테이너
  handle을 앱 수명 동안 보관하고 종료 시 `container.dispose()`를 호출합니다.

Awilix의 브라우저 지원·주입 방식·수명주기 규칙은
[공식 문서](https://github.com/jeffijoe/awilix#readme)를 참고하세요.

## SampleItem ViewModel Hook 이해하기

`SampleItem`은 한 페이지의 입력 폼과 한 번의 서버 요청을 ViewModel Hook으로 묶는 예제입니다.

```text
SampleItemPage
  → useSampleItemViewModel
      ├─ useForm
      │    └─ 입력값과 입력 오류 관리
      ├─ React local state
      │    └─ 로딩·성공·실패 상태 관리
      └─ prepare 안의 Redux Thunk
           └─ AppServices.prepareSampleItem 실행
```

View는 `useForm`, Thunk, 요청 상태를 직접 조립하지 않고 ViewModel이 반환한 값만 사용합니다.

```tsx
function SampleItemPage() {
  const viewModel = useSampleItemViewModel()

  return (
    <form onSubmit={viewModel.prepare}>
      <input {...viewModel.registerField('name')} />
      <button disabled={!viewModel.isReady}>준비 요청</button>
      {viewModel.isPreparing ? <p>요청 중...</p> : null}
      {viewModel.preparation ? <p>요청 성공</p> : null}
    </form>
  )
}
```

요청을 보내는 핵심 코드는 ViewModel 안에 직접 보입니다.

```ts
const preparationWorkflow: AppThunk<Promise<SampleItemPreparation>> = (
  _dispatch,
  _getState,
  appServices,
) => appServices.prepareSampleItem({ item })

const result = await dispatch(preparationWorkflow)
```

- `dispatch(preparationWorkflow)`가 Store의 Thunk middleware를 통과합니다.
- Store에 주입된 같은 `AppServices` 객체가 세 번째 인자 `appServices`로 전달됩니다.
- `appServices.prepareSampleItem(...)`이 UseCase와 Repository를 거쳐 Core API를 호출합니다.
- 성공 결과, 로딩 여부와 안전한 오류 메시지는 ViewModel의 React state에 저장됩니다.
- 입력이 바뀌면 이전 결과를 지우고, 늦게 도착한 과거 응답은 요청 ID가 다르면 무시합니다.

ViewModel이 Repository를 직접 생성하는 것은 아닙니다. 실제 Repository와 UseCase는
`app/di`의 Awilix 컨테이너에서 한 번 해석되고, `app/services.ts`를 거쳐 `app/store.ts`가 완성된
facade를 Thunk의 `extraArgument`로 주입합니다. 따라서 운영 Store에는 실제 구현을, 테스트 Store에는
Fake 구현을 넣을 수 있습니다.

```text
사용자 입력
  → useForm이 값과 오류 관리
  → 제출
  → ViewModel이 local state를 pending으로 변경
  → ViewModel 안의 Thunk 실행
  → Store가 주입한 AppServices의 UseCase·Repository 호출
  → ViewModel이 성공 또는 실패를 local state에 저장
  → ViewModel이 화면용 상태로 변환
  → React가 다시 렌더링
```

이 패턴은 기업정보 입력·수정처럼 현재 페이지에서 끝나는 폼에 적합합니다. 채팅 메시지처럼 여러
컴포넌트가 공유하고 오래 이어지는 작업은 Redux Slice가 담당하고, 각 페이지의 입력·포커스·DOM 상태는
React Hook에 둡니다. 따라서 SampleItem 방식과 Redux 방식은 둘 중 하나만 고르는 관계가 아니라 함께
사용하는 관계입니다.

## 검증

```bash
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm build
```

전체 서비스 연결은 루트의 `./infrastructure/scripts/verify-compose.sh`로 확인합니다.

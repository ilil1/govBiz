# GovBiz Web

React, TypeScript, Vite 기반의 지원사업 검색 채팅입니다. Redux Toolkit이 대화 상태를 관리하고,
각 ViewModel Hook은 GetIt과 비슷한 전역 Awilix Service Locator에서 필요한 UseCase나 외부 API 기능을
직접 조회합니다. 서버 요청의 로딩·성공·실패 상태도 각 ViewModel Hook이 명시적으로 관리합니다.

## 실행

### Docker Compose

저장소 루트에서 실행합니다.

```bash
docker compose --env-file .env --file infrastructure/compose.yaml up --build
```

브라우저는 `http://127.0.0.1:5173`으로 접속합니다. Compose에서는 React가 `/api` 상대 주소를
호출하고 Vite가 `core-api:8080`으로 중계합니다.

### 네이티브 개발

Kotlin Core API를 별도 터미널에서 먼저 실행합니다.

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
├── app/                                # Redux Store와 앱 단위 Awilix Service Locator
│   └── di/                             # Repository·UseCase·외부 서비스 역할별 Awilix 등록
├── presentation/features/chat/         # View, 인라인 Thunk ViewModel, Redux slice·selector
├── presentation/features/sample-item/  # React Hook·Redux 비교 View, ViewModel, slice
├── presentation/shared/                # Core API 연결 상태 UI
├── domain/                             # Entity, Repository port, UseCase
└── data/                               # Fetch API·Zod DTO·HTTP Repository와 테스트 fixture
```

`ChatPage`는 `useSupportProgramChatViewModel`이 반환한 상태와 행동만 사용합니다. ViewModel은 typed
Redux hook으로 selector를 읽고, `appContainer.resolve('searchSupportProgramsUseCase')`로 검색 UseCase를
직접 조회합니다. `submitMessage` 안의 Thunk는 그 UseCase를 실행하고 Redux 상태 전이를 제어합니다.
구체 Repository 등록과 singleton 수명주기는 `app/di`의 역할별 등록 모듈에서 관리합니다.

```text
ChatPage
  → useSupportProgramChatViewModel
      → appContainer.resolve('searchSupportProgramsUseCase')
      → submitMessage 안의 Redux Thunk
          → Awilix가 생성한 SearchSupportProgramsUseCase
              → Awilix가 주입한 SupportProgramRepository
                  → Core API Fetch·Zod 응답 검증
                      → chat slice 성공·실패 상태
```

사이드바 열림과 DOM 스크롤처럼 화면에만 필요한 상태는 React 로컬 hook에 남깁니다. 공고 검색
workflow는 ViewModel의 Thunk에 명시하고, 대화 상태는 Redux에 둡니다. Health와 React Hook
SampleItem 요청 상태는 해당 ViewModel의 로컬 state가 관리합니다. 비교용 Redux SampleItem은 같은
요청 상태를 Store에 두어 상태 수명의 차이를 직접 보여 줍니다.

## 상태 관리 설계와 확장 원칙

현재 구현은 실제 Core API 검색에 필요한 상태 분리, DI, 런타임 응답 검증과 비동기 응답 방어를
갖추고 있습니다. 같은 상태를 React Hook과 Redux 양쪽에 중복 저장하지 않고 다음 기준으로 소유권을
나눕니다.

| 소유자 | 담당 상태 | 현재 예시 |
|---|---|---|
| React Hook | 화면과 함께 사라지는 UI·요청 상태 | 사이드바, Health, Hook SampleItem |
| Redux Toolkit | 여러 화면에서 유지할 작업 흐름 | 채팅과 Redux SampleItem 입력·요청·결과 |
| 서버 저장소 | 새로고침 후에도 남아야 하는 장기 데이터 | 이후 추가할 전체 대화 이력과 공고 데이터 |

운영 ViewModel은 전역 `appContainer`의 singleton을 사용하고, 테스트는 Hook의 선택적 의존성 인자에
plain Fake UseCase를 전달하거나 새 테스트 컨테이너를 만듭니다. 전역 컨테이너를 테스트에서 덮어쓰지
않으므로 테스트 간 singleton 오염을 막습니다. Redux에는 문자열·배열·일반 객체처럼 직렬화 가능한
값만 저장합니다.

현재 채팅 흐름은 다음 안전장치를 적용합니다.

- 검색 중 중복 전송을 차단합니다.
- 새 대화 시작과 화면 이탈 시 진행 중인 Fetch를 `AbortController`로 취소합니다.
- 새 대화를 시작한 뒤 도착한 이전 성공·실패 응답은 `requestId`가 다르면 무시합니다.
- 내부 예외를 그대로 노출하지 않고 안전한 사용자 오류로 변환합니다.
- 성공, 중복 요청, 늦은 응답, 오류 흐름을 ViewModel Thunk → resolved UseCase → Slice 통합 테스트로
  검증합니다.

운영 환경과 장기 대화를 연결하기 전에는 다음 항목을 보완합니다.

1. 전체 채팅 이력은 서버에 저장하고 Redux에는 현재 대화의 최근 메시지만 보관합니다.
2. 메시지마다 공고 객체 전체를 반복 저장하지 않고, 데이터 규모에 맞춰 공고 ID·요약 또는 정규화된
   상태를 저장합니다.
3. 요청 취소·입력 검증·네트워크·서버 오류를 구분할 안전한 오류 코드와 개발용 관측 정보를
   추가합니다.

따라서 현재 Redux 구조를 다시 만드는 것이 아니라, 운영 확장 시 서버 캐시와 장기 보관 정책을
추가하는 방향으로 확장합니다. 더 넓은 계층 규칙은 [아키텍처 문서](../docs/architecture.md#frontend)를
참고하세요.

## Redux Provider와 Service Locator에서 ViewModel까지 전달

`useSupportProgramChatViewModel`이 Store를 직접 import하지 않아도 `dispatch`와 State를 사용할 수
있는 이유는 React 컴포넌트 트리의 상위에 Redux `Provider`가 있기 때문입니다. UseCase는 React
Context가 아니라 `app/appContainer.ts`가 한 번 생성한 전역 Awilix 컨테이너에서 조회합니다.

```text
Provider store={store}
└─ App
   └─ ChatPage → useSupportProgramChatViewModel
```

현재 `App`은 ChatPage를 첫 화면으로 렌더링하고, 상태관리 비교 화면에서 React Hook 또는 Redux
SampleItemPage로 전환합니다. 세 화면 모두 같은 `Provider` 아래에 있어 Redux 예제의 입력과 완료
결과는 화면 이동 후에도 유지됩니다.

`main.tsx`에는 Redux Store Provider만 남습니다.

```tsx
const store = createAppStore()

<Provider store={store}>
  <App />
</Provider>
```

Provider는 Store를 React Context에 보관합니다. `ChatPage`가 렌더링되는 동안 실행되는 ViewModel
Hook은 같은 Context에서 Store를 찾을 수 있습니다. `app/hooks.ts`는 React Redux Hook에 GovBiz
Store 타입만 미리 적용합니다.

```ts
export const useAppDispatch =
  useDispatch.withTypes<AppDispatch>()

export const useAppSelector =
  useSelector.withTypes<RootState>()
```

`withTypes()`는 Store를 새로 만들거나 전역 변수를 조회하지 않습니다. 런타임 동작은 기존
`useDispatch()`·`useSelector()`와 같고, TypeScript가 GovBiz의 `AppDispatch`와 `RootState`를 알게
합니다. 동작을 단순화하면 다음과 같습니다.

```ts
function useDispatch() {
  const store = useContext(ReactReduxContext)
  return store.dispatch
}

function useSelector(selector) {
  const store = useContext(ReactReduxContext)
  return selector(store.getState())
}
```

실제 `useSelector`는 Store 변경을 구독하고, 선택한 값이 달라질 때 컴포넌트를 다시 렌더링하는 로직도
포함합니다. 따라서 ViewModel의 다음 코드는 Provider가 제공한 같은 Store를 사용합니다.

```ts
const dispatch = useAppDispatch()
const draft = useAppSelector(selectChatDraft)
const messages = useAppSelector(selectChatMessages)
```

`dispatch`는 `store.dispatch`이고, selector는 `store.getState()`에서 필요한 값을 선택합니다.

```text
useAppDispatch()
→ Provider의 Store
→ store.dispatch

useAppSelector(selectChatDraft)
→ Provider의 Store
→ store.getState()
→ state.chat.draft
```

ViewModel이 의존성을 가져오는 경로는 Store와 별개입니다.

```text
appContainer
→ resolve('prepareSampleItemUseCase')
→ Awilix가 연결한 PrepareSampleItemUseCase singleton
→ execute(item, signal)
```

일반 action을 dispatch하면 Slice가 처리합니다.

```ts
dispatch(draftChanged(value))
```

함수인 Thunk를 dispatch하면 Redux Thunk middleware가 가로채서 `dispatch`와 `getState`를 인자로
전달합니다. UseCase는 이미 ViewModel이 Service Locator에서 조회했으므로 세 번째 인자가 없습니다.

```ts
dispatchToStore(runSupportProgramSearch)

// Redux Thunk가 내부적으로 실행하는 형태
runSupportProgramSearch(store.dispatch, store.getState)
```

전체 의존성 전달 흐름은 다음과 같습니다.

```text
appContainer 모듈 로드
→ Awilix 컨테이너 한 번 생성
→ ViewModel이 필요한 토큰을 resolve
├─ Chat: SearchSupportProgramsUseCase + Redux Thunk
├─ Hook SampleItem: PrepareSampleItemUseCase 직접 실행
├─ Redux SampleItem: PrepareSampleItemUseCase + Redux Thunk
└─ Health: fetchCoreApiHealth 함수 직접 실행
```

Redux Provider 없이 `ChatPage`를 렌더링하면 React Redux Context를 찾을 수 없습니다. UseCase 조회에는
React Provider가 필요하지 않습니다. Hook을 React 컴포넌트 렌더링 밖에서 직접 호출하는 것은 여전히
허용되지 않습니다.

## Awilix DI 컨테이너 이해하기

DI 라이브러리를 사용해도 구현체 등록 자체는 필요합니다. 달라진 점은 `new`와 함수 `bind`를 직접
연결하는 대신 Awilix가 등록 정보를 바탕으로 객체 생성, 의존성 해석과 수명주기를 관리한다는 것입니다.

```text
createAppContainer()
  → registerRepositories()
  → registerUseCases()
  → registerExternalServices()
  → PROXY 방식으로 의존성 해석
  → 앱 컨테이너 안에서 singleton 재사용
  → appContainer 모듈이 root container 한 번 보관
  → ViewModel이 필요한 UseCase를 이름으로 resolve
```

컨테이너 조립과 등록은 `app/di`에 있고, `app/appContainer.ts`가 GetIt 같은 앱 단위 Service Locator를
공개합니다. ViewModel은 필요한 UseCase나 외부 함수만 resolve하고 Repository는 직접 조회하지 않습니다.
Domain은 Awilix를 import하지 않으므로 UseCase·Repository 계약은 컨테이너 구현과 분리됩니다.

등록 책임은 다음과 같이 분리합니다.

- `registerRepositories.ts`: Data Layer Repository 구현체
- `registerUseCases.ts`: Domain UseCase와 Repository 연결
- `registerExternalServices.ts`: Health처럼 UseCase가 아닌 외부 API 기능
- `container.ts`: 위 등록 모듈을 하나의 컨테이너로 조립
- `appContainer.ts`: 조립한 컨테이너를 앱 전체에서 한 번만 생성하는 Service Locator

현재 정책은 다음과 같습니다.

- 브라우저 minification에 안전하도록 `InjectionMode.PROXY`를 사용합니다.
- 수명주기 오류를 조기에 찾도록 Awilix `strict` 모드를 사용합니다.
- 상태가 없는 Repository·UseCase는 앱 컨테이너 안에서 singleton으로 관리합니다.
- 요청별 `AbortController`, 폼 상태와 Redux Store는 컨테이너에 등록하지 않습니다.
- ViewModel은 UseCase·외부 함수만 resolve하며 Repository 구현체를 직접 조회하지 않습니다.
- 테스트는 전역 컨테이너를 수정하지 않고 매번 새 컨테이너 또는 plain Fake UseCase를 사용합니다.
- 현재는 정리할 외부 자원이 없으며, WebSocket·Worker 같은 disposable 서비스를 추가할 때는 컨테이너
  handle을 앱 수명 동안 보관하고 종료 시 `container.dispose()`를 호출합니다.

Awilix의 브라우저 지원·주입 방식·수명주기 규칙은
[공식 문서](https://github.com/jeffijoe/awilix#readme)를 참고하세요.

## SampleItem React Hook과 Redux 비교

두 화면은 같은 schema, Domain mapper, UseCase, Repository와 HTTP endpoint를 사용합니다. 차이는
오직 화면 상태와 요청 상태를 어디에서 보관하고 누가 상태 전이를 기록하는지입니다.

| 항목 | React Hook 버전 | Redux Toolkit 버전 |
|---|---|---|
| 폼 값 | React Hook Form | `state.sampleItemRedux.values` |
| 요청 상태·결과 | ViewModel의 `useState` | `sampleItemRedux` slice |
| 상태 전이 | ViewModel의 setter | action·reducer·selector |
| 화면 이동 후 | 초기화 | 입력·완료 결과 유지 |
| 새로고침 후 | 초기화 | 초기화 |
| API·UseCase | 동일 | 동일 |

`AbortController`는 직렬화할 수 없으므로 두 버전 모두 ViewModel의 `useRef`가 관리합니다. Redux
Store에는 문자열, 폼 값, request ID와 API 성공 결과처럼 직렬화 가능한 값만 저장합니다.

### React Hook ViewModel

`SampleItem`은 한 페이지의 입력 폼과 한 번의 서버 요청을 ViewModel Hook으로 묶는 예제입니다.

```text
SampleItemPage
  → useSampleItemViewModel
      ├─ useForm
      │    └─ 입력값과 입력 오류 관리
      ├─ React local state
      │    └─ 로딩·성공·실패 상태 관리
      └─ appContainer.resolve('prepareSampleItemUseCase')
           └─ PrepareSampleItemUseCase.execute 직접 실행
```

View는 `useForm`, 서비스 호출, 요청 상태를 직접 조립하지 않고 ViewModel이 반환한 값만 사용합니다.

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
const prepareSampleItemUseCase =
  appContainer.resolve('prepareSampleItemUseCase')

const result = await prepareSampleItemUseCase.execute(item, controller.signal)
```

- `appContainer.resolve(...)`가 Awilix에 singleton으로 등록한 실제 UseCase 인스턴스를 가져옵니다.
- Redux dispatch, Thunk나 React Context Provider를 거치지 않습니다.
- `PrepareSampleItemUseCase.execute(...)`가 Repository를 거쳐 Core API를 호출합니다.
- 성공 결과, 로딩 여부와 안전한 오류 메시지는 ViewModel의 React state에 저장됩니다.
- 입력이 바뀌면 이전 결과를 지우고, 늦게 도착한 과거 응답은 요청 ID가 다르면 무시합니다.

ViewModel이 Repository를 직접 생성하는 것은 아닙니다. 실제 Repository와 UseCase는
`app/di`의 Awilix 컨테이너에서 연결되고, ViewModel은 `appContainer`에서 UseCase만 조회합니다. 운영
호출은 기본 singleton을 사용하고 테스트는 Hook의 선택적 인자에 Fake UseCase를 넣습니다.

```text
사용자 입력
  → useForm이 값과 오류 관리
  → 제출
  → ViewModel이 local state를 pending으로 변경
  → appContainer에서 PrepareSampleItemUseCase 조회
  → UseCase.execute()와 Repository 실행
  → ViewModel이 성공 또는 실패를 local state에 저장
  → ViewModel이 화면용 상태로 변환
  → React가 다시 렌더링
```

이 패턴은 기업정보 입력·수정처럼 현재 페이지에서 끝나는 폼에 적합합니다. 채팅 메시지처럼 여러
컴포넌트가 공유하고 오래 이어지는 작업은 Redux Slice가 담당하고, 각 페이지의 입력·포커스·DOM 상태는
React Hook에 둡니다. 따라서 SampleItem 방식과 Redux 방식은 둘 중 하나만 고르는 관계가 아니라 함께
사용하는 관계입니다.

### Redux Toolkit ViewModel

```text
ReduxSampleItemPage
  → useReduxSampleItemViewModel
      ├→ typed selector로 state.sampleItemRedux 구독
      ├→ ViewModel 안의 Thunk가 getState로 최신 상태 확인
      ├→ PrepareSampleItemUseCase.execute
      └→ started·succeeded·failed·cancelled action
          → sampleItemRedux reducer
          → selector 재계산
          → 화면 재렌더링
```

Redux 화면에서 값을 입력하고 Hook 화면이나 채팅으로 이동했다가 돌아오면 같은 Store의 입력과 성공
결과가 남습니다. `Redux 상태 초기화` 버튼은 slice를 명시적으로 초기 상태로 되돌립니다. 이 구현은
단순 폼도 항상 Redux에 넣으라는 권장이 아니라, 상태 공유·화면 간 유지 요구가 생겼을 때 추가되는
비용과 동작을 비교하기 위한 예제입니다.

## 검증

```bash
pnpm install --frozen-lockfile
pnpm test
pnpm lint
pnpm build
```

전체 서비스 연결은 루트의 `./infrastructure/scripts/verify-compose.sh`로 확인합니다.

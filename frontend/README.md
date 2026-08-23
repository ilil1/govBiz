# GovBiz Web

React, TypeScript, Vite 기반의 지원사업 검색 채팅입니다. Redux Toolkit이 대화 상태를 관리하고,
RTK Query가 샘플 검색과 이후 Core API 서버 상태의 요청·캐시 lifecycle을 담당합니다.

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
├── app/                                # Store, RTK Query, 서비스 Composition Root
├── presentation/features/chat/         # View, ViewModel, Redux slice·selector·thunk
├── presentation/features/sample-item/  # 계층 연결 예제 View와 ViewModel
├── presentation/shared/                # Core API 연결 상태 UI
├── domain/                             # Entity, Repository port, UseCase
└── data/                               # Fixture·Fetch API·Zod DTO·Repository 구현
```

`ChatPage`는 `useSupportProgramChatViewModel`이 반환한 상태와 행동만 사용합니다. ViewModel은 typed
Redux hook으로 selector와 thunk를 묶고, 검색 thunk는 RTK Query endpoint를 통해 주입된 UseCase를
실행합니다. 구체 Repository 선택은 `app/services.ts` 한 곳에서만 수행합니다.

```text
ChatPage
  → useSupportProgramChatViewModel
      → chat slice·selector·thunk
          → RTK Query
              → AppServices
                  → SearchSupportProgramsUseCase
                      → SupportProgramRepository
```

사이드바 열림과 DOM 스크롤처럼 화면에만 필요한 상태는 React 로컬 hook에 남깁니다. 공고 검색처럼
재사용·추적할 상태는 Redux에, 서버 요청과 캐시는 RTK Query에 둡니다.

## 상태 관리 설계와 확장 원칙

현재 구현은 샘플 데이터 기반 프로토타입에 필요한 상태 분리, DI, 비동기 응답 방어를 갖추고 있습니다.
같은 상태를 React Hook과 Redux 양쪽에 중복 저장하지 않고 다음 기준으로 소유권을 나눕니다.

| 소유자 | 담당 상태 | 현재 예시 |
|---|---|---|
| React Hook | 화면과 함께 사라지는 UI 상태 | 사이드바 열림, DOM 참조, 스크롤 |
| Redux Toolkit | 여러 컴포넌트가 공유하는 작업 흐름 | 입력 초안, 메시지, 검색 상태, 활성 요청 ID |
| RTK Query | 서버가 원본인 요청·응답과 캐시 | Health 조회, 공고 검색, SampleItem mutation |
| 서버 저장소 | 새로고침 후에도 남아야 하는 장기 데이터 | 이후 추가할 전체 대화 이력과 공고 데이터 |

Store를 만들 때 `AppServices`를 주입하므로 테스트에서는 실제 Repository 대신 Fake 구현을 사용할 수
있습니다. View는 typed selector와 dispatch를 직접 조립하지 않고 ViewModel Hook이 반환한 상태와 행동만
사용합니다. Redux에는 문자열·배열·일반 객체처럼 직렬화 가능한 값만 저장합니다.

현재 채팅 흐름은 다음 안전장치를 적용합니다.

- 검색 중 중복 전송을 차단합니다.
- 새 대화를 시작한 뒤 도착한 이전 성공·실패 응답은 `requestId`가 다르면 무시합니다.
- 내부 예외를 그대로 노출하지 않고 안전한 사용자 오류로 변환합니다.
- 성공, 중복 요청, 늦은 응답, 오류 흐름을 Store → RTK Query → 주입 서비스 → Slice 통합 테스트로
  검증합니다.

실제 API와 장기 대화를 연결하기 전에는 다음 항목을 보완합니다.

1. 채팅 메시지 전송은 일회성 작업이므로 RTK Query mutation으로 전환하고, 순수 공고 목록·상세
   조회만 query 캐시로 관리합니다.
2. 새 대화 시작과 화면 이탈 시 진행 중인 HTTP·LLM 요청을 `AbortSignal`로 취소합니다.
3. 전체 채팅 이력은 서버에 저장하고 Redux에는 현재 대화의 최근 메시지만 보관합니다.
4. 메시지마다 공고 객체 전체를 반복 저장하지 않고, 데이터 규모에 맞춰 공고 ID·요약 또는 정규화된
   상태를 저장합니다.
5. 요청 취소·입력 검증·네트워크·서버 오류를 구분할 안전한 오류 코드와 개발용 관측 정보를
   추가합니다.

따라서 현재 Redux 구조를 다시 만드는 것이 아니라, 실제 API 도입 시 캐시·취소·장기 보관 정책을
추가하는 방향으로 확장합니다. 더 넓은 계층 규칙은 [아키텍처 문서](../docs/architecture.md#frontend)를
참고하세요.

## SampleItem ViewModel Hook 이해하기

`SampleItem`은 한 페이지의 입력 폼과 한 번의 서버 요청을 ViewModel Hook으로 묶는 예제입니다.

```text
SampleItemPage
  → useSampleItemViewModel
      ├─ useForm
      │    └─ 입력값과 입력 오류 관리
      └─ usePrepareSampleItemMutation
           └─ 요청 실행과 로딩·성공·실패 상태 관리
```

View는 `useForm`이나 RTK Query의 세부 동작을 직접 조립하지 않고 ViewModel이 반환한 값만 사용합니다.

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

다음 코드는 새로운 API·UseCase·Repository 인스턴스를 생성하는 코드가 아닙니다.

```ts
const [prepareSampleItem, preparationMutation] = usePrepareSampleItemMutation()
```

이해하기 쉽게 이름을 바꾸면 다음과 같습니다.

```ts
const [요청보내기, 요청상태] = usePrepareSampleItemMutation()
```

- `prepareSampleItem(command)`는 사용자가 제출할 때 실제 요청을 시작하는 함수입니다.
- `preparationMutation.isLoading`은 요청 중인지 알려 줍니다.
- `preparationMutation.data`는 성공 결과를 담습니다.
- `preparationMutation.isError`는 요청 실패 여부를 알려 줍니다.
- `preparationMutation.reset()`은 입력이 변경됐을 때 이전 요청 결과를 지웁니다.

`applicationApi`와 DI로 조립한 서비스는 이미 존재합니다. 생성된 RTK Query Hook을 호출하면 현재
컴포넌트가 그 요청 기능과 상태에 연결됩니다. Hook을 호출했다고 요청이 자동 실행되지는 않으며,
`prepareSampleItem(command)`를 호출해야 실행됩니다.

```text
사용자 입력
  → useForm이 값과 오류 관리
  → 제출
  → prepareSampleItem(command)
  → RTK Query가 pending 상태 관리
  → AppServices의 UseCase·Repository 실행
  → 성공 또는 실패 상태 저장
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

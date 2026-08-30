# GovBiz 아키텍처

## 서비스 경계

```text
Browser
  → React Web
      → 공개 HTTP API
          → Spring Boot Core API
              ├→ 외부 HTTP API → 공공데이터포털
              └→ 내부 HTTP API → FastAPI AI Service
                                      → OpenAI API
```

React는 Core API만 호출합니다. FastAPI는 브라우저에 공개하지 않으며, Core API가 호출 결과를
자신의 공개 DTO와 오류 계약으로 변환합니다. 공공데이터포털 인증키는 Core API에, OpenAI 인증키는
AI Service에만 보관합니다. 외부 공고 응답을 GovBiz 지원사업 모델로 변환한 뒤 공개하며, LLM의
내부 의도 DTO는 브라우저 응답에 포함하지 않습니다. 이후 정책 엔진, 데이터베이스와 큐도 같은
방식으로 Core API 뒤에 추가합니다.

## Frontend

```text
앱 시작:
appContainer 모듈
  → Awilix Composition Root 한 번 생성
      ├→ Repository singleton
      ├→ UseCase singleton
      └→ 외부 API 함수

요청 실행:
View
  → ViewModel Hook
      ├→ Chat: typed Redux hooks
      │   → appContainer.resolve(SearchSupportProgramsUseCase)
      │   → ViewModel 안의 Redux Thunk
      │       → Domain UseCase.execute
      │           → Repository interface
      │               → Fixture 또는 HTTP Repository
      │   → Redux Toolkit slice·selector
      └→ SampleItem·Health
          ├→ Hook SampleItem·Health: 직접 실행 → React local state
          └→ Redux SampleItem: Thunk → 같은 UseCase → Redux slice·selector
```

- **View**는 JSX, 접근성, 표시를 담당합니다.
- **ViewModel**은 selector와 action을 화면이 사용하기 좋은 상태·행동으로 묶고, 검색 Thunk의 전체
  실행 순서를 한곳에 보여 줍니다.
- **Redux Toolkit**은 대화 메시지·검색 조건과 Redux SampleItem처럼 여러 화면에서 유지할 클라이언트
  상태를 관리합니다.
- **React Router**는 `/`, `/examples/sample-item/hook`, `/examples/sample-item/redux` URL을 각각의
  화면과 연결하고 브라우저 뒤로 가기와 직접 진입을 지원합니다.
- **React Hook state**는 Health와 Hook SampleItem처럼 한 화면에서 끝나는 요청의 로딩·성공·실패
  상태를 관리합니다.
- **Awilix Composition Root**는 `app/di`의 역할별 등록 모듈을 하나의 객체 graph로 조립하고 앱 단위
  singleton 수명주기를 관리합니다.
- **appContainer**는 GetIt처럼 앱 전체에서 동일한 Awilix root container를 조회하는 Service
  Locator입니다. ViewModel은 Repository가 아닌 UseCase·외부 함수 토큰만 resolve합니다.
- **UseCase**는 화면과 HTTP 구현 사이의 업무 행동입니다.
- **Repository interface**는 Domain이 필요한 통신을 정의합니다.
- **Data Layer**는 Fetch, URL, Zod 응답 검증을 소유합니다.

SampleItem의 두 화면은 같은 UseCase·Repository·endpoint를 사용하며 상태 보관 위치만 다릅니다.
Hook 화면은 이탈 시 초기화되고 Redux 화면의 완료 상태는 최상위 `appStore`의 `sampleItem` Slice가
유지합니다. 둘 다 새로고침
후에는 초기화됩니다. 사이드바 열림과 DOM 참조 같은 화면 전용 상태는 Redux에 넣지 않고 React 로컬 hook에 둡니다.
Health 조회처럼 업무 도메인이 아닌 연결 상태는 UseCase·Repository를 억지로 거치지 않지만, 서버
요청과 취소 lifecycle은 해당 Hook이 직접 관리합니다.

## Core API

```text
supportprogram/controller
→ supportprogram/service
→ supportprogram/domain
   ├→ supportprogram/client/bizinfo → 공공데이터포털
   └→ supportprogram/client/ai → _adapters/ai/client → FastAPI → OpenAI
```

- **supportprogram/controller**는 HTTP 요청을 받고 응답 DTO로 변환합니다.
- **supportprogram/service**는 검색 흐름, 공고 정규화와 AI 점수화 결과 검증을 담당합니다.
- **supportprogram/domain**은 프레임워크에 의존하지 않는 지원사업 모델과 상태를 둡니다.
- **supportprogram/dto/api**는 브라우저 공개 응답을, **supportprogram/dto/ai**는 AI Service 내부 요청·응답을, **supportprogram/dto/bizinfo**는 기업마당 응답 전송 객체를 둡니다.
- **supportprogram/client/bizinfo**는 기업마당 HTTP 요청·응답 계약과 연결 설정을 담당합니다.
- **supportprogram/client/ai**는 AI 점수화 Client 인터페이스를 담당합니다.
- **_adapters/ai/client·config**는 비즈니스 기능이 아닌 공통 FastAPI 연결 어댑터를 관리합니다.
- **_health_ai_service**는 AI Service 상태 조회 API와 Health 계약 검증을 독립적으로 관리합니다.

각 기능은 자기 `controller`, `service`, `domain`, `dto`, `client`, `config`를 소유합니다. 여러 기능에서 실제로 함께 쓰는 JSON·CORS·RestClient 설정, 전역 예외 처리와 timeout 판별만 `_common/config`, `_common/exception`, `_common/http`에 둡니다. `_common`의 밑줄은 IDE에서 공통 코드를 기능보다 위에 표시하기 위한 프로젝트 규칙입니다. 데이터베이스를 도입할 때도 해당 기능 아래에 필요한 저장 계층을 추가합니다.

## LLM 추천 점수화와 장애 격리

Core API는 공개 검색 요청을 받으면 AI Service에 다음 내부 요청을 보냅니다.

```http
POST /internal/v1/support-program-rankings/rank
Content-Type: application/json

{"originalQuery":"서울 AI 창업지원 찾아줘","scoringVersion":"govbiz-support-program-ranking-v1","resultLimit":5,"candidates":["Core가 검증한 공식 공고 후보"]}
```

AI Service는 필수 [OpenAI Agents SDK](https://openai.github.io/openai-agents-python/)의 단일 typed
agent를 실행합니다. 프롬프트의 버전된 100점 기준에 따라 모든 후보의 의미 관련성·대상·지역·접수
상태·지원 유형을 점수화하고 추천 이유를 반환합니다. 공고에 없는 사실은 생성하지 않습니다.

```text
AI Service
  ├→ Agent + Runner 성공 + schema 검증 성공 → 후보별 세부 점수·총점·추천 이유
  └→ 키 누락은 시작 실패, 실행·검증 실패는 안전한 HTTP 503

Core API
  ├→ 접수 상태를 먼저 필터링하고 최신 후보 최대 20개만 전송
  ├→ 후보 ID·중복·점수 범위·합계·내림차순을 재검증
  └→ AI HTTP 오류·timeout·JSON 오류·echo 불일치·계약 위반 → 공개 오류
```

AI Service가 유효하지 않은 요청을 받으면 422를 반환합니다. LLM 실패를 Kotlin 고정 가중치나
단어 사전으로 숨기지 않으므로 AI Service나 OpenAI 장애는 공개 검색 오류로 전파됩니다. 성공 응답은 계속
`{query, programs}` 계약을 유지합니다.

현재는 한 번의 구조화된 후보 점수화만 필요하므로 agent 하나를 `max_turns=1`로 실행합니다. tool,
handoff, session이나 manager agent는 실제 역할이 없어 추가하지 않습니다. 추천 기능의 Agent,
prompt, model과 서비스 흐름은 `support_program_ranking` 수직 기능 디렉터리에 모으고, OpenAI
client 소유권과 DI는 root `bootstrap.py`에 둡니다. 실제 사업 조회 도구나 서로 다른 전문가로 실행권을
넘기는 요구가 생길 때 `<feature_name>` 모듈과 tool 또는 handoff 도입을 다시 평가합니다.

모델 HTTP 호출과 전체 Runner 실행은 별도 timeout으로 제한합니다. 전체 run 제한을 모델 호출
제한보다 길고 Core API의 AI Service 읽기 제한보다 짧게 두어, Core가 timeout을 명확한 공개 오류로
변환할 시간을 확보합니다.

## Docker Compose 개발 흐름

```text
Browser (127.0.0.1:5173)
  → Vite web container
      → /api proxy
          → core-api:8080
              ├→ https://apis.data.go.kr
              └→ ai-service:8000
                    └→ https://api.openai.com (LLM 활성 시)
```

브라우저는 `core-api`와 `ai-service`라는 Compose 내부 DNS 이름을 알 수 없습니다. React는 `/api`
상대 주소를 호출하고, Vite 컨테이너가 내부 DNS를 사용해 Core API로 중계합니다.

## 의존성 규칙

- React View는 Redux Store 또는 Data Layer를 직접 호출하지 않고 ViewModel을 사용합니다.
- ViewModel은 전역 `appContainer`에서 필요한 UseCase나 외부 API 함수만 resolve하고, Repository를
  직접 resolve하거나 생성하지 않습니다.
- `createAppContainer()`는 운영 코드에서 `app/appContainer.ts`만 호출합니다. 테스트는 격리된 새
  컨테이너를 만들 수 있습니다.
- Core API는 FastAPI 전송 DTO를 브라우저에 그대로 노출하지 않습니다.
- FastAPI는 Core API 소스 코드를 import하거나 Core API 데이터 저장소를 수정하지 않습니다.
- 공공데이터포털 인증키는 Core API 환경변수에만 주입하고 Frontend bundle·응답·로그에 노출하지
  않습니다.
- OpenAI 인증키는 AI Service 환경변수에만 주입하며 Core API·Frontend·공개 응답·로그에 노출하지
  않습니다.
- LLM은 Core가 제공한 후보만 점수화하며 공고 사실, 신청 가능 여부, 금액과 날짜의 최종 근거는
  기업마당 원문입니다. Core는 존재하지 않는 공고 ID와 잘못된 점수 합계를 거부합니다.
- 외부 공고의 신청기간을 확실히 해석할 수 없으면 `UNKNOWN`으로 유지하며 접수 상태를 추정하지
  않습니다.
- 서비스 간 통신은 명시적인 HTTP·JSON 계약과 테스트로 검증합니다.
- 필요하지 않은 빈 계층이나 인프라를 미리 만들지 않습니다.

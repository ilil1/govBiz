# GovBiz 아키텍처

## 서비스 경계

```text
Browser
  → React Web
      → 공개 HTTP API
          → Spring Boot Core API
              → 내부 HTTP API
                  → FastAPI AI Service
```

React는 Core API만 호출합니다. FastAPI는 브라우저에 공개하지 않으며, Core API가 호출 결과를
자신의 공개 DTO와 오류 계약으로 변환합니다. 이후 정책 엔진, 데이터베이스, 큐, 외부 API도 같은
방식으로 Core API 뒤에 추가합니다.

## Frontend

```text
앱 시작:
Awilix Composition Root
  → AppServices facade
      → Redux Store의 Thunk extraArgument

요청 실행:
View
  → ViewModel Hook
      → typed Redux hooks
          → ViewModel 안의 Redux Thunk
              → injected AppServices facade
                  → Domain UseCase
                      → Repository interface
                          → Fixture 또는 HTTP Repository
          → Redux Toolkit slice·selector
```

- **View**는 JSX, 접근성, 표시를 담당합니다.
- **ViewModel**은 selector와 action을 화면이 사용하기 좋은 상태·행동으로 묶고, 검색 Thunk의 전체
  실행 순서를 한곳에 보여 줍니다.
- **Redux Toolkit**은 대화 메시지·검색 조건처럼 공유할 클라이언트 상태를 관리합니다.
- **React Hook state**는 Health와 SampleItem처럼 한 화면에서 끝나는 요청의 로딩·성공·실패 상태를
  관리합니다.
- **Awilix Composition Root**는 `app/di`의 역할별 등록 모듈을 하나의 객체 graph로 조립하고 앱 단위
  singleton 수명주기를 관리합니다.
- **AppServices**는 컨테이너 전체를 노출하지 않고 ViewModel에 필요한 기능만 전달하는 facade입니다.
- **UseCase**는 화면과 HTTP 구현 사이의 업무 행동입니다.
- **Repository interface**는 Domain이 필요한 통신을 정의합니다.
- **Data Layer**는 Fetch, URL, Zod 응답 검증을 소유합니다.

사이드바 열림과 DOM 참조 같은 화면 전용 상태는 Redux에 넣지 않고 React 로컬 hook에 둡니다.
Health 조회처럼 업무 도메인이 아닌 연결 상태는 UseCase·Repository를 억지로 거치지 않지만, 서버
요청과 취소 lifecycle은 해당 Hook이 직접 관리합니다.

## Core API

```text
Controller → Service → Domain
                    ↘ client/ai → FastAPI
```

- **Controller**는 HTTP 요청·응답 DTO 변환과 Bean Validation을 담당합니다.
- **Service**는 use case와 상태 전이를 담당합니다.
- **Domain**은 프레임워크에 의존하지 않는 record·enum·불변식을 둡니다.
- **client/ai**는 FastAPI와의 HTTP 계약을 소비합니다.
- **config**는 외부 서비스 주소와 HTTP Client 설정을 조립합니다.

외부 HTTP 호출은 영속성 Repository와 다른 책임이므로 `client`에 둡니다. 데이터베이스를 도입할 때
그때 필요한 Repository를 추가합니다.

## AI Service

FastAPI는 내부 분석 서비스를 위한 독립 실행 단위입니다. 지금은 Health 계약만 제공하지만, 이후
PDF 처리, LLM Structured Output, 검색 같은 기능을 이 서비스에 추가할 수 있습니다. AI Service는
Core API의 데이터베이스를 직접 수정하지 않습니다.

## Docker Compose 개발 흐름

```text
Browser (127.0.0.1:5173)
  → Vite web container
      → /api proxy
          → core-api:8080
              → ai-service:8000
```

브라우저는 `core-api`와 `ai-service`라는 Compose 내부 DNS 이름을 알 수 없습니다. React는 `/api`
상대 주소를 호출하고, Vite 컨테이너가 내부 DNS를 사용해 Core API로 중계합니다.

## 의존성 규칙

- React View는 Redux Store 또는 Data Layer를 직접 호출하지 않고 ViewModel을 사용합니다.
- ViewModel의 Redux Thunk는 구체 Repository를 생성하지 않고 AppServices에서 필요한 의존성을
  주입받습니다.
- Awilix 컨테이너와 `container.resolve()`는 `app/di` Composition Root와 `app/services.ts` 공개
  facade 밖으로 노출하지 않습니다.
- Core API는 FastAPI 전송 DTO를 브라우저에 그대로 노출하지 않습니다.
- FastAPI는 Core API 소스 코드를 import하거나 Core API 데이터 저장소를 수정하지 않습니다.
- 서비스 간 통신은 명시적인 HTTP·JSON 계약과 테스트로 검증합니다.
- 필요하지 않은 빈 계층이나 인프라를 미리 만들지 않습니다.

# GovBiz AI Service

Core API가 내부 HTTP로 호출하는 FastAPI 서비스입니다. OpenAI Agents SDK의 단일 typed agent가
사용자의 질문을 지원사업 검색 조건으로 변환합니다. OpenAI는 필수 의존성이며, 설정 누락이나 실행
실패를 불완전한 규칙 분석으로 숨기지 않습니다.

## 내부 Health 계약

```text
GET /internal/v1/health
→ { "status": "up", "service": "govbiz-ai-service" }
```

이 경로는 브라우저 공개 API가 아닙니다. React는 Core API만 호출하므로 AI Service에 CORS를
추가하지 않았습니다.

## 검색 의도 분석 계약

```http
POST /internal/v1/search-intents/analyze
Content-Type: application/json

{
  "query": "서울에서 AI 스타트업 지원사업 찾아줘",
  "acceptingOnly": true
}
```

```json
{
  "originalQuery": "서울에서 AI 스타트업 지원사업 찾아줘",
  "keywords": [],
  "regions": ["서울"],
  "categories": ["AI", "창업"],
  "targetTerms": ["스타트업"],
  "acceptingOnly": true,
  "clarificationNeeded": false,
  "clarificationQuestion": null
}
```

`acceptingOnly`는 Core API가 지정한 검색 조건이므로 LLM이 변경하지 않습니다. 각 검색 조건 배열은
최대 8개이며, 지역과 분야는 Core API가 이해하는 enum으로 제한됩니다.

## 필수 OpenAI 설정과 오류 경계

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `OPENAI_API_KEY` | 없음(필수) | AI Service에만 주입하는 비밀키. 없거나 공백이면 시작 실패 |
| `OPENAI_MODEL` | `gpt-5.6-luna` | Structured Outputs를 지원하는 모델 |
| `LLM_MODEL_TIMEOUT_SECONDS` | `2.0` | OpenAI 모델 호출 한 번의 제한시간 |
| `LLM_RUN_TIMEOUT_SECONDS` | `2.5` | parsing을 포함한 전체 agent run 제한시간 |

두 timeout은 0초 초과 30초 이하만 허용하며, run timeout을 model timeout보다 길게 유지합니다. 기존
`LLM_TIMEOUT_SECONDS`는 마이그레이션을 위해 run timeout의 별칭으로만 계속 읽습니다.

OpenAI 호출은 공식 Agents SDK의 `Agent(output_type=ExtractedSearchIntent)`와 비동기
`Runner.run()`을 사용합니다. 현재 책임은 한 번의 구조화 추출이므로 tool·handoff·session 없이
`max_turns=1`로 제한합니다. 모델 출력은 Pydantic이 검증하고, SDK 재시도는 0회이며 reasoning
effort는 `none`입니다. 응답 저장은 `store=false`, Agents SDK tracing은 비활성화합니다.

API 오류, 거절, timeout, 불완전하거나 스키마 검증에 실패한 출력은 안전한 HTTP 503으로 반환합니다.
Agent 오류 본문과 사용자 질의는 로그에 남기지 않으며, 애플리케이션 종료 시 container가 소유한
비동기 OpenAI client를 닫습니다.

## 코드 구조

```text
app/
├── agents/
│   ├── errors.py         # 모든 Agent가 공유하는 실행 실패 경계 오류
│   └── search_intent/    # 검색 의도 Agent 수직 슬라이스
│       ├── agent.py      # Agent 설정과 Runner 실행
│       ├── prompt.py     # instructions
│       ├── models.py     # 입력·출력·Structured Output 계약
│       └── service.py    # 필수 agent 실행과 응답 조립
├── api/                  # FastAPI 라우터와 HTTP 의존성 조회
├── schemas/              # Agent와 무관한 공통 HTTP schema
├── bootstrap.py          # OpenAI client·model·agent·service DI 조립과 소유권
└── main.py               # FastAPI 생성과 lifespan
```

```text
HTTP 요청
→ SearchIntentAnalysisService
→ SearchIntentAgent → Runner.run(max_turns=1) → Pydantic output
    ├→ 성공 → SearchIntentResponse
    └→ 실패 → 안전한 HTTP 503
```

## Python 파일의 수직 실행 흐름

Python 파일이 요청마다 위에서 아래로 전부 다시 실행되는 것은 아닙니다. 서버 시작 시 설정과 객체를
한 번 조립하고, 요청이 들어오면 이미 만들어 둔 객체를 순서대로 호출합니다.

### 1. 서버 시작 시 한 번 실행되는 흐름

Docker 컨테이너는 `python -m uvicorn app.main:create_app --factory`로 시작합니다. Uvicorn이
`app/main.py`의 `create_app()`을 호출해 FastAPI 객체를 생성한다는 뜻입니다.

```text
Dockerfile CMD
→ Uvicorn이 app/main.py import
→ Uvicorn이 main.py의 create_app() 호출
→ config.py의 Settings.from_environment()
   → 필수 OPENAI_API_KEY, OPENAI_MODEL, timeout 읽기
→ bootstrap.py의 build_application_container(settings)
   → AsyncOpenAI 생성
   → OpenAIResponsesModel 생성
   → SearchIntentAgent 생성
   → SearchIntentAnalysisService(agent) 생성
→ main.py가 container를 FastAPI application.state에 저장
→ search_intents router 등록
→ HTTP 요청 대기
```

조립이 끝난 뒤 메모리에 존재하는 객체 관계는 다음과 같습니다.

```text
FastAPI application
└→ state.container: ApplicationContainer
    ├→ search_intent_service: SearchIntentAnalysisService
    │   └→ _agent: SearchIntentAgent
    │       └→ _agent: OpenAI Agents SDK의 Agent
    └→ openai_client: AsyncOpenAI 또는 None
```

바깥쪽 `SearchIntentAgent`는 GovBiz가 만든 wrapper이고, 그 안쪽 `_agent`는 OpenAI Agents SDK가
제공하는 `Agent` 객체입니다. `ApplicationContainer`와 그 안의 객체는 요청마다 새로 만들지 않고
서버가 실행되는 동안 재사용합니다.

### 2. 검색 요청의 성공 흐름

Core API가 다음 내부 요청을 보냈다고 가정합니다.

```json
{
  "query": "서울에서 AI 스타트업 지원사업 찾아줘",
  "acceptingOnly": true
}
```

실제 호출 순서는 다음과 같습니다.

```text
POST /internal/v1/search-intents/analyze
→ api/search_intents.py의 analyze_search_intent()
→ models.py의 SearchIntentRequest
   → query 길이·acceptingOnly 타입·추가 필드 검증
→ get_search_intent_service()
   → request.app.state.container에서 이미 만든 service 조회
→ service.py의 SearchIntentAnalysisService.analyze(request)
→ agent.py의 SearchIntentAgent.analyze(query)
→ OpenAI Agents SDK Runner.run(max_turns=1)
   ├→ prompt.py의 SEARCH_INTENT_INSTRUCTIONS 사용
   ├→ OpenAIResponsesModel이 AsyncOpenAI로 모델 호출
   └→ models.py의 ExtractedSearchIntent로 출력 검증
→ agent.py가 검증된 ExtractedSearchIntent 반환
→ service.py가 SearchIntentResponse 생성
→ FastAPI가 camelCase JSON으로 직렬화
→ Core API에 반환
```

`models.py`는 별도의 업무 함수를 실행하는 파일이라기보다 요청과 AI 출력이 약속된 형식인지 검사하는
양식입니다. FastAPI와 Agents SDK가 모델 클래스를 사용하면서 Pydantic validator를 자동 실행합니다.

### 3. 설정 누락 또는 Agent 실패 흐름

`OPENAI_API_KEY`가 없거나 공백이면 `Settings.from_environment()`가 시작 오류를 발생시켜 잘못 구성된
AI Service가 요청을 받지 못하게 합니다. 실행 중 timeout, 모델 거부, OpenAI 오류 또는 structured
output 검증 실패가 발생하면 `agent.py`가 공통 `AgentExecutionError`로 변환합니다.

```text
api/search_intents.py
→ service.py
→ agent.py
→ OpenAI 또는 output 검증 실패
→ AgentExecutionError
→ api/search_intents.py가 세부정보 없는 HTTP 503 반환
```

사용자 질문, API key, OpenAI 원문 오류는 응답이나 로그에 노출하지 않습니다.

### 4. 파일별 호출 관계

| 파일 | 누가 사용하거나 호출하는가 | 실행 책임 |
|---|---|---|
| `main.py` | Uvicorn | FastAPI 생성, container 저장, router와 lifespan 등록 |
| `config.py` | `main.py` | 환경변수를 `Settings`로 변환 |
| `bootstrap.py` | `main.py` | OpenAI client, model, Agent, Service 조립 |
| `api/search_intents.py` | FastAPI | HTTP 요청 수신, Service 조회와 호출 |
| `service.py` | API router | 필수 Agent 실행과 응답 계약 조립 |
| `agent.py` | Service | Agents SDK `Runner.run()` 실행과 오류 경계 변환 |
| `models.py` | FastAPI, Agent, Service | 요청·출력·응답 형식과 불변식 검증 |
| `prompt.py` | `agent.py` | 모델에 전달할 instructions 제공 |
| `agents/errors.py` | 모든 Agent와 Agent API | 공통 Agent 실행 실패 경계 오류 정의 |
| `__init__.py` | Python import system | 디렉터리를 패키지로 인식하고 패키지 설명 제공 |

의존성 import 방향과 실제 런타임 호출 순서는 다릅니다. 예를 들어 `models.py`는 여러 파일에서
import되지만 HTTP 요청을 직접 받거나 OpenAI를 직접 호출하지 않습니다.

### 5. 서버 종료 흐름

Uvicorn이 종료되면 `main.py`가 등록한 lifespan의 `finally`가 실행됩니다.

```text
Uvicorn 종료
→ main.py lifespan 종료
→ ApplicationContainer.close()
→ AsyncOpenAI.close()
→ 프로세스 종료
```

가장 짧게 기억할 수 있는 요청 흐름은 다음 두 줄입니다.

```text
성공: HTTP → api → service → agent → OpenAI → models 검증 → service → HTTP
실패: HTTP → api → service → agent/OpenAI 실패 → 안전한 HTTP 503
```

검색 의도 추출이라는 한 책임에 manager/triage/region/category agent를 따로 만들지는 않습니다.
실제 사업 조회 tool이나 서로 다른 전문가에게 실행권을 넘기는 요구가 생길 때만 tool, handoff 또는
추가 agent를 도입합니다.

여러 Agent를 추가할 때의 폴더 규칙, 의존성 방향과 테스트 배치는
[AI Agent 모듈 구조](docs/agent-structure.md)에 정리되어 있습니다.

## 설치와 실행

Python 3.11~3.14와 `uv`가 필요합니다.

```bash
uv sync --locked --extra dev
OPENAI_API_KEY=발급받은_키 \
uv run --locked --extra dev python -m uvicorn app.main:create_app --factory --reload --port 8000
```

확인:

```bash
curl --fail http://127.0.0.1:8000/internal/v1/health
```

FastAPI 문서는 `http://127.0.0.1:8000/docs`에서 볼 수 있습니다.

## 검증

```bash
uv lock --check
uv sync --locked --extra dev
uv pip check --python .venv/bin/python
uv run --locked --extra dev python -m pytest
uv build
```

Agent workflow 테스트는 공식 `agents.testing.ScriptedModel`로 실제 Runner를 실행합니다. OpenAI
wire 계약 한 건만 로컬 mock transport로 확인하므로 실제 OpenAI 키나 외부 네트워크를 사용하지
않습니다.

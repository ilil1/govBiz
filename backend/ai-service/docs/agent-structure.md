# AI Agent 모듈 구조

GovBiz AI Service는 여러 Agent가 추가될 수 있다는 전제에서 `app/agents/<agent_name>/` 단위로
코드를 묶습니다. Agent별 폴더는 SDK 객체만 보관하는 기술 레이어가 아니라, 해당 Agent가 제공하는
기능을 한곳에서 찾을 수 있게 하는 수직 슬라이스입니다.

OpenAI 공식 문서는 `Agent`, instructions, model, tools, handoffs, structured output 같은 구성 요소를
정의하지만 애플리케이션의 디렉터리 구조까지 강제하지 않습니다. 이 문서의 폴더와 의존성 규칙은
GovBiz에서 여러 Agent를 일관되게 관리하기 위한 프로젝트 규약입니다.

## 현재 구조

```text
app/
├── agents/
│   ├── __init__.py
│   └── search_intent/
│       ├── __init__.py
│       ├── agent.py
│       ├── models.py
│       ├── port.py
│       ├── prompt.py
│       └── service.py
├── api/
│   ├── health.py
│   └── search_intents.py
├── schemas/
│   └── health.py
├── bootstrap.py
├── config.py
└── main.py
```

| 파일 | 책임 |
|---|---|
| `agent.py` | Agents SDK의 `Agent`, 모델 설정과 `Runner.run()` 실행 |
| `prompt.py` | Agent instructions 원문 |
| `models.py` | Structured Output, 요청·응답, enum 계약 |
| `port.py` | 서비스가 의존하는 분석 포트와 안전한 경계 오류 |
| `service.py` | 필수 Agent 실행과 응답 계약 조립 |
| `__init__.py` | 패키지 설명만 제공하며 다른 모듈을 자동으로 다시 export하지 않음 |

HTTP 라우팅과 FastAPI 수명주기는 특정 Agent가 소유하지 않으므로 `api/`와 `main.py`에 둡니다.
OpenAI client 생성·소유·종료와 구체 구현 조립은 애플리케이션 composition root인 `bootstrap.py`가
담당합니다.

## 의존성 방향

```text
models ← port ← agent
models + port ← service
agent + port + service ← bootstrap
models + service ← api
bootstrap + port ← main
```

다음 규칙을 지킵니다.

- `models.py`와 `prompt.py`는 다른 Agent 모듈을 import하지 않습니다.
- `port.py`는 구체 구현인 `agent.py`를 import하지 않습니다.
- `agent.py`는 HTTP 처리를 위해 `service.py`, `api/`를 import하지 않습니다.
- `service.py`는 `SearchIntentAgent`를 직접 생성하지 않고 `SearchIntentAnalyzer` 포트에만 의존합니다.
- OpenAI client와 `OpenAIResponsesModel`은 `bootstrap.py`에서만 생성합니다.
- 슬라이스 내부에서는 `.models`, `.port`처럼 형제 모듈을 명시하고, 외부에서는
  `app.agents.search_intent.agent`처럼 전체 경로를 사용합니다. 패키지 `__init__.py`의 광범위한
  re-export로 순환 import를 숨기지 않습니다.

## 검색 의도 Agent 실행 흐름

```text
POST /internal/v1/search-intents/analyze
→ SearchIntentAnalysisService
→ SearchIntentAgent
→ Runner.run(max_turns=1)
→ ExtractedSearchIntent 검증
    ├→ 성공 → SearchIntentResponse
    └→ 실패 → 안전한 HTTP 503
```

Agent는 SDK·OpenAI·timeout·structured output 오류를 `SearchIntentAnalysisError`로 바꿉니다.
API는 이 경계 오류를 세부정보 없는 HTTP 503으로 변환합니다. 규칙 기반 의미 분석으로 오류를
숨기지 않으며, `OPENAI_API_KEY`가 없으면 애플리케이션 생성 단계에서 실패합니다.

## 새로운 Agent 추가 방법

예를 들어 지원 자격 판정 Agent를 추가한다면 다음처럼 시작합니다.

```text
app/agents/eligibility/
├── __init__.py
├── agent.py
├── models.py
├── port.py
├── prompt.py
└── service.py

tests/agents/eligibility/
└── test_eligibility_agent.py
```

1. `models.py`에 입력과 structured output 계약을 먼저 정의합니다.
2. `port.py`에 호출자가 의존할 최소 Protocol과 경계 오류를 정의합니다.
3. `prompt.py`에는 instructions만 둡니다.
4. `agent.py`에서 SDK Agent와 Runner 실행 한도를 설정합니다.
5. 응답 조립이나 추가 애플리케이션 흐름이 필요할 때만 `service.py`를 추가합니다.
6. `bootstrap.py`에서 client, model, Agent와 서비스를 조립하고 소유 자원을 등록합니다.
7. HTTP가 필요하면 `api/`에 얇은 라우터를 추가합니다.
8. `tests/agents/<agent_name>/`에서 실제 Runner를 네트워크 없이 실행해 Agent 계약을 검증합니다.

모든 Agent가 `service.py`를 반드시 가져야 하는 것은 아닙니다. 필요한 책임만 추가하고,
파일 이름과 의존 방향은 같은 규칙을 따릅니다.

## 공통 코드 추출 기준

두 번째 Agent가 생기기 전에는 `BaseAgent`, `AgentRegistry`, 범용 Runner wrapper를 만들지 않습니다.
OpenAI Agents SDK가 이미 `Agent`와 `Runner`를 제공하므로 동일한 개념을 다시 감싸면 실제 설정과 오류
경계가 보이지 않게 됩니다.

두 개 이상의 Agent에서 같은 코드가 반복되고 변경 이유도 같을 때만 `app/agents/shared/`를 만듭니다.
공유 후보는 개인정보 제거 로깅, 공통 timeout 검증, 순수한 변환 함수처럼 Agent 업무 의미와 무관한
기능으로 제한합니다. prompt, output model과 서비스 흐름은 각 Agent 폴더에 남깁니다.

## 테스트 배치

```text
tests/
├── agents/
│   └── search_intent/
│       └── test_search_intent_agent.py
├── test_bootstrap.py
├── test_config.py
├── test_health.py
└── test_search_intents.py
```

- Agent 단위 테스트는 `tests/agents/<agent_name>/`에서 공식 `ScriptedModel`로 실제 Runner를
  실행합니다.
- API 성공·실패 계약과 OpenAPI 검증은 HTTP 경계 테스트에 남깁니다.
- client 생성과 lifespan 종료는 `test_bootstrap.py`에서 검증합니다.
- 실제 OpenAI 네트워크를 사용하지 않고, provider wire 계약이 필요할 때만 mock transport를
  사용합니다.

Agent 정의와 structured output의 공식 개념은
[OpenAI Agent definitions](https://developers.openai.com/api/docs/guides/agents/define-agents)를
기준으로 합니다.

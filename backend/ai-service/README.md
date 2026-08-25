# GovBiz AI Service

Core API가 내부 HTTP로 호출하는 FastAPI 서비스입니다. OpenAI Agents SDK의 단일 typed agent가
사용자의 질문을 지원사업 검색 조건으로 변환하고, agent를 사용할 수 없을 때는 결정적인 규칙 기반
분석으로 자동 전환합니다.

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
  "clarificationQuestion": null,
  "analysisMode": "LLM"
}
```

`acceptingOnly`는 Core API가 지정한 검색 조건이므로 LLM이 변경하지 않습니다. 각 검색 조건 배열은
최대 8개이며, 지역과 분야는 Core API가 이해하는 enum으로 제한됩니다.

## LLM 설정과 fallback

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `LLM_PROVIDER` | `disabled` | `openai`일 때만 OpenAI 호출 활성화 |
| `OPENAI_API_KEY` | 없음 | AI Service에만 주입하는 비밀키 |
| `OPENAI_MODEL` | `gpt-5.6-luna` | Structured Outputs를 지원하는 모델 |
| `LLM_MODEL_TIMEOUT_SECONDS` | `2.0` | OpenAI 모델 호출 한 번의 제한시간 |
| `LLM_RUN_TIMEOUT_SECONDS` | `2.5` | parsing을 포함한 전체 agent run 제한시간 |

두 timeout은 0초 초과 30초 이하만 허용하며, run timeout을 model timeout보다 길게 유지합니다. 기존
`LLM_TIMEOUT_SECONDS`는 마이그레이션을 위해 run timeout의 별칭으로만 계속 읽습니다.

OpenAI 호출은 공식 Agents SDK의 `Agent(output_type=ExtractedSearchIntent)`와 비동기
`Runner.run()`을 사용합니다. 현재 책임은 한 번의 구조화 추출이므로 tool·handoff·session 없이
`max_turns=1`로 제한합니다. 모델 출력은 Pydantic이 검증하고, SDK 재시도는 0회이며 reasoning
effort는 `none`입니다. 응답 저장은 `store=false`, Agents SDK tracing은 비활성화합니다.

API 오류, 거절, timeout, 불완전하거나 스키마 검증에 실패한 출력은 HTTP 오류 대신
`analysisMode: "RULE_BASED_FALLBACK"`인 동일한 응답 계약으로 전환합니다. Agent 오류 본문과 사용자
질의는 로그에 남기지 않으며, 애플리케이션 종료 시 container가 소유한 비동기 OpenAI client를
닫습니다. Agent 구성이나 프로그래밍 오류는 fallback으로 숨기지 않습니다. 기존 Core API 계약을
지키기 위해 성공 경로의 `analysisMode` 값은 계속 `LLM`입니다.

## 코드 구조

```text
app/
├── agents/
│   └── search_intent/    # 검색 의도 Agent 수직 슬라이스
│       ├── agent.py      # Agent 설정과 Runner 실행
│       ├── prompt.py     # instructions
│       ├── models.py     # 입력·출력·Structured Output 계약
│       ├── port.py       # SearchIntentAnalyzer 추상화
│       ├── rules.py      # 결정적 fallback
│       └── service.py    # agent 우선/fallback 전환 흐름
├── api/                  # FastAPI 라우터와 HTTP 의존성 조회
├── schemas/              # Agent와 무관한 공통 HTTP schema
├── bootstrap.py          # OpenAI client·model·agent·service DI 조립과 소유권
└── main.py               # FastAPI 생성과 lifespan
```

```text
HTTP 요청
→ SearchIntentAnalysisService
    ├→ SearchIntentAgent → Runner.run(max_turns=1) → Pydantic output
    └→ 실패 또는 비활성화 → extract_with_rules
→ 동일한 SearchIntentResponse
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
uv run --locked --extra dev python -m uvicorn app.main:app --reload --port 8000
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

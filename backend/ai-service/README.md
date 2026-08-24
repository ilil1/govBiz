# GovBiz AI Service

Core API가 내부 HTTP로 호출하는 FastAPI 서비스입니다. OpenAI Structured Outputs로 사용자의
질문을 지원사업 검색 조건으로 변환하고, LLM을 사용할 수 없을 때는 결정적인 규칙 기반 분석으로
자동 전환합니다.

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
| `LLM_TIMEOUT_SECONDS` | `2.5` | provider 호출 제한시간, 허용 범위 0초 초과 30초 이하 |

OpenAI 호출은 공식 비동기 SDK의 `responses.parse`와 Pydantic 스키마를 사용하며, SDK 재시도는
0회이고 reasoning effort는 `none`입니다. 응답 저장은 `store=false`로 비활성화하고, 설정한
timeout을 전체 provider 호출의 상한으로 적용합니다. `completed`가 아닌 응답, API 오류, 거절,
불완전하거나 스키마 검증에 실패한 응답은 HTTP 오류 대신
`analysisMode: "RULE_BASED_FALLBACK"`인 동일한 응답 계약으로 전환합니다. Provider 오류 본문과
사용자 질의는 로그에 남기지 않으며, 애플리케이션 종료 시 비동기 OpenAI client도 닫습니다.

현재 기능은 한 번의 구조화 추출만 필요하므로 LangChain이나 agent loop를 사용하지 않습니다.
다중 provider 또는 도구 실행 workflow가 실제로 필요해지면 `SearchIntentProvider` 포트 뒤에
adapter를 추가할 수 있습니다.

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

단위·계약 테스트는 가짜 provider와 로컬 mock transport를 사용하므로 실제 OpenAI 키나 외부
네트워크를 사용하지 않습니다.

# Base Architecture AI Service

Core API가 내부 HTTP로 호출하는 FastAPI 서비스입니다. 현재는 서비스 경계와 Health 계약만 제공하며,
실제 AI·문서 처리 기능을 붙일 자리를 보여 줍니다.

## 내부 Health 계약

```text
GET /internal/v1/health
→ { "status": "up", "service": "base-architecture-ai-service" }
```

이 경로는 브라우저 공개 API가 아닙니다. React는 Core API만 호출하므로 AI Service에 CORS를
추가하지 않았습니다.

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

PDF 처리, LLM 호출, 검색 등은 실제 필요가 생길 때 `app/` 아래에 유스케이스 단위로 추가하세요.

import asyncio
import json
from types import SimpleNamespace
from typing import Any

import httpx2
import pytest
from openai import AsyncOpenAI

import app.providers.search_intent as provider_module
from app.providers.search_intent import OpenAISearchIntentProvider, SYSTEM_PROMPT
from app.schemas.search_intent import ExtractedSearchIntent


class FakeResponses:
    def __init__(
        self,
        parsed: ExtractedSearchIntent | None,
        *,
        status: str = "completed",
        error: object | None = None,
    ) -> None:
        self._parsed = parsed
        self._status = status
        self._error = error
        self.arguments: dict[str, Any] | None = None

    async def parse(self, **arguments: Any) -> SimpleNamespace:
        self.arguments = arguments
        return SimpleNamespace(
            output_parsed=self._parsed,
            status=self._status,
            error=self._error,
        )


class FakeClient:
    def __init__(
        self,
        parsed: ExtractedSearchIntent | None,
        *,
        status: str = "completed",
        error: object | None = None,
    ) -> None:
        self.responses = FakeResponses(parsed, status=status, error=error)
        self.closed = False

    async def close(self) -> None:
        self.closed = True


class HangingResponses:
    async def parse(self, **arguments: Any) -> None:
        await asyncio.Event().wait()


class HangingClient:
    def __init__(self) -> None:
        self.responses = HangingResponses()

    async def close(self) -> None:
        return None


def valid_intent() -> ExtractedSearchIntent:
    return ExtractedSearchIntent(
        keywords=["반도체"],
        regions=["서울"],
        categories=["AI"],
        target_terms=["중소기업"],
        clarification_needed=False,
        clarification_question=None,
    )


def sdk_response_body(
    *,
    status: str,
    content: list[dict[str, object]],
) -> dict[str, object]:
    return {
        "id": "resp_test",
        "created_at": 0,
        "error": None,
        "incomplete_details": (
            {"reason": "max_output_tokens"} if status == "incomplete" else None
        ),
        "model": "gpt-5.6-luna",
        "object": "response",
        "output": [
            {
                "id": "msg_test",
                "content": content,
                "role": "assistant",
                "status": status,
                "type": "message",
            }
        ],
        "parallel_tool_calls": False,
        "status": status,
        "tool_choice": "none",
        "tools": [],
    }


def sdk_backed_provider(
    response_body: dict[str, object],
    captured_requests: list[dict[str, object]],
) -> tuple[OpenAISearchIntentProvider, AsyncOpenAI]:
    def handler(request: httpx2.Request) -> httpx2.Response:
        captured_requests.append(json.loads(request.content))
        return httpx2.Response(200, json=response_body)

    http_client = httpx2.AsyncClient(transport=httpx2.MockTransport(handler))
    client = AsyncOpenAI(
        api_key="test-api-key",
        base_url="https://openai.test/v1/",
        http_client=http_client,
        max_retries=0,
    )
    provider = OpenAISearchIntentProvider(
        api_key="not-used-by-injected-client",
        model="gpt-5.6-luna",
        timeout_seconds=5.0,
        client=client,
    )
    return provider, client


@pytest.mark.anyio
async def test_calls_responses_parse_with_pydantic_structured_output() -> None:
    fake_client = FakeClient(valid_intent())
    provider = OpenAISearchIntentProvider(
        api_key="not-used-by-fake",
        model="test-model",
        timeout_seconds=1.5,
        client=fake_client,
    )

    result = await provider.analyze("서울 AI 반도체")

    assert result == valid_intent()
    assert fake_client.responses.arguments == {
        "model": "test-model",
        "instructions": SYSTEM_PROMPT,
        "input": [{"role": "user", "content": "서울 AI 반도체"}],
        "text_format": ExtractedSearchIntent,
        "max_output_tokens": 500,
        "reasoning": {"effort": "none"},
        "store": False,
    }


@pytest.mark.anyio
async def test_rejects_provider_response_without_parsed_output() -> None:
    provider = OpenAISearchIntentProvider(
        api_key="not-used-by-fake",
        model="test-model",
        timeout_seconds=1.5,
        client=FakeClient(None),
    )

    with pytest.raises(ValueError, match="parsed search intent"):
        await provider.analyze("서울 AI")


@pytest.mark.anyio
@pytest.mark.parametrize(
    ("status", "error"),
    [
        ("incomplete", None),
        ("failed", SimpleNamespace(code="server_error")),
        ("completed", SimpleNamespace(code="server_error")),
    ],
)
async def test_rejects_non_completed_or_error_response_even_when_parsed(
    status: str,
    error: object | None,
) -> None:
    provider = OpenAISearchIntentProvider(
        api_key="not-used-by-fake",
        model="test-model",
        timeout_seconds=1.5,
        client=FakeClient(valid_intent(), status=status, error=error),
    )

    with pytest.raises(ValueError, match="complete successfully"):
        await provider.analyze("서울 AI")


@pytest.mark.anyio
async def test_real_sdk_rejects_incomplete_response_with_valid_parsed_json() -> None:
    parsed_json = json.dumps(valid_intent().model_dump(mode="json"), ensure_ascii=False)
    provider, _ = sdk_backed_provider(
        sdk_response_body(
            status="incomplete",
            content=[
                {
                    "annotations": [],
                    "text": parsed_json,
                    "type": "output_text",
                }
            ],
        ),
        [],
    )

    try:
        with pytest.raises(ValueError, match="complete successfully"):
            await provider.analyze("서울 AI")
    finally:
        await provider.close()


@pytest.mark.anyio
async def test_real_sdk_refusal_has_no_parsed_intent() -> None:
    provider, _ = sdk_backed_provider(
        sdk_response_body(
            status="completed",
            content=[{"refusal": "Request refused", "type": "refusal"}],
        ),
        [],
    )

    try:
        with pytest.raises(ValueError, match="parsed search intent"):
            await provider.analyze("서울 AI")
    finally:
        await provider.close()


@pytest.mark.anyio
async def test_real_sdk_sends_non_stored_strict_schema_request() -> None:
    captured_requests: list[dict[str, object]] = []
    parsed_json = json.dumps(valid_intent().model_dump(mode="json"), ensure_ascii=False)
    provider, client = sdk_backed_provider(
        sdk_response_body(
            status="completed",
            content=[
                {
                    "annotations": [],
                    "text": parsed_json,
                    "type": "output_text",
                }
            ],
        ),
        captured_requests,
    )

    try:
        assert await provider.analyze("서울 AI") == valid_intent()
    finally:
        await provider.close()

    assert client.is_closed()
    request_body = captured_requests[0]
    assert request_body["store"] is False
    text_format = request_body["text"]["format"]  # type: ignore[index]
    assert text_format["type"] == "json_schema"
    assert text_format["strict"] is True
    schema = text_format["schema"]
    assert schema["additionalProperties"] is False
    assert set(schema["required"]) == {
        "keywords",
        "regions",
        "categories",
        "target_terms",
        "clarification_needed",
        "clarification_question",
    }


@pytest.mark.anyio
async def test_enforces_whole_provider_call_deadline() -> None:
    provider = OpenAISearchIntentProvider(
        api_key="not-used-by-fake",
        model="test-model",
        timeout_seconds=0.01,
        client=HangingClient(),
    )

    with pytest.raises(TimeoutError):
        await provider.analyze("서울 AI")


@pytest.mark.anyio
async def test_closes_injected_async_client() -> None:
    client = FakeClient(valid_intent())
    provider = OpenAISearchIntentProvider(
        api_key="not-used-by-fake",
        model="test-model",
        timeout_seconds=1.5,
        client=client,
    )

    await provider.close()

    assert client.closed is True


def test_configures_official_client_with_timeout_and_no_sdk_retries(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured: dict[str, object] = {}

    def fake_openai(**arguments: object) -> object:
        captured.update(arguments)
        return object()

    monkeypatch.setattr(provider_module, "AsyncOpenAI", fake_openai)

    OpenAISearchIntentProvider(
        api_key="private-key",
        model="test-model",
        timeout_seconds=2.5,
    )

    assert captured == {
        "api_key": "private-key",
        "timeout": 2.5,
        "max_retries": 0,
    }

import asyncio
import json

import httpx2
import pytest
from agents import (
    MaxTurnsExceeded,
    ModelBehaviorError,
    ModelTracing,
    OpenAIResponsesModel,
    UserError,
)
from agents.testing import ModelStep, ScriptedModel, assistant_message
from openai import AsyncOpenAI

from app.agents.search_intent.agent import SearchIntentAgent
from app.agents.search_intent.models import ExtractedSearchIntent
from app.agents.search_intent.port import SearchIntentAnalysisError
from app.agents.search_intent.prompt import SEARCH_INTENT_INSTRUCTIONS


def valid_intent() -> ExtractedSearchIntent:
    return ExtractedSearchIntent(
        keywords=["반도체"],
        regions=["서울"],
        categories=["AI"],
        target_terms=["중소기업"],
        clarification_needed=False,
        clarification_question=None,
    )


@pytest.mark.anyio
async def test_runs_typed_agent_through_the_real_runner() -> None:
    expected = valid_intent()
    model = ScriptedModel(
        [[assistant_message(expected.model_dump_json())]]
    )
    agent = SearchIntentAgent(
        model=model,
        model_timeout_seconds=1.5,
        run_timeout_seconds=2.0,
    )

    assert await agent.analyze("서울 AI 반도체") == expected

    call = model.first_call
    assert call is not None
    assert call.system_instructions == SEARCH_INTENT_INSTRUCTIONS
    assert call.input == [{"role": "user", "content": "서울 AI 반도체"}]
    assert call.output_schema is not None
    assert call.output_schema.output_type is ExtractedSearchIntent
    assert call.output_schema.is_strict_json_schema() is True
    schema = call.output_schema.json_schema()
    assert schema["additionalProperties"] is False
    assert set(schema["required"]) == {
        "keywords",
        "regions",
        "categories",
        "target_terms",
        "clarification_needed",
        "clarification_question",
    }
    assert call.model_settings.max_tokens == 500
    assert call.model_settings.store is False
    assert call.model_settings.reasoning is not None
    assert call.model_settings.reasoning.effort == "none"
    assert call.model_settings.timeout == 1.5
    assert call.tracing is ModelTracing.DISABLED
    assert len(model.calls) == 1
    model.assert_complete()


@pytest.mark.anyio
async def test_turns_invalid_structured_output_into_boundary_error() -> None:
    model = ScriptedModel([[assistant_message("not-json")]])
    agent = SearchIntentAgent(
        model=model,
        model_timeout_seconds=1.5,
        run_timeout_seconds=2.0,
    )

    with pytest.raises(SearchIntentAnalysisError) as captured:
        await agent.analyze("서울 AI")

    assert isinstance(captured.value.__cause__, ModelBehaviorError)
    model.assert_complete()


@pytest.mark.anyio
async def test_propagates_agent_configuration_errors() -> None:
    model = ScriptedModel(
        [ModelStep.raise_error(UserError("invalid agent configuration"))]
    )
    agent = SearchIntentAgent(
        model=model,
        model_timeout_seconds=1.5,
        run_timeout_seconds=2.0,
    )

    with pytest.raises(UserError, match="invalid agent configuration"):
        await agent.analyze("서울 AI")

    model.assert_complete()


@pytest.mark.anyio
async def test_limits_the_extractor_to_one_model_turn() -> None:
    model = ScriptedModel(
        [[], [assistant_message(valid_intent().model_dump_json())]]
    )
    agent = SearchIntentAgent(
        model=model,
        model_timeout_seconds=1.5,
        run_timeout_seconds=2.0,
    )

    with pytest.raises(SearchIntentAnalysisError) as captured:
        await agent.analyze("서울 AI")

    assert isinstance(captured.value.__cause__, MaxTurnsExceeded)
    assert len(model.calls) == 1


@pytest.mark.anyio
async def test_enforces_whole_agent_run_deadline() -> None:
    async def hang_forever(_: object) -> list[object]:
        await asyncio.Event().wait()
        return []

    model = ScriptedModel([ModelStep.respond(hang_forever)])
    agent = SearchIntentAgent(
        model=model,
        model_timeout_seconds=1.0,
        run_timeout_seconds=0.01,
    )

    with pytest.raises(SearchIntentAnalysisError) as captured:
        await agent.analyze("서울 AI")

    assert isinstance(captured.value.__cause__, TimeoutError)


def responses_body(output_json: str) -> dict[str, object]:
    return {
        "id": "resp_test",
        "created_at": 0,
        "error": None,
        "incomplete_details": None,
        "model": "gpt-5.6-luna",
        "object": "response",
        "output": [
            {
                "id": "msg_test",
                "content": [
                    {
                        "annotations": [],
                        "text": output_json,
                        "type": "output_text",
                    }
                ],
                "role": "assistant",
                "status": "completed",
                "type": "message",
            }
        ],
        "parallel_tool_calls": False,
        "status": "completed",
        "tool_choice": "none",
        "tools": [],
    }


@pytest.mark.anyio
async def test_openai_model_sends_non_stored_strict_structured_output() -> None:
    captured_requests: list[dict[str, object]] = []

    def handler(request: httpx2.Request) -> httpx2.Response:
        captured_requests.append(json.loads(request.content))
        return httpx2.Response(
            200,
            json=responses_body(valid_intent().model_dump_json()),
        )

    http_client = httpx2.AsyncClient(transport=httpx2.MockTransport(handler))
    openai_client = AsyncOpenAI(
        api_key="test-api-key",
        base_url="https://openai.test/v1/",
        http_client=http_client,
        max_retries=0,
    )
    model = OpenAIResponsesModel(
        model="gpt-5.6-luna",
        openai_client=openai_client,
    )
    agent = SearchIntentAgent(
        model=model,
        model_timeout_seconds=4.0,
        run_timeout_seconds=5.0,
    )

    try:
        assert await agent.analyze("서울 AI") == valid_intent()
    finally:
        await openai_client.close()

    request_body = captured_requests[0]
    assert request_body["store"] is False
    assert request_body["max_output_tokens"] == 500
    assert request_body["reasoning"] == {"effort": "none"}
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

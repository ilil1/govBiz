import pytest
from agents.testing import ScriptedModel, assistant_message
from fastapi.testclient import TestClient

import app.bootstrap as bootstrap_module
import app.main as main_module
from app.agents.search_intent.models import (
    ExtractedSearchIntent,
    SearchIntentRequest,
)
from app.agents.search_intent.service import SearchIntentAnalysisService
from app.bootstrap import ApplicationContainer, build_application_container
from app.config import Settings


OPENAI_SETTINGS = Settings(
    openai_api_key="private-key",
    openai_model="test-model",
    llm_model_timeout_seconds=1.25,
    llm_run_timeout_seconds=1.75,
)


class FakeOpenAIClient:
    def __init__(self) -> None:
        self.closed = False

    async def close(self) -> None:
        self.closed = True


class NeverCalledAgent:
    async def analyze(self, query: str) -> ExtractedSearchIntent:
        raise AssertionError("health and lifespan tests must not invoke the agent")


@pytest.mark.anyio
async def test_builds_and_wires_agent_in_the_composition_root(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    captured_client_arguments: dict[str, object] = {}
    captured_model_arguments: dict[str, object] = {}
    client = FakeOpenAIClient()
    expected = ExtractedSearchIntent(
        keywords=["반도체"],
        regions=["서울"],
        categories=["AI"],
        target_terms=[],
        clarification_needed=False,
        clarification_question=None,
    )
    model = ScriptedModel(
        [[assistant_message(expected.model_dump_json())]]
    )

    def fake_openai_client(**arguments: object) -> FakeOpenAIClient:
        captured_client_arguments.update(arguments)
        return client

    def fake_responses_model(**arguments: object) -> ScriptedModel:
        captured_model_arguments.update(arguments)
        return model

    monkeypatch.setattr(bootstrap_module, "AsyncOpenAI", fake_openai_client)
    monkeypatch.setattr(
        bootstrap_module,
        "OpenAIResponsesModel",
        fake_responses_model,
    )

    container = build_application_container(OPENAI_SETTINGS)

    assert isinstance(container.search_intent_service, SearchIntentAnalysisService)
    assert container.openai_client is client
    assert captured_client_arguments == {
        "api_key": "private-key",
        "timeout": 1.25,
        "max_retries": 0,
    }
    assert captured_model_arguments == {
        "model": "test-model",
        "openai_client": client,
    }

    response = await container.search_intent_service.analyze(
        SearchIntentRequest(query="서울 AI 반도체", acceptingOnly=True)
    )

    assert response.keywords == ["반도체"]
    model.assert_complete()


def test_application_lifespan_closes_container_owned_client(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    client = FakeOpenAIClient()
    container = ApplicationContainer(
        search_intent_service=SearchIntentAnalysisService(NeverCalledAgent()),
        openai_client=client,  # type: ignore[arg-type]
    )

    monkeypatch.setattr(
        main_module,
        "build_application_container",
        lambda *args, **kwargs: container,
    )

    with TestClient(main_module.create_app(settings=OPENAI_SETTINGS)) as test_client:
        assert client.closed is False
        assert test_client.get("/internal/v1/health").status_code == 200

    assert client.closed is True

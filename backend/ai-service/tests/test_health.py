from fastapi.testclient import TestClient

from app.agents.search_intent.agent import SearchIntentAgent
from app.agents.search_intent.models import ExtractedSearchIntent
from app.config import Settings
from app.main import create_app


class NeverCalledAgent(SearchIntentAgent):
    def __init__(self) -> None:
        pass

    async def analyze(self, query: str) -> ExtractedSearchIntent:
        raise AssertionError("health tests must not invoke the agent")


client = TestClient(
    create_app(
        settings=Settings(
            openai_api_key="test-key",
            openai_model="test-model",
            llm_model_timeout_seconds=2.0,
            llm_run_timeout_seconds=2.5,
        ),
        search_intent_agent=NeverCalledAgent(),
    )
)


def test_returns_typed_health_response() -> None:
    response = client.get("/internal/v1/health")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("application/json")
    assert response.json() == {
        "status": "up",
        "service": "govbiz-ai-service",
    }

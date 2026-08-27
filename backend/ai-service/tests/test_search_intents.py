import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.agents.search_intent.models import (
    ExtractedSearchIntent,
    SupportCategory,
    SupportRegion,
)
from app.agents.search_intent.port import SearchIntentAnalysisError
from app.config import Settings
from app.main import create_app


TEST_SETTINGS = Settings(
    openai_api_key="test-key",
    openai_model="unused-model",
    llm_model_timeout_seconds=2.0,
    llm_run_timeout_seconds=2.5,
)


class SuccessfulAgent:
    def __init__(self) -> None:
        self.queries: list[str] = []

    async def analyze(self, query: str) -> ExtractedSearchIntent:
        self.queries.append(query)
        return ExtractedSearchIntent(
            keywords=["반도체"],
            regions=[SupportRegion.SEOUL],
            categories=[SupportCategory.AI, SupportCategory.STARTUP],
            target_terms=["창업 7년 이내 기업"],
            clarification_needed=False,
            clarification_question=None,
        )


class FailingAgent:
    async def analyze(self, query: str) -> ExtractedSearchIntent:
        raise SearchIntentAnalysisError("private agent response must not escape")


def test_rejects_terms_longer_than_core_contract() -> None:
    with pytest.raises(ValidationError):
        ExtractedSearchIntent(
            keywords=["가" * 51],
            regions=[],
            categories=[],
            target_terms=[],
            clarification_needed=False,
            clarification_question=None,
        )


def test_returns_structured_llm_intent_and_preserves_caller_filter() -> None:
    agent = SuccessfulAgent()
    client = TestClient(create_app(settings=TEST_SETTINGS, search_intent_agent=agent))

    response = client.post(
        "/internal/v1/search-intents/analyze",
        json={
            "query": "  서울의 AI 반도체 스타트업 지원사업  ",
            "acceptingOnly": False,
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "originalQuery": "서울의 AI 반도체 스타트업 지원사업",
        "keywords": ["반도체"],
        "regions": ["서울"],
        "categories": ["AI", "창업"],
        "targetTerms": ["창업 7년 이내 기업"],
        "acceptingOnly": False,
        "clarificationNeeded": False,
        "clarificationQuestion": None,
    }
    assert agent.queries == ["서울의 AI 반도체 스타트업 지원사업"]


def test_agent_failure_returns_safe_error_without_leaking_details() -> None:
    client = TestClient(
        create_app(settings=TEST_SETTINGS, search_intent_agent=FailingAgent())
    )
    response = client.post(
        "/internal/v1/search-intents/analyze",
        json={"query": "서울 수출 secret-company", "acceptingOnly": True},
    )

    assert response.status_code == 503
    assert response.json() == {
        "detail": "Search intent analysis is temporarily unavailable."
    }
    assert "private agent response" not in response.text
    assert "secret-company" not in response.text


@pytest.mark.parametrize(
    "body",
    [
        {"query": "서울 AI"},
        {"query": "   ", "acceptingOnly": True},
        {"query": "서울 AI", "acceptingOnly": True, "unknown": "value"},
        {"query": "가" * 501, "acceptingOnly": True},
    ],
)
def test_rejects_invalid_requests(body: dict[str, object]) -> None:
    client = TestClient(
        create_app(settings=TEST_SETTINGS, search_intent_agent=SuccessfulAgent())
    )

    response = client.post("/internal/v1/search-intents/analyze", json=body)

    assert response.status_code == 422


def test_exposes_camel_case_contract_in_openapi() -> None:
    client = TestClient(
        create_app(settings=TEST_SETTINGS, search_intent_agent=SuccessfulAgent())
    )

    response = client.get("/openapi.json")

    assert response.status_code == 200
    operation = response.json()["paths"][
        "/internal/v1/search-intents/analyze"
    ]["post"]
    request_schema = operation["requestBody"]["content"]["application/json"][
        "schema"
    ]
    assert request_schema["$ref"] == "#/components/schemas/SearchIntentRequest"

    schemas = response.json()["components"]["schemas"]
    assert set(schemas["SearchIntentRequest"]["required"]) == {
        "query",
        "acceptingOnly",
    }
    assert set(schemas["SearchIntentResponse"]["required"]) == {
        "originalQuery",
        "keywords",
        "regions",
        "categories",
        "targetTerms",
        "acceptingOnly",
        "clarificationNeeded",
        "clarificationQuestion",
    }


def test_llm_schema_rejects_more_than_eight_terms_per_field() -> None:
    with pytest.raises(ValueError, match="at most 8 search terms"):
        ExtractedSearchIntent(
            keywords=[f"키워드-{index}" for index in range(9)],
            regions=[],
            categories=[],
            target_terms=[],
            clarification_needed=False,
            clarification_question=None,
        )

import logging

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.config import Settings
from app.main import create_app
from app.schemas.search_intent import (
    ExtractedSearchIntent,
    SupportCategory,
    SupportRegion,
)


DISABLED_SETTINGS = Settings(
    llm_provider="disabled",
    openai_api_key=None,
    openai_model="unused-model",
    llm_timeout_seconds=2.0,
)


class SuccessfulProvider:
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


class FailingProvider:
    async def analyze(self, query: str) -> ExtractedSearchIntent:
        raise TimeoutError("private provider response must not escape")


class ClosableProvider(SuccessfulProvider):
    def __init__(self) -> None:
        super().__init__()
        self.closed = False

    async def close(self) -> None:
        self.closed = True


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
    provider = SuccessfulProvider()
    client = TestClient(create_app(search_intent_provider=provider))

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
        "analysisMode": "LLM",
    }
    assert provider.queries == ["서울의 AI 반도체 스타트업 지원사업"]


def test_application_lifespan_closes_provider() -> None:
    provider = ClosableProvider()

    with TestClient(create_app(search_intent_provider=provider)) as client:
        assert provider.closed is False
        response = client.post(
            "/internal/v1/search-intents/analyze",
            json={"query": "서울 AI", "acceptingOnly": True},
        )
        assert response.status_code == 200

    assert provider.closed is True


@pytest.mark.parametrize(
    ("query", "expected"),
    [
        (
            "서울에서 AI 창업지원 사업 찾아줘",
            {
                "regions": ["서울"],
                "categories": ["AI", "창업"],
                "targetTerms": [],
            },
        ),
        (
            "현재 접수 중인 수출 지원사업 알려줘",
            {
                "regions": [],
                "categories": ["수출"],
                "targetTerms": [],
            },
        ),
        (
            "제조기업 R&D 사업을 찾아줘",
            {
                "regions": [],
                "categories": ["제조", "기술"],
                "targetTerms": ["제조기업"],
            },
        ),
        (
            "경기도 소재 업력 7년 이내 스타트업",
            {
                "regions": ["경기"],
                "categories": ["창업"],
                "targetTerms": ["업력 7년 이내", "스타트업"],
            },
        ),
    ],
)
def test_rule_based_fallback_extracts_supported_korean_filters(
    query: str,
    expected: dict[str, list[str]],
) -> None:
    client = TestClient(create_app(settings=DISABLED_SETTINGS))

    response = client.post(
        "/internal/v1/search-intents/analyze",
        json={"query": query, "acceptingOnly": True},
    )

    assert response.status_code == 200
    document = response.json()
    assert document["analysisMode"] == "RULE_BASED_FALLBACK"
    assert document["regions"] == expected["regions"]
    assert document["categories"] == expected["categories"]
    assert document["targetTerms"] == expected["targetTerms"]
    assert document["acceptingOnly"] is True
    assert document["clarificationNeeded"] is False


def test_rule_based_fallback_requests_clarification_for_empty_intent() -> None:
    client = TestClient(create_app(settings=DISABLED_SETTINGS))

    response = client.post(
        "/internal/v1/search-intents/analyze",
        json={"query": "지원사업 추천해줘", "acceptingOnly": True},
    )

    assert response.status_code == 200
    assert response.json() == {
        "originalQuery": "지원사업 추천해줘",
        "keywords": [],
        "regions": [],
        "categories": [],
        "targetTerms": [],
        "acceptingOnly": True,
        "clarificationNeeded": True,
        "clarificationQuestion": "원하는 지역, 지원 분야 또는 기업 유형을 알려주세요.",
        "analysisMode": "RULE_BASED_FALLBACK",
    }


def test_provider_failure_returns_safe_fallback_without_leaking_details(
    caplog: pytest.LogCaptureFixture,
) -> None:
    client = TestClient(create_app(search_intent_provider=FailingProvider()))
    caplog.set_level(logging.WARNING)

    response = client.post(
        "/internal/v1/search-intents/analyze",
        json={"query": "서울 수출 secret-company", "acceptingOnly": True},
    )

    assert response.status_code == 200
    assert response.json()["analysisMode"] == "RULE_BASED_FALLBACK"
    assert "private provider response" not in caplog.text
    assert "secret-company" not in caplog.text


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
    client = TestClient(create_app(settings=DISABLED_SETTINGS))

    response = client.post("/internal/v1/search-intents/analyze", json=body)

    assert response.status_code == 422


def test_exposes_camel_case_contract_in_openapi() -> None:
    client = TestClient(create_app(settings=DISABLED_SETTINGS))

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
        "analysisMode",
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

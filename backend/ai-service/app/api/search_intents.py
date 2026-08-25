from typing import Annotated

from fastapi import APIRouter, Depends, Request

from app.agents.search_intent.models import SearchIntentRequest, SearchIntentResponse
from app.agents.search_intent.service import SearchIntentAnalysisService


router = APIRouter(prefix="/internal/v1", tags=["internal"])


def get_search_intent_service(request: Request) -> SearchIntentAnalysisService:
    return request.app.state.container.search_intent_service


@router.post(
    "/search-intents/analyze",
    response_model=SearchIntentResponse,
    summary="지원사업 검색 의도 분석",
)
async def analyze_search_intent(
    payload: SearchIntentRequest,
    service: Annotated[
        SearchIntentAnalysisService,
        Depends(get_search_intent_service),
    ],
) -> SearchIntentResponse:
    """LLM 분석 실패 여부와 관계없이 Core API가 사용할 검색 조건을 반환한다."""

    return await service.analyze(payload)

from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, status

from app.agents.search_intent.models import SearchIntentRequest, SearchIntentResponse
from app.agents.search_intent.port import SearchIntentAnalysisError
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
    """OpenAI Agent가 검증한 검색 조건을 Core API에 반환한다."""

    try:
        return await service.analyze(payload)
    except SearchIntentAnalysisError as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Search intent analysis is temporarily unavailable.",
        ) from error

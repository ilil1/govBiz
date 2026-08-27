from .agent import SearchIntentAgent
from .models import (
    ExtractedSearchIntent,
    SearchIntentRequest,
    SearchIntentResponse,
)


class SearchIntentAnalysisService:
    """필수 검색 의도 Agent를 실행하고 응답 계약을 조립한다."""

    def __init__(self, agent: SearchIntentAgent) -> None:
        self._agent = agent

    async def analyze(self, request: SearchIntentRequest) -> SearchIntentResponse:
        extracted: ExtractedSearchIntent = await self._agent.analyze(request.query)

        return SearchIntentResponse(
            original_query=request.query,
            keywords=extracted.keywords,
            regions=extracted.regions,
            categories=extracted.categories,
            target_terms=extracted.target_terms,
            accepting_only=request.accepting_only,
            clarification_needed=extracted.clarification_needed,
            clarification_question=extracted.clarification_question,
        )

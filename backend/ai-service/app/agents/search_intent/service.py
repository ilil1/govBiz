import logging

from .models import (
    AnalysisMode,
    ExtractedSearchIntent,
    SearchIntentRequest,
    SearchIntentResponse,
)
from .port import (
    SearchIntentAnalysisError,
    SearchIntentAnalyzer,
)
from .rules import extract_with_rules


logger = logging.getLogger(__name__)


class SearchIntentAnalysisService:
    """에이전트 실행과 결정적 fallback 사이의 애플리케이션 흐름."""

    def __init__(self, agent: SearchIntentAnalyzer | None = None) -> None:
        self._agent = agent

    async def analyze(self, request: SearchIntentRequest) -> SearchIntentResponse:
        analysis_mode = AnalysisMode.RULE_BASED_FALLBACK
        extracted: ExtractedSearchIntent

        if self._agent is None:
            extracted = extract_with_rules(request.query)
        else:
            try:
                extracted = await self._agent.analyze(request.query)
                analysis_mode = AnalysisMode.LLM
            except SearchIntentAnalysisError:
                logger.warning(
                    "Search intent agent failed; using rule-based fallback",
                    extra={"agent": type(self._agent).__name__},
                )
                extracted = extract_with_rules(request.query)

        return SearchIntentResponse(
            original_query=request.query,
            keywords=extracted.keywords,
            regions=extracted.regions,
            categories=extracted.categories,
            target_terms=extracted.target_terms,
            accepting_only=request.accepting_only,
            clarification_needed=extracted.clarification_needed,
            clarification_question=extracted.clarification_question,
            analysis_mode=analysis_mode,
        )

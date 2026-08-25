from typing import Protocol

from .models import ExtractedSearchIntent


class SearchIntentAnalyzer(Protocol):
    """구현 기술과 무관하게 검색 의도를 추출하는 애플리케이션 포트."""

    async def analyze(self, query: str) -> ExtractedSearchIntent: ...


class SearchIntentAnalysisError(RuntimeError):
    """외부 검색 의도 분석을 완료하지 못했을 때의 안전한 경계 오류."""

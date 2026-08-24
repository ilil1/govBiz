import logging
import re
import unicodedata
from inspect import isawaitable

from app.config import Settings
from app.providers.search_intent import (
    OpenAISearchIntentProvider,
    SearchIntentProvider,
)
from app.schemas.search_intent import (
    AnalysisMode,
    ExtractedSearchIntent,
    SearchIntentRequest,
    SearchIntentResponse,
    SupportCategory,
    SupportRegion,
)


logger = logging.getLogger(__name__)

TOKEN_SEPARATOR = re.compile(r"[^\w&]+", re.UNICODE)
BUSINESS_AGE = re.compile(
    r"(?:(?:창업|업력)\s*)?\d+\s*년\s*(?:이내|미만|이상|초과)"
)
PRE_FOUNDER = re.compile(r"예비\s*창업자")

STOP_WORDS = {
    "공고",
    "사업",
    "사업을",
    "사업이",
    "사업은",
    "사업도",
    "지원",
    "지원사업",
    "지원사업을",
    "정부지원",
    "정부지원사업",
    "찾아줘",
    "알려줘",
    "보여줘",
    "추천해줘",
    "추천",
    "해줘",
    "주세요",
    "현재",
    "접수",
    "중인",
    "가능한",
    "가능",
    "신청",
    "프로그램",
}

REGION_ALIASES = {
    "서울": SupportRegion.SEOUL,
    "서울특별시": SupportRegion.SEOUL,
    "부산": SupportRegion.BUSAN,
    "부산광역시": SupportRegion.BUSAN,
    "대구": SupportRegion.DAEGU,
    "대구광역시": SupportRegion.DAEGU,
    "인천": SupportRegion.INCHEON,
    "인천광역시": SupportRegion.INCHEON,
    "광주": SupportRegion.GWANGJU,
    "광주광역시": SupportRegion.GWANGJU,
    "대전": SupportRegion.DAEJEON,
    "대전광역시": SupportRegion.DAEJEON,
    "울산": SupportRegion.ULSAN,
    "울산광역시": SupportRegion.ULSAN,
    "세종": SupportRegion.SEJONG,
    "세종특별자치시": SupportRegion.SEJONG,
    "경기": SupportRegion.GYEONGGI,
    "경기도": SupportRegion.GYEONGGI,
    "강원": SupportRegion.GANGWON,
    "강원특별자치도": SupportRegion.GANGWON,
    "충북": SupportRegion.CHUNGBUK,
    "충청북도": SupportRegion.CHUNGBUK,
    "충남": SupportRegion.CHUNGNAM,
    "충청남도": SupportRegion.CHUNGNAM,
    "전북": SupportRegion.JEONBUK,
    "전북특별자치도": SupportRegion.JEONBUK,
    "전남": SupportRegion.JEONNAM,
    "전라남도": SupportRegion.JEONNAM,
    "경북": SupportRegion.GYEONGBUK,
    "경상북도": SupportRegion.GYEONGBUK,
    "경남": SupportRegion.GYEONGNAM,
    "경상남도": SupportRegion.GYEONGNAM,
    "제주": SupportRegion.JEJU,
    "제주특별자치도": SupportRegion.JEJU,
    "전국": SupportRegion.NATIONWIDE,
}

CATEGORY_ALIASES = {
    "ai": SupportCategory.AI,
    "인공지능": SupportCategory.AI,
    "창업": SupportCategory.STARTUP,
    "스타트업": SupportCategory.STARTUP,
    "기술": SupportCategory.TECHNOLOGY,
    "기술개발": SupportCategory.TECHNOLOGY,
    "r&d": SupportCategory.TECHNOLOGY,
    "연구개발": SupportCategory.TECHNOLOGY,
    "수출": SupportCategory.EXPORT,
    "해외진출": SupportCategory.EXPORT,
    "경영": SupportCategory.MANAGEMENT,
    "금융": SupportCategory.FINANCE,
    "융자": SupportCategory.FINANCE,
    "보증": SupportCategory.FINANCE,
    "인력": SupportCategory.WORKFORCE,
    "채용": SupportCategory.WORKFORCE,
    "고용": SupportCategory.WORKFORCE,
    "내수": SupportCategory.DOMESTIC_MARKET,
    "판로": SupportCategory.DOMESTIC_MARKET,
    "유통": SupportCategory.DOMESTIC_MARKET,
    "제조": SupportCategory.MANUFACTURING,
    "스마트공장": SupportCategory.MANUFACTURING,
    "콘텐츠": SupportCategory.CONTENT,
    "소상공인": SupportCategory.SMALL_BUSINESS,
}

REGION_SUFFIXES = (
    "지역에서",
    "지역에는",
    "지역에",
    "지역",
    "소재",
    "에서",
    "에는",
    "에",
    "의",
)
TARGET_HINTS = ("기업", "사업자", "법인", "스타트업", "창업자", "소상공인")


class SearchIntentAnalysisService:
    """LLM 분석을 시도하고 언제나 결정적 fallback을 제공한다."""

    def __init__(self, provider: SearchIntentProvider | None = None) -> None:
        self._provider = provider

    async def close(self) -> None:
        """수명주기를 제공하는 provider의 자원을 정상 종료한다."""

        if self._provider is None:
            return
        close = getattr(self._provider, "close", None)
        if not callable(close):
            return
        close_result = close()
        if isawaitable(close_result):
            await close_result

    async def analyze(self, request: SearchIntentRequest) -> SearchIntentResponse:
        analysis_mode = AnalysisMode.RULE_BASED_FALLBACK
        extracted: ExtractedSearchIntent

        if self._provider is None:
            extracted = extract_with_rules(request.query)
        else:
            try:
                extracted = await self._provider.analyze(request.query)
                analysis_mode = AnalysisMode.LLM
            except Exception:
                logger.warning(
                    "LLM search intent analysis failed; using rule-based fallback",
                    extra={"provider": type(self._provider).__name__},
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


def build_search_intent_service(settings: Settings) -> SearchIntentAnalysisService:
    provider: SearchIntentProvider | None = None
    if settings.openai_enabled:
        assert settings.openai_api_key is not None
        provider = OpenAISearchIntentProvider(
            api_key=settings.openai_api_key,
            model=settings.openai_model,
            timeout_seconds=settings.llm_timeout_seconds,
        )
    return SearchIntentAnalysisService(provider)


def extract_with_rules(query: str) -> ExtractedSearchIntent:
    """LLM을 사용할 수 없을 때 적용하는 보수적인 한국어 토큰 분석."""

    normalized_query = unicodedata.normalize("NFKC", query).casefold()
    regions: list[SupportRegion] = []
    categories: list[SupportCategory] = []
    target_terms: list[str] = []
    keywords: list[str] = []

    for match in BUSINESS_AGE.finditer(normalized_query):
        _append_unique(target_terms, _collapse_spaces(match.group()))
    for match in PRE_FOUNDER.finditer(normalized_query):
        _append_unique(target_terms, _collapse_spaces(match.group()))

    for token in TOKEN_SEPARATOR.split(normalized_query):
        if not token or token in STOP_WORDS:
            continue

        region = REGION_ALIASES.get(_remove_region_suffix(token))
        if region is not None:
            _append_unique(regions, region)
            continue

        category = _category_for(token)
        if category is not None:
            _append_unique(categories, category)

        if any(hint in token for hint in TARGET_HINTS):
            _append_unique(target_terms, token)

        if category is None and not token.isdecimal() and not _covered_by_age(token):
            _append_unique(keywords, token)

    has_filters = bool(keywords or regions or categories or target_terms)
    return ExtractedSearchIntent(
        keywords=keywords[:8],
        regions=regions[:8],
        categories=categories[:8],
        target_terms=target_terms[:8],
        clarification_needed=not has_filters,
        clarification_question=(
            None
            if has_filters
            else "원하는 지역, 지원 분야 또는 기업 유형을 알려주세요."
        ),
    )


def _remove_region_suffix(token: str) -> str:
    if token in REGION_ALIASES:
        return token
    for suffix in REGION_SUFFIXES:
        if token.endswith(suffix) and len(token) > len(suffix):
            candidate = token[: -len(suffix)]
            if candidate in REGION_ALIASES:
                return candidate
    return token


def _category_for(token: str) -> SupportCategory | None:
    direct = CATEGORY_ALIASES.get(token)
    if direct is not None:
        return direct

    stripped = token
    for suffix in ("지원사업", "사업", "지원"):
        if stripped.endswith(suffix) and len(stripped) > len(suffix):
            stripped = stripped[: -len(suffix)]
            break
    direct = CATEGORY_ALIASES.get(stripped)
    if direct is not None:
        return direct

    for alias, category in CATEGORY_ALIASES.items():
        if len(alias) >= 2 and alias in token:
            return category
    return None


def _covered_by_age(token: str) -> bool:
    return bool(re.fullmatch(r"\d+년|이내|미만|이상|초과|업력", token))


def _append_unique(values: list, value: object) -> None:
    if value not in values:
        values.append(value)


def _collapse_spaces(value: str) -> str:
    return " ".join(value.split())

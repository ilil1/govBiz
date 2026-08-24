from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class SupportRegion(StrEnum):
    """Core API가 검색할 수 있는 정규화된 광역 지역."""

    SEOUL = "서울"
    BUSAN = "부산"
    DAEGU = "대구"
    INCHEON = "인천"
    GWANGJU = "광주"
    DAEJEON = "대전"
    ULSAN = "울산"
    SEJONG = "세종"
    GYEONGGI = "경기"
    GANGWON = "강원"
    CHUNGBUK = "충북"
    CHUNGNAM = "충남"
    JEONBUK = "전북"
    JEONNAM = "전남"
    GYEONGBUK = "경북"
    GYEONGNAM = "경남"
    JEJU = "제주"
    NATIONWIDE = "전국"


class SupportCategory(StrEnum):
    """현재 Core API 검색 색인이 구분하는 지원 분야."""

    AI = "AI"
    STARTUP = "창업"
    TECHNOLOGY = "기술"
    EXPORT = "수출"
    MANAGEMENT = "경영"
    FINANCE = "금융"
    WORKFORCE = "인력"
    DOMESTIC_MARKET = "내수"
    MANUFACTURING = "제조"
    CONTENT = "콘텐츠"
    SMALL_BUSINESS = "소상공인"


class AnalysisMode(StrEnum):
    """의도 분석에 실제로 사용한 경로."""

    LLM = "LLM"
    RULE_BASED_FALLBACK = "RULE_BASED_FALLBACK"


class SearchIntentRequest(BaseModel):
    """Core API가 보내는 검색 의도 분석 요청."""

    model_config = ConfigDict(
        extra="forbid",
        frozen=True,
        populate_by_name=True,
    )

    query: str = Field(min_length=1, max_length=500)
    accepting_only: bool = Field(alias="acceptingOnly")

    @field_validator("query", mode="before")
    @classmethod
    def strip_query(cls, value: object) -> object:
        return value.strip() if isinstance(value, str) else value


class ExtractedSearchIntent(BaseModel):
    """LLM provider가 Structured Output으로 반환해야 하는 엄격한 스키마."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    keywords: list[str]
    regions: list[SupportRegion]
    categories: list[SupportCategory]
    target_terms: list[str]
    clarification_needed: bool
    clarification_question: str | None

    @field_validator("keywords", "target_terms")
    @classmethod
    def validate_terms(cls, values: list[str]) -> list[str]:
        normalized: list[str] = []
        seen: set[str] = set()
        for value in values:
            term = value.strip()
            if not term or len(term) > 50:
                raise ValueError("search terms must contain 1 to 50 characters")
            key = term.casefold()
            if key not in seen:
                seen.add(key)
                normalized.append(term)
        if len(normalized) > 8:
            raise ValueError("at most 8 search terms are allowed")
        return normalized

    @field_validator("regions", "categories")
    @classmethod
    def deduplicate_enums(cls, values: list[StrEnum]) -> list[StrEnum]:
        normalized = list(dict.fromkeys(values))
        if len(normalized) > 8:
            raise ValueError("at most 8 normalized filters are allowed")
        return normalized

    @field_validator("clarification_question")
    @classmethod
    def normalize_question(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        if not normalized or len(normalized) > 200:
            raise ValueError("clarification question must contain 1 to 200 characters")
        return normalized

    @model_validator(mode="after")
    def validate_clarification(self) -> "ExtractedSearchIntent":
        if self.clarification_needed != (self.clarification_question is not None):
            raise ValueError(
                "clarificationQuestion must be present exactly when clarification is needed"
            )
        return self


class SearchIntentResponse(BaseModel):
    """AI Service와 Core API 사이의 검색 의도 분석 응답 계약."""

    model_config = ConfigDict(
        extra="forbid",
        frozen=True,
        populate_by_name=True,
    )

    original_query: str = Field(alias="originalQuery")
    keywords: list[str]
    regions: list[SupportRegion]
    categories: list[SupportCategory]
    target_terms: list[str] = Field(alias="targetTerms")
    accepting_only: bool = Field(alias="acceptingOnly")
    clarification_needed: bool = Field(alias="clarificationNeeded")
    clarification_question: str | None = Field(alias="clarificationQuestion")
    analysis_mode: AnalysisMode = Field(alias="analysisMode")

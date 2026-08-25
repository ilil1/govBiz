from dataclasses import dataclass
from os import environ


DEFAULT_OPENAI_MODEL = "gpt-5.6-luna"
DEFAULT_LLM_MODEL_TIMEOUT_SECONDS = 2.0
DEFAULT_LLM_RUN_TIMEOUT_SECONDS = 2.5


@dataclass(frozen=True, slots=True)
class Settings:
    """환경변수에서 읽는 검색 의도 agent 설정."""

    llm_provider: str
    openai_api_key: str | None
    openai_model: str
    llm_model_timeout_seconds: float
    llm_run_timeout_seconds: float

    @classmethod
    def from_environment(cls) -> "Settings":
        legacy_run_timeout = environ.get("LLM_TIMEOUT_SECONDS")
        return cls(
            llm_provider=environ.get("LLM_PROVIDER", "disabled").strip().lower(),
            openai_api_key=_optional_value(environ.get("OPENAI_API_KEY")),
            openai_model=(
                environ.get("OPENAI_MODEL", DEFAULT_OPENAI_MODEL).strip()
                or DEFAULT_OPENAI_MODEL
            ),
            llm_model_timeout_seconds=_positive_float(
                environ.get("LLM_MODEL_TIMEOUT_SECONDS"),
                default=DEFAULT_LLM_MODEL_TIMEOUT_SECONDS,
            ),
            llm_run_timeout_seconds=_positive_float(
                environ.get("LLM_RUN_TIMEOUT_SECONDS", legacy_run_timeout),
                default=DEFAULT_LLM_RUN_TIMEOUT_SECONDS,
            ),
        )

    @property
    def openai_enabled(self) -> bool:
        return self.llm_provider == "openai" and self.openai_api_key is not None


def _optional_value(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    return normalized or None


def _positive_float(value: str | None, *, default: float) -> float:
    if value is None:
        return default
    try:
        parsed = float(value)
    except ValueError:
        return default
    return parsed if 0 < parsed <= 30 else default

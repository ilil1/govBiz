import pytest

from app.config import DEFAULT_LLM_TIMEOUT_SECONDS, DEFAULT_OPENAI_MODEL, Settings


def test_reads_trimmed_openai_settings(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_PROVIDER", " OpenAI ")
    monkeypatch.setenv("OPENAI_API_KEY", " private-key ")
    monkeypatch.setenv("OPENAI_MODEL", " test-model ")
    monkeypatch.setenv("LLM_TIMEOUT_SECONDS", "1.75")

    settings = Settings.from_environment()

    assert settings.llm_provider == "openai"
    assert settings.openai_api_key == "private-key"
    assert settings.openai_model == "test-model"
    assert settings.llm_timeout_seconds == 1.75
    assert settings.openai_enabled is True


@pytest.mark.parametrize("value", [None, "", "0", "-1", "invalid", "31"])
def test_uses_safe_timeout_default(
    monkeypatch: pytest.MonkeyPatch,
    value: str | None,
) -> None:
    if value is None:
        monkeypatch.delenv("LLM_TIMEOUT_SECONDS", raising=False)
    else:
        monkeypatch.setenv("LLM_TIMEOUT_SECONDS", value)

    settings = Settings.from_environment()

    assert settings.llm_timeout_seconds == DEFAULT_LLM_TIMEOUT_SECONDS


def test_defaults_to_disabled_without_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in ("LLM_PROVIDER", "OPENAI_API_KEY", "OPENAI_MODEL"):
        monkeypatch.delenv(name, raising=False)

    settings = Settings.from_environment()

    assert settings.llm_provider == "disabled"
    assert settings.openai_api_key is None
    assert settings.openai_model == DEFAULT_OPENAI_MODEL
    assert settings.openai_enabled is False


def test_openai_provider_stays_disabled_for_blank_key(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("LLM_PROVIDER", "openai")
    monkeypatch.setenv("OPENAI_API_KEY", "   ")

    assert Settings.from_environment().openai_enabled is False

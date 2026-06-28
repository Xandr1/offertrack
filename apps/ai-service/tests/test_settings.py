from app.settings import (
    DEFAULT_MAX_JOB_TEXT_CHARS,
    DEFAULT_MAX_RESPONSE_BYTES,
    DEFAULT_OPENAI_MODEL,
    DEFAULT_OPENAI_TIMEOUT_SECONDS,
    Settings,
)


def test_default_model_is_gpt_5_4_mini_when_not_set(monkeypatch) -> None:
    monkeypatch.delenv("OPENAI_MODEL", raising=False)
    monkeypatch.setenv("OPENAI_API_KEY", "")

    settings = Settings.from_env()

    assert settings.openai_model == DEFAULT_OPENAI_MODEL
    assert settings.openai_model == "gpt-5.4-mini"


def test_internal_api_key_is_blank_when_not_configured(monkeypatch) -> None:
    monkeypatch.delenv("AI_SERVICE_INTERNAL_API_KEY", raising=False)

    settings = Settings.from_env()

    assert settings.internal_api_key == ""


def test_reads_internal_api_key_from_env(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_INTERNAL_API_KEY", " test-key ")

    settings = Settings.from_env()

    assert settings.internal_api_key == "test-key"


def test_default_openai_timeout_seconds_when_not_set(monkeypatch) -> None:
    monkeypatch.delenv("OPENAI_TIMEOUT_SECONDS", raising=False)

    settings = Settings.from_env()

    assert settings.openai_timeout_seconds == DEFAULT_OPENAI_TIMEOUT_SECONDS


def test_reads_positive_openai_timeout_seconds(monkeypatch) -> None:
    monkeypatch.setenv("OPENAI_TIMEOUT_SECONDS", "12.5")

    settings = Settings.from_env()

    assert settings.openai_timeout_seconds == 12.5


def test_ignores_invalid_openai_timeout_seconds(monkeypatch) -> None:
    monkeypatch.setenv("OPENAI_TIMEOUT_SECONDS", "-1")

    settings = Settings.from_env()

    assert settings.openai_timeout_seconds == DEFAULT_OPENAI_TIMEOUT_SECONDS


def test_fetch_and_text_limits_default_to_safe_values(monkeypatch) -> None:
    monkeypatch.delenv("AI_SERVICE_MAX_RESPONSE_BYTES", raising=False)
    monkeypatch.delenv("AI_SERVICE_MAX_JOB_TEXT_CHARS", raising=False)

    settings = Settings.from_env()

    assert settings.max_response_bytes == DEFAULT_MAX_RESPONSE_BYTES
    assert settings.max_response_bytes == 10_000_000
    assert settings.max_job_text_chars == DEFAULT_MAX_JOB_TEXT_CHARS
    assert settings.max_job_text_chars == 18_000


def test_reads_fetch_and_text_limits_from_env(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_MAX_RESPONSE_BYTES", "12000000")
    monkeypatch.setenv("AI_SERVICE_MAX_JOB_TEXT_CHARS", "15000")

    settings = Settings.from_env()

    assert settings.max_response_bytes == 12_000_000
    assert settings.max_job_text_chars == 15_000


def test_ignores_invalid_fetch_and_text_limits(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_MAX_RESPONSE_BYTES", "invalid")
    monkeypatch.setenv("AI_SERVICE_MAX_JOB_TEXT_CHARS", "0")

    settings = Settings.from_env()

    assert settings.max_response_bytes == DEFAULT_MAX_RESPONSE_BYTES
    assert settings.max_job_text_chars == DEFAULT_MAX_JOB_TEXT_CHARS

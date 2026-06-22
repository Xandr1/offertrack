from app.settings import DEFAULT_OPENAI_MODEL, DEFAULT_OPENAI_TIMEOUT_SECONDS, Settings


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


def test_browser_settings_default_to_safe_values(monkeypatch) -> None:
    monkeypatch.delenv("AI_SERVICE_BROWSER_TIMEOUT_SECONDS", raising=False)
    monkeypatch.delenv("AI_SERVICE_BROWSER_MAX_RESPONSE_BYTES", raising=False)
    monkeypatch.delenv("AI_SERVICE_BROWSER_MIN_TEXT_LENGTH", raising=False)
    monkeypatch.delenv("AI_SERVICE_BROWSER_MAX_CONCURRENCY", raising=False)

    settings = Settings.from_env()

    assert settings.browser_timeout_seconds == 12.0
    assert settings.browser_max_response_bytes == 1_000_000
    assert settings.browser_min_text_length == 500
    assert settings.browser_max_concurrency == 1


def test_reads_browser_settings_from_env(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_BROWSER_TIMEOUT_SECONDS", "8.5")
    monkeypatch.setenv("AI_SERVICE_BROWSER_MAX_RESPONSE_BYTES", "2000000")
    monkeypatch.setenv("AI_SERVICE_BROWSER_MIN_TEXT_LENGTH", "750")
    monkeypatch.setenv("AI_SERVICE_BROWSER_MAX_CONCURRENCY", "2")

    settings = Settings.from_env()

    assert settings.browser_timeout_seconds == 8.5
    assert settings.browser_max_response_bytes == 2_000_000
    assert settings.browser_min_text_length == 750
    assert settings.browser_max_concurrency == 2


def test_ignores_invalid_browser_settings(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_BROWSER_TIMEOUT_SECONDS", "nan")
    monkeypatch.setenv("AI_SERVICE_BROWSER_MAX_RESPONSE_BYTES", "0")
    monkeypatch.setenv("AI_SERVICE_BROWSER_MIN_TEXT_LENGTH", "-1")
    monkeypatch.setenv("AI_SERVICE_BROWSER_MAX_CONCURRENCY", "1.5")

    settings = Settings.from_env()

    assert settings.browser_timeout_seconds == 12.0
    assert settings.browser_max_response_bytes == 1_000_000
    assert settings.browser_min_text_length == 500
    assert settings.browser_max_concurrency == 1

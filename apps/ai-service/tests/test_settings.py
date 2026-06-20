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

import pytest

from app.settings import (
    DEFAULT_ALLOWED_HOSTS,
    DEFAULT_FETCH_TIMEOUT_SECONDS,
    DEFAULT_MAX_JOB_TEXT_CHARS,
    DEFAULT_MAX_REDIRECTS,
    DEFAULT_MAX_RESPONSE_BYTES,
    DEFAULT_OPENAI_MODEL,
    DEFAULT_OPENAI_TIMEOUT_SECONDS,
    LOCAL_INTERNAL_API_KEY,
    Settings,
)


@pytest.fixture(autouse=True)
def clear_settings_environment(monkeypatch) -> None:
    for name in (
        "APP_ENV",
        "OPENAI_API_KEY",
        "OPENAI_MODEL",
        "OPENAI_TIMEOUT_SECONDS",
        "AI_SERVICE_INTERNAL_API_KEY",
        "AI_SERVICE_FETCH_TIMEOUT_SECONDS",
        "AI_SERVICE_MAX_RESPONSE_BYTES",
        "AI_SERVICE_MAX_JOB_TEXT_CHARS",
        "AI_SERVICE_MAX_REDIRECTS",
        "AI_SERVICE_ALLOWED_HOSTS",
        "AI_SERVICE_DOCS_ENABLED",
    ):
        monkeypatch.delenv(name, raising=False)


def test_default_model_is_gpt_5_4_mini_when_not_set(monkeypatch) -> None:
    settings = Settings.from_env()

    assert settings.openai_model == DEFAULT_OPENAI_MODEL
    assert settings.openai_model == "gpt-5.4-mini"


def test_internal_api_key_is_blank_when_not_configured(monkeypatch) -> None:
    settings = Settings.from_env()

    assert settings.internal_api_key == ""


def test_reads_internal_api_key_from_env(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_INTERNAL_API_KEY", " test-key ")

    settings = Settings.from_env()

    assert settings.internal_api_key == "test-key"


def test_default_openai_timeout_seconds_when_not_set(monkeypatch) -> None:
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


def test_local_defaults_keep_docs_and_safe_trusted_hosts_enabled() -> None:
    settings = Settings.from_env()

    assert settings.app_env == "local"
    assert settings.docs_enabled is True
    assert settings.trusted_hosts == DEFAULT_ALLOWED_HOSTS
    assert settings.fetch_timeout_seconds == DEFAULT_FETCH_TIMEOUT_SECONDS
    assert settings.max_redirects == DEFAULT_MAX_REDIRECTS


def test_blank_compose_overrides_preserve_environment_sensitive_defaults(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_ALLOWED_HOSTS", "")
    monkeypatch.setenv("AI_SERVICE_DOCS_ENABLED", "")

    settings = Settings.from_env()

    assert settings.docs_enabled is True
    assert settings.trusted_hosts == DEFAULT_ALLOWED_HOSTS


def test_reads_fetch_timeout_redirect_limit_hosts_and_docs(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_FETCH_TIMEOUT_SECONDS", "7.5")
    monkeypatch.setenv("AI_SERVICE_MAX_REDIRECTS", "3")
    monkeypatch.setenv("AI_SERVICE_ALLOWED_HOSTS", " AI.EXAMPLE.COM , api.example.com ")
    monkeypatch.setenv("AI_SERVICE_DOCS_ENABLED", "false")

    settings = Settings.from_env()

    assert settings.fetch_timeout_seconds == 7.5
    assert settings.max_redirects == 3
    assert settings.trusted_hosts == ("ai.example.com", "api.example.com")
    assert settings.docs_enabled is False


@pytest.mark.parametrize("app_env", ["staging", "production"])
def test_protected_config_accepts_explicit_safe_values(monkeypatch, app_env: str) -> None:
    _set_protected_env(monkeypatch, app_env)

    settings = Settings.from_env()

    assert settings.app_env == app_env
    assert settings.docs_enabled is False
    assert settings.trusted_hosts == ("ai.example.com",)


def test_protected_docs_can_be_explicitly_enabled(monkeypatch) -> None:
    _set_protected_env(monkeypatch)
    monkeypatch.setenv("AI_SERVICE_DOCS_ENABLED", "true")

    settings = Settings.from_env()

    assert settings.docs_enabled is True


def test_protected_config_requires_openai_api_key(monkeypatch) -> None:
    _set_protected_env(monkeypatch)
    monkeypatch.delenv("OPENAI_API_KEY")

    with pytest.raises(ValueError, match="OPENAI_API_KEY is required"):
        Settings.from_env()


@pytest.mark.parametrize(
    "internal_api_key",
    ["", LOCAL_INTERNAL_API_KEY, "change-me", "short-protected-secret"],
)
def test_protected_config_rejects_missing_placeholder_and_short_internal_keys(
    monkeypatch,
    internal_api_key: str,
) -> None:
    _set_protected_env(monkeypatch)
    monkeypatch.setenv("AI_SERVICE_INTERNAL_API_KEY", internal_api_key)

    with pytest.raises(ValueError, match="AI_SERVICE_INTERNAL_API_KEY"):
        Settings.from_env()


@pytest.mark.parametrize(
    "allowed_hosts", [None, "", "*", "*.example.com", "https://ai.example.com"]
)
def test_protected_config_requires_explicit_non_wildcard_trusted_hosts(
    monkeypatch,
    allowed_hosts: str | None,
) -> None:
    _set_protected_env(monkeypatch)
    if allowed_hosts is None:
        monkeypatch.delenv("AI_SERVICE_ALLOWED_HOSTS")
    else:
        monkeypatch.setenv("AI_SERVICE_ALLOWED_HOSTS", allowed_hosts)

    with pytest.raises(ValueError, match="AI_SERVICE_ALLOWED_HOSTS"):
        Settings.from_env()


@pytest.mark.parametrize(
    ("name", "value"),
    [
        ("OPENAI_TIMEOUT_SECONDS", "0"),
        ("OPENAI_TIMEOUT_SECONDS", "nan"),
        ("AI_SERVICE_FETCH_TIMEOUT_SECONDS", "invalid"),
        ("AI_SERVICE_MAX_RESPONSE_BYTES", "-1"),
        ("AI_SERVICE_MAX_JOB_TEXT_CHARS", "0"),
        ("AI_SERVICE_MAX_REDIRECTS", "invalid"),
        ("AI_SERVICE_DOCS_ENABLED", "sometimes"),
    ],
)
def test_protected_config_rejects_invalid_explicit_values(
    monkeypatch,
    name: str,
    value: str,
) -> None:
    _set_protected_env(monkeypatch)
    monkeypatch.setenv(name, value)

    with pytest.raises(ValueError, match=name):
        Settings.from_env()


def test_rejects_unknown_app_environment(monkeypatch) -> None:
    monkeypatch.setenv("APP_ENV", "prod")

    with pytest.raises(ValueError, match="APP_ENV must be one of"):
        Settings.from_env()


def _set_protected_env(monkeypatch, app_env: str = "production") -> None:
    monkeypatch.setenv("APP_ENV", app_env)
    monkeypatch.setenv("OPENAI_API_KEY", "sk-protected-test-key")
    monkeypatch.setenv("AI_SERVICE_INTERNAL_API_KEY", "a" * 32)
    monkeypatch.setenv("AI_SERVICE_ALLOWED_HOSTS", "ai.example.com")

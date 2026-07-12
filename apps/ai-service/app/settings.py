import math
import os
import re
from dataclasses import dataclass

DEFAULT_APP_ENV = "local"
DEFAULT_OPENAI_MODEL = "gpt-5.4-mini"
DEFAULT_OPENAI_TIMEOUT_SECONDS = 30.0
DEFAULT_FETCH_TIMEOUT_SECONDS = 10.0
DEFAULT_MAX_RESPONSE_BYTES = 10_000_000
DEFAULT_MAX_JOB_TEXT_CHARS = 18_000
DEFAULT_MAX_REDIRECTS = 5
DEFAULT_ALLOWED_HOSTS = ("localhost", "127.0.0.1", "testserver")

PROTECTED_APP_ENVS = frozenset({"staging", "production"})
VALID_APP_ENVS = frozenset({"local", "test", *PROTECTED_APP_ENVS})
LOCAL_INTERNAL_API_KEY = "local-dev-ai-service-key"
KNOWN_INTERNAL_API_KEY_PLACEHOLDERS = frozenset(
    {
        LOCAL_INTERNAL_API_KEY,
        "change-me",
        "changeme",
        "replace-me",
        "your-api-key",
    }
)

_TRUSTED_HOST_PATTERN = re.compile(
    r"^(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(?:\."
    r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$"
)


@dataclass(frozen=True)
class Settings:
    openai_api_key: str
    openai_model: str
    app_env: str = DEFAULT_APP_ENV
    internal_api_key: str = ""
    openai_timeout_seconds: float = DEFAULT_OPENAI_TIMEOUT_SECONDS
    fetch_timeout_seconds: float = DEFAULT_FETCH_TIMEOUT_SECONDS
    max_response_bytes: int = DEFAULT_MAX_RESPONSE_BYTES
    max_job_text_chars: int = DEFAULT_MAX_JOB_TEXT_CHARS
    max_redirects: int = DEFAULT_MAX_REDIRECTS
    allowed_hosts: tuple[str, ...] | None = None
    docs_enabled: bool | None = None

    def __post_init__(self) -> None:
        app_env = self.app_env.strip().lower()
        openai_api_key = self.openai_api_key.strip()
        internal_api_key = self.internal_api_key.strip()
        object.__setattr__(self, "app_env", app_env)
        object.__setattr__(self, "openai_api_key", openai_api_key)
        object.__setattr__(self, "internal_api_key", internal_api_key)

        if app_env not in VALID_APP_ENVS:
            allowed_values = ", ".join(sorted(VALID_APP_ENVS))
            raise ValueError(f"APP_ENV must be one of: {allowed_values}.")

        _require_positive_number("OPENAI_TIMEOUT_SECONDS", self.openai_timeout_seconds)
        _require_positive_number("AI_SERVICE_FETCH_TIMEOUT_SECONDS", self.fetch_timeout_seconds)
        _require_positive_integer("AI_SERVICE_MAX_RESPONSE_BYTES", self.max_response_bytes)
        _require_positive_integer("AI_SERVICE_MAX_JOB_TEXT_CHARS", self.max_job_text_chars)
        _require_positive_integer("AI_SERVICE_MAX_REDIRECTS", self.max_redirects)

        allowed_hosts = self.allowed_hosts
        if allowed_hosts is None:
            if self.is_protected:
                raise ValueError("AI_SERVICE_ALLOWED_HOSTS is required in protected environments.")
            allowed_hosts = DEFAULT_ALLOWED_HOSTS

        normalized_hosts = tuple(host.strip().lower() for host in allowed_hosts if host.strip())
        if not normalized_hosts:
            raise ValueError("AI_SERVICE_ALLOWED_HOSTS must contain at least one host.")

        for host in normalized_hosts:
            if "*" in host or _TRUSTED_HOST_PATTERN.fullmatch(host) is None:
                raise ValueError(
                    "AI_SERVICE_ALLOWED_HOSTS must contain only explicit host names without "
                    "schemes, ports, paths, or wildcards."
                )

        object.__setattr__(self, "allowed_hosts", normalized_hosts)

        docs_enabled = self.docs_enabled
        if docs_enabled is None:
            docs_enabled = not self.is_protected
        object.__setattr__(self, "docs_enabled", docs_enabled)

        if self.is_protected:
            self._validate_protected()

    @property
    def is_protected(self) -> bool:
        return self.app_env in PROTECTED_APP_ENVS

    @property
    def trusted_hosts(self) -> tuple[str, ...]:
        assert self.allowed_hosts is not None
        return self.allowed_hosts

    def _validate_protected(self) -> None:
        if not self.openai_api_key:
            raise ValueError("OPENAI_API_KEY is required in protected environments.")

        if not self.internal_api_key:
            raise ValueError("AI_SERVICE_INTERNAL_API_KEY is required in protected environments.")

        if self.internal_api_key.lower() in KNOWN_INTERNAL_API_KEY_PLACEHOLDERS:
            raise ValueError(
                "AI_SERVICE_INTERNAL_API_KEY must not use a known placeholder in protected "
                "environments."
            )

        if len(self.internal_api_key) < 32:
            raise ValueError(
                "AI_SERVICE_INTERNAL_API_KEY must contain at least 32 characters in protected "
                "environments."
            )

    @classmethod
    def from_env(cls) -> "Settings":
        app_env = os.getenv("APP_ENV", DEFAULT_APP_ENV).strip().lower() or DEFAULT_APP_ENV
        strict = app_env in PROTECTED_APP_ENVS

        return cls(
            app_env=app_env,
            openai_api_key=os.getenv("OPENAI_API_KEY", "").strip(),
            openai_model=os.getenv("OPENAI_MODEL", "").strip() or DEFAULT_OPENAI_MODEL,
            internal_api_key=os.getenv("AI_SERVICE_INTERNAL_API_KEY", "").strip(),
            openai_timeout_seconds=_float_from_env(
                "OPENAI_TIMEOUT_SECONDS",
                DEFAULT_OPENAI_TIMEOUT_SECONDS,
                strict=strict,
            ),
            fetch_timeout_seconds=_float_from_env(
                "AI_SERVICE_FETCH_TIMEOUT_SECONDS",
                DEFAULT_FETCH_TIMEOUT_SECONDS,
                strict=strict,
            ),
            max_response_bytes=_int_from_env(
                "AI_SERVICE_MAX_RESPONSE_BYTES",
                DEFAULT_MAX_RESPONSE_BYTES,
                strict=strict,
            ),
            max_job_text_chars=_int_from_env(
                "AI_SERVICE_MAX_JOB_TEXT_CHARS",
                DEFAULT_MAX_JOB_TEXT_CHARS,
                strict=strict,
            ),
            max_redirects=_int_from_env(
                "AI_SERVICE_MAX_REDIRECTS",
                DEFAULT_MAX_REDIRECTS,
                strict=strict,
            ),
            allowed_hosts=_hosts_from_env(),
            docs_enabled=_bool_from_env("AI_SERVICE_DOCS_ENABLED", strict=strict),
        )


def _float_from_env(name: str, default: float, *, strict: bool) -> float:
    raw_value = os.getenv(name, "").strip()

    if not raw_value:
        return default

    try:
        value = float(raw_value)
    except ValueError:
        if strict:
            raise ValueError(f"{name} must be a positive number.") from None
        return default

    if math.isfinite(value) and value > 0:
        return value

    if strict:
        raise ValueError(f"{name} must be a positive number.")
    return default


def _int_from_env(name: str, default: int, *, strict: bool) -> int:
    raw_value = os.getenv(name, "").strip()

    if not raw_value:
        return default

    try:
        value = int(raw_value)
    except ValueError:
        if strict:
            raise ValueError(f"{name} must be a positive integer.") from None
        return default

    if value > 0:
        return value

    if strict:
        raise ValueError(f"{name} must be a positive integer.")
    return default


def _bool_from_env(name: str, *, strict: bool) -> bool | None:
    raw_value = os.getenv(name, "").strip().lower()
    if not raw_value:
        return None
    if raw_value in {"true", "1", "yes", "on"}:
        return True
    if raw_value in {"false", "0", "no", "off"}:
        return False
    if strict:
        raise ValueError(f"{name} must be true or false.")
    return None


def _hosts_from_env() -> tuple[str, ...] | None:
    raw_value = os.getenv("AI_SERVICE_ALLOWED_HOSTS")
    if raw_value is None or not raw_value.strip():
        return None
    return tuple(raw_value.split(","))


def _require_positive_number(name: str, value: float) -> None:
    if not math.isfinite(value) or value <= 0:
        raise ValueError(f"{name} must be a positive number.")


def _require_positive_integer(name: str, value: int) -> None:
    if not isinstance(value, int) or isinstance(value, bool) or value <= 0:
        raise ValueError(f"{name} must be a positive integer.")

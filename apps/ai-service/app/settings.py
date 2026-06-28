import math
import os
from dataclasses import dataclass

DEFAULT_OPENAI_MODEL = "gpt-5.4-mini"
DEFAULT_OPENAI_TIMEOUT_SECONDS = 30.0
DEFAULT_MAX_RESPONSE_BYTES = 10_000_000
DEFAULT_MAX_JOB_TEXT_CHARS = 18_000


@dataclass(frozen=True)
class Settings:
    openai_api_key: str
    openai_model: str
    internal_api_key: str = ""
    openai_timeout_seconds: float = DEFAULT_OPENAI_TIMEOUT_SECONDS
    fetch_timeout_seconds: float = 10.0
    max_response_bytes: int = DEFAULT_MAX_RESPONSE_BYTES
    max_job_text_chars: int = DEFAULT_MAX_JOB_TEXT_CHARS
    max_redirects: int = 5

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            openai_api_key=os.getenv("OPENAI_API_KEY", "").strip(),
            openai_model=os.getenv("OPENAI_MODEL", "").strip() or DEFAULT_OPENAI_MODEL,
            internal_api_key=os.getenv("AI_SERVICE_INTERNAL_API_KEY", "").strip(),
            openai_timeout_seconds=_float_from_env(
                "OPENAI_TIMEOUT_SECONDS", DEFAULT_OPENAI_TIMEOUT_SECONDS
            ),
            max_response_bytes=_int_from_env(
                "AI_SERVICE_MAX_RESPONSE_BYTES", DEFAULT_MAX_RESPONSE_BYTES
            ),
            max_job_text_chars=_int_from_env(
                "AI_SERVICE_MAX_JOB_TEXT_CHARS", DEFAULT_MAX_JOB_TEXT_CHARS
            ),
        )


def _float_from_env(name: str, default: float) -> float:
    raw_value = os.getenv(name, "").strip()

    if not raw_value:
        return default

    try:
        value = float(raw_value)
    except ValueError:
        return default

    return value if math.isfinite(value) and value > 0 else default


def _int_from_env(name: str, default: int) -> int:
    raw_value = os.getenv(name, "").strip()

    if not raw_value:
        return default

    try:
        value = int(raw_value)
    except ValueError:
        return default

    return value if value > 0 else default

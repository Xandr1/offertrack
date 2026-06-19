import os
from dataclasses import dataclass

DEFAULT_OPENAI_MODEL = "gpt-5.4-mini"


@dataclass(frozen=True)
class Settings:
  openai_api_key: str
  openai_model: str
  fetch_timeout_seconds: float = 10.0
  max_response_bytes: int = 1_000_000
  max_redirects: int = 5

  @classmethod
  def from_env(cls) -> "Settings":
    return cls(
        openai_api_key=os.getenv("OPENAI_API_KEY", "").strip(),
        openai_model=os.getenv("OPENAI_MODEL", "").strip() or DEFAULT_OPENAI_MODEL,
    )

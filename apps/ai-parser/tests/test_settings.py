from app.settings import DEFAULT_OPENAI_MODEL, Settings


def test_default_model_is_gpt_5_4_mini_when_not_set(monkeypatch) -> None:
  monkeypatch.delenv("OPENAI_MODEL", raising=False)
  monkeypatch.setenv("OPENAI_API_KEY", "")

  settings = Settings.from_env()

  assert settings.openai_model == DEFAULT_OPENAI_MODEL
  assert settings.openai_model == "gpt-5.4-mini"

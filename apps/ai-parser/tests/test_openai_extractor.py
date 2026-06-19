from app.openai_extractor import OpenAiDraftExtractor
from app.settings import Settings


def test_passes_configured_timeout_to_openai_client(monkeypatch) -> None:
    created_client: dict[str, object] = {}

    class FakeOpenAI:
        def __init__(self, api_key: str, timeout: float) -> None:
            created_client["api_key"] = api_key
            created_client["timeout"] = timeout

    monkeypatch.setattr("app.openai_extractor.OpenAI", FakeOpenAI)

    extractor = OpenAiDraftExtractor(
        Settings(
            openai_api_key="test-key",
            openai_model="test-model",
            openai_timeout_seconds=12.5,
        )
    )

    assert isinstance(extractor.client, FakeOpenAI)
    assert created_client == {"api_key": "test-key", "timeout": 12.5}

import logging
import re
from uuid import UUID

from fastapi.testclient import TestClient

from app.fetcher import FetchResult, JobFetchError, JobFetchTimeoutError
from app.main import _request_id, _safe_log_host, create_app
from app.models import ExtractedDraft, ExtractedInterview
from app.openai_extractor import OpenAiTimeoutError
from app.settings import Settings

TEST_INTERNAL_API_KEY = "test-internal-key"


class FakeFetcher:
    def __init__(self, body: bytes) -> None:
        self.body = body

    async def fetch(self, job_url: str) -> FetchResult:
        return FetchResult(url=job_url, body=self.body, content_type="text/html")


class FailingFetcher:
    def __init__(self, exception: Exception) -> None:
        self.exception = exception

    async def fetch(self, job_url: str) -> FetchResult:
        raise self.exception


class FakeExtractor:
    def __init__(self, extracted: ExtractedDraft) -> None:
        self.extracted = extracted

    def extract(self, job_text: str) -> ExtractedDraft:
        return self.extracted


class CapturingExtractor(FakeExtractor):
    def __init__(self, extracted: ExtractedDraft) -> None:
        super().__init__(extracted)
        self.job_text: str | None = None

    def extract(self, job_text: str) -> ExtractedDraft:
        self.job_text = job_text
        return super().extract(job_text)


class InvalidExtractor:
    def extract(self, job_text: str) -> ExtractedDraft:
        return ExtractedDraft.model_validate(
            {
                "company_name": "Acme",
                "position_title": "Backend Engineer",
                "work_mode": "space",
                "notes": "Invalid work mode should fail validation.",
            }
        )


class TimeoutExtractor:
    def extract(self, job_text: str) -> ExtractedDraft:
        raise OpenAiTimeoutError("OpenAI timed out.")


def test_health_does_not_require_internal_api_key() -> None:
    client = _client(
        b"<html><body>Acme Backend Engineer remote role.</body></html>",
        _successful_extractor(),
    )

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_rejects_untrusted_host() -> None:
    client = _client(
        b"<html><body>Acme Backend Engineer remote role.</body></html>",
        _successful_extractor(),
    )

    response = client.get("/health", headers={"Host": "attacker.example"})

    assert response.status_code == 400
    assert response.text == "Invalid host header"


def test_accepts_explicit_trusted_host() -> None:
    settings = Settings(
        openai_api_key="",
        openai_model="test-model",
        internal_api_key=TEST_INTERNAL_API_KEY,
        allowed_hosts=("ai.example.com",),
    )
    client = TestClient(create_app(settings=settings), base_url="http://ai.example.com")

    response = client.get("/health")

    assert response.status_code == 200


def test_docs_and_openapi_are_disabled_by_default_in_production() -> None:
    client = _protected_client()

    assert client.get("/docs").status_code == 404
    assert client.get("/redoc").status_code == 404
    assert client.get("/openapi.json").status_code == 404


def test_docs_can_be_explicitly_enabled_in_production() -> None:
    client = _protected_client(docs_enabled=True)

    assert client.get("/docs").status_code == 200
    assert client.get("/redoc").status_code == 200
    assert client.get("/openapi.json").status_code == 200


def test_parse_job_requires_internal_api_key() -> None:
    client = _client(
        b"<html><body>Acme Backend Engineer remote role.</body></html>",
        _successful_extractor(),
    )

    response = client.post("/parse-job", json={"jobUrl": "https://example.com/jobs/1"})

    assert response.status_code == 401
    assert response.json() == {
        "code": "MISSING_INTERNAL_API_KEY",
        "message": "Internal API key is required.",
    }


def test_parse_job_rejects_invalid_internal_api_key() -> None:
    client = _client(
        b"<html><body>Acme Backend Engineer remote role.</body></html>",
        _successful_extractor(),
    )

    response = client.post(
        "/parse-job",
        json={"jobUrl": "https://example.com/jobs/1"},
        headers={"X-Internal-Api-Key": "wrong-key"},
    )

    assert response.status_code == 403
    assert response.json() == {
        "code": "INVALID_INTERNAL_API_KEY",
        "message": "Internal API key is invalid.",
    }


def test_parse_job_returns_internal_error_when_runtime_key_is_missing() -> None:
    client = _client(
        b"<html><body>Acme Backend Engineer remote role.</body></html>",
        _successful_extractor(),
        internal_api_key="",
    )

    response = _post_parse(client)

    assert response.status_code == 500
    assert response.json() == {
        "code": "AI_SERVICE_INTERNAL_ERROR",
        "message": "AI service internal API key is not configured.",
    }


def test_maps_extracted_structured_output_to_draft_response() -> None:
    client = _client(
        b"<html><body>Acme Backend Engineer remote role.</body></html>",
        _successful_extractor(),
    )

    response = _post_parse(client)

    assert response.status_code == 200
    assert response.json() == {
        "companyName": "Acme",
        "positionTitle": "Backend Engineer",
        "jobUrl": "https://example.com/jobs/1",
        "location": "Remote",
        "workMode": "remote",
        "stage": "initial",
        "notes": "Acme is hiring a backend engineer for platform APIs. The role is remote.",
        "interviews": [],
        "warnings": [],
    }


def test_preserves_safe_request_id_in_logs(caplog) -> None:
    client = _client(
        b"<html><body>Acme Backend Engineer remote role.</body></html>",
        _successful_extractor(),
    )
    caplog.set_level(logging.INFO, logger="app.main")

    response = client.post(
        "/parse-job",
        json={"jobUrl": "https://example.com/jobs/1"},
        headers={
            "X-Internal-Api-Key": TEST_INTERNAL_API_KEY,
            "X-Request-Id": "safe.request-id_123:abc",
        },
    )

    assert response.status_code == 200
    assert "request_id=safe.request-id_123:abc" in caplog.text


def test_replaces_invalid_request_id_before_logging(caplog) -> None:
    client = _client(
        b"<html><body>Acme Backend Engineer remote role.</body></html>",
        _successful_extractor(),
    )
    caplog.set_level(logging.INFO, logger="app.main")

    response = client.post(
        "/parse-job",
        json={"jobUrl": "https://example.com/jobs/1"},
        headers={
            "X-Internal-Api-Key": TEST_INTERNAL_API_KEY,
            "X-Request-Id": "unsafe request id",
        },
    )

    assert response.status_code == 200
    assert "unsafe request id" not in caplog.text
    match = re.search(r"request_id=([0-9a-f-]{36})", caplog.text)
    assert match is not None
    UUID(match.group(1))


def test_replaces_control_characters_and_overlong_request_ids() -> None:
    control_character_id = _request_id("safe\r\nforged-entry")
    overlong_id = _request_id("a" * 129)
    whitespace_id = _request_id(" safe-id ")

    UUID(control_character_id)
    UUID(overlong_id)
    UUID(whitespace_id)
    assert "forged-entry" not in control_character_id


def test_sends_only_bounded_clean_text_to_extractor() -> None:
    extractor = CapturingExtractor(_successful_extractor().extracted)
    settings = Settings(
        openai_api_key="",
        openai_model="test-model",
        internal_api_key=TEST_INTERNAL_API_KEY,
        max_job_text_chars=12,
    )
    app = create_app(
        settings=settings,
        fetcher_factory=lambda resolved_settings: FakeFetcher(
            b"<script>ignored()</script><p>" + (b"R" * 30) + b"</p>"
        ),
        extractor_factory=lambda resolved_settings: extractor,
    )

    response = _post_parse(TestClient(app))

    assert response.status_code == 200
    assert extractor.job_text == "R" * 12


def test_adds_warnings_for_missing_draft_fields() -> None:
    client = _client(
        b"<html><body>Partial job page content is available.</body></html>",
        FakeExtractor(
            ExtractedDraft(
                warnings=["Page content appears partial.", "Page content appears partial."]
            )
        ),
    )

    response = _post_parse(client)

    assert response.status_code == 200
    assert response.json()["companyName"] is None
    assert response.json()["positionTitle"] is None
    assert response.json()["location"] is None
    assert response.json()["workMode"] is None
    assert response.json()["notes"] is None
    assert response.json()["warnings"] == [
        "Page content appears partial.",
        "Company name could not be determined.",
        "Position title could not be determined.",
        "Location could not be determined.",
        "Work mode could not be determined.",
        "Vacancy summary could not be determined.",
    ]


def test_handles_invalid_model_output_safely() -> None:
    client = _client(b"<html><body>Acme Backend Engineer.</body></html>", InvalidExtractor())

    response = _post_parse(client)

    assert response.status_code == 502
    assert response.json() == {
        "code": "AI_EXTRACTION_FAILED",
        "message": "Could not extract application draft.",
    }


def test_handles_openai_timeout_safely() -> None:
    client = _client(b"<html><body>Acme Backend Engineer.</body></html>", TimeoutExtractor())

    response = _post_parse(client)

    assert response.status_code == 502
    assert response.json()["code"] == "AI_EXTRACTION_FAILED"


def test_returns_fetch_timeout_error() -> None:
    client = _client_with_fetcher(FailingFetcher(JobFetchTimeoutError("timeout")))

    response = _post_parse(client)

    assert response.status_code == 504
    assert response.json() == {
        "code": "JOB_FETCH_TIMEOUT",
        "message": "Timed out fetching job URL.",
    }


def test_returns_fetch_failed_error() -> None:
    client = _client_with_fetcher(FailingFetcher(JobFetchError("failed")))

    response = _post_parse(client)

    assert response.status_code == 502
    assert response.json() == {
        "code": "JOB_FETCH_FAILED",
        "message": "Could not fetch job URL.",
    }


def test_logs_fetch_failure_diagnostics(caplog) -> None:
    client = _client_with_fetcher(
        FailingFetcher(
            JobFetchError(
                "failed",
                reason="http_status_error",
                status_code=403,
                content_type="text/html; charset=utf-8",
                redirect_target_host="jobs.example.com",
            )
        )
    )
    caplog.set_level(logging.WARNING, logger="app.main")

    response = _post_parse(client)

    assert response.status_code == 502
    assert "reason=http_status_error" in caplog.text
    assert "status_code=403" in caplog.text
    assert "content_type=text/html; charset=utf-8" in caplog.text
    assert "redirect_target_host=jobs.example.com" in caplog.text


def test_replaces_ip_literal_hosts_in_logs(caplog) -> None:
    initial_host = "198.51.100.23"
    redirect_host = "2001:db8::23"
    client = _client_with_fetcher(
        FailingFetcher(
            JobFetchError(
                "failed",
                reason="unsafe_redirect_target",
                redirect_target_host=redirect_host,
            )
        )
    )
    caplog.set_level(logging.WARNING, logger="app.main")

    response = _post_parse(client, job_url=f"https://{initial_host}/jobs/1")

    assert response.status_code == 502
    assert "url_host=ip-literal" in caplog.text
    assert "redirect_target_host=ip-literal" in caplog.text
    assert initial_host not in caplog.text
    assert redirect_host not in caplog.text


def test_replaces_ambiguous_numeric_and_colon_hosts_for_logging() -> None:
    assert _safe_log_host("2130706433") == "ip-literal"
    assert _safe_log_host("127.1") == "ip-literal"
    assert _safe_log_host("[::ffff:127.0.0.1]") == "ip-literal"
    assert _safe_log_host("jobs.example.com:443") == "ip-literal"
    assert _safe_log_host("jobs.example.com") == "jobs.example.com"


def test_returns_page_not_readable_error_for_empty_page() -> None:
    client = _client(b"", _successful_extractor())

    response = _post_parse(client)

    assert response.status_code == 502
    assert response.json() == {
        "code": "JOB_PAGE_NOT_READABLE",
        "message": "Job page did not contain readable text.",
    }


def test_returns_invalid_job_url_error() -> None:
    app = create_app(
        settings=Settings(
            openai_api_key="",
            openai_model="test-model",
            internal_api_key=TEST_INTERNAL_API_KEY,
        )
    )
    client = TestClient(app)

    response = _post_parse(client, job_url="ftp://example.com/jobs/1")

    assert response.status_code == 400
    assert response.json() == {
        "code": "INVALID_JOB_URL",
        "message": "Job URL is invalid or unsafe.",
    }


def test_rejects_oversized_job_url_before_fetching() -> None:
    client = _client(
        b"<html><body>Acme Backend Engineer remote role.</body></html>",
        _successful_extractor(),
    )

    response = _post_parse(client, job_url="https://example.com/" + ("a" * 2049))

    assert response.status_code == 422


def test_does_not_invent_interview_rounds_when_none_are_present() -> None:
    client = _client(
        b"<html><body>Acme Backend Engineer remote role.</body></html>",
        FakeExtractor(
            ExtractedDraft(
                company_name="Acme",
                position_title="Backend Engineer",
                location="Remote",
                work_mode="remote",
                notes="Acme is hiring a backend engineer for platform APIs. The role is remote.",
                interviews=[ExtractedInterview(type="technical")],
                warnings=[],
            )
        ),
    )

    response = _post_parse(client)

    assert response.status_code == 200
    assert response.json()["interviews"] == []
    assert "Interview rounds were omitted" in response.json()["warnings"][0]


def test_parses_explicit_interview_rounds_as_initial_interviews() -> None:
    client = _client(
        b"<html><body>The process includes a recruiter call and technical interview.</body></html>",
        FakeExtractor(
            ExtractedDraft(
                company_name="Acme",
                position_title="Backend Engineer",
                location="Remote",
                work_mode="remote",
                notes="Acme is hiring a backend engineer for platform APIs. The role is remote.",
                interviews=[
                    ExtractedInterview(type="recruiter"),
                    ExtractedInterview(type="technical"),
                ],
                warnings=[],
            )
        ),
    )

    response = _post_parse(client)

    assert response.status_code == 200
    assert response.json()["interviews"] == [
        {"type": "recruiter", "status": "initial", "scheduledAt": None},
        {"type": "technical", "status": "initial", "scheduledAt": None},
    ]


def _client(body: bytes, extractor, internal_api_key: str = TEST_INTERNAL_API_KEY) -> TestClient:
    return _client_with_fetcher(FakeFetcher(body), extractor, internal_api_key)


def _client_with_fetcher(
    fetcher,
    extractor=None,
    internal_api_key: str = TEST_INTERNAL_API_KEY,
) -> TestClient:
    settings = Settings(
        openai_api_key="",
        openai_model="test-model",
        internal_api_key=internal_api_key,
    )
    app = create_app(
        settings=settings,
        fetcher_factory=lambda resolved_settings: fetcher,
        extractor_factory=lambda resolved_settings: extractor or _successful_extractor(),
    )

    return TestClient(app)


def _protected_client(*, docs_enabled: bool | None = None) -> TestClient:
    settings = Settings(
        app_env="production",
        openai_api_key="sk-protected-test-key",
        openai_model="test-model",
        internal_api_key="a" * 32,
        allowed_hosts=("ai.example.com",),
        docs_enabled=docs_enabled,
    )
    app = create_app(settings=settings)
    return TestClient(app, base_url="https://ai.example.com")


def _post_parse(client: TestClient, job_url: str = "https://example.com/jobs/1"):
    return client.post(
        "/parse-job",
        json={"jobUrl": job_url},
        headers={
            "X-Internal-Api-Key": TEST_INTERNAL_API_KEY,
            "X-Request-Id": "test-request-id",
        },
    )


def _successful_extractor() -> FakeExtractor:
    return FakeExtractor(
        ExtractedDraft(
            company_name="Acme",
            position_title="Backend Engineer",
            location="Remote",
            work_mode="remote",
            notes="Acme is hiring a backend engineer for platform APIs. The role is remote.",
            warnings=[],
        )
    )

import asyncio

import httpx
import pytest

from app.fetcher import JobFetchError, JobPageFetcher, UnsafeJobUrlError, validate_public_url
from app.settings import Settings

PUBLIC_IP = "93.184.216.34"


class AsyncBytesStream(httpx.AsyncByteStream):
    def __init__(self, body: bytes) -> None:
        self.body = body

    async def __aiter__(self):
        yield self.body


@pytest.mark.parametrize(
    ("url", "resolved_ips"),
    [
        ("http://localhost/jobs/1", ["127.0.0.1"]),
        ("http://internal.example/jobs/1", ["10.0.0.1"]),
        ("http://internal.example/jobs/1", ["172.16.0.1"]),
        ("http://internal.example/jobs/1", ["192.168.1.10"]),
        ("http://127.0.0.1/jobs/1", [PUBLIC_IP]),
        ("http://169.254.169.254/latest/meta-data", [PUBLIC_IP]),
        ("http://[::1]/jobs/1", [PUBLIC_IP]),
        ("http://[fd00::1]/jobs/1", [PUBLIC_IP]),
        ("http://[fe80::1]/jobs/1", [PUBLIC_IP]),
    ],
)
def test_rejects_localhost_private_loopback_link_local_and_metadata_urls(
    url: str,
    resolved_ips: list[str],
) -> None:
    def resolver(host: str, port: int) -> list[str]:
        return resolved_ips

    with pytest.raises(UnsafeJobUrlError):
        validate_public_url(url, resolver)


@pytest.mark.parametrize(
    "url",
    [
        "ftp://example.com/jobs/1",
        "file:///etc/passwd",
        "https://user:pass@example.com/jobs/1",
    ],
)
def test_rejects_non_http_schemes_and_credentials(url: str) -> None:
    with pytest.raises(UnsafeJobUrlError):
        validate_public_url(url, lambda host, port: [PUBLIC_IP])


def test_validates_redirect_target_safety() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        if request.url.host == "example.com":
            return httpx.Response(302, headers={"Location": "http://127.0.0.1/admin"})

        return httpx.Response(200, content=b"unexpected")

    fetcher = JobPageFetcher(
        Settings(openai_api_key="", openai_model="test"),
        resolver=lambda host, port: [PUBLIC_IP],
        transport=httpx.MockTransport(handler),
    )

    with pytest.raises(UnsafeJobUrlError):
        asyncio.run(fetcher.fetch("https://example.com/jobs/1"))


def test_rejects_redirect_to_private_ip() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        if request.url.host == "example.com":
            return httpx.Response(
                302,
                headers={"Location": "http://169.254.169.254/latest/meta-data"},
            )

        return httpx.Response(200, content=b"unexpected")

    fetcher = JobPageFetcher(
        Settings(openai_api_key="", openai_model="test"),
        resolver=lambda host, port: [PUBLIC_IP],
        transport=httpx.MockTransport(handler),
    )

    with pytest.raises(UnsafeJobUrlError) as exception_info:
        asyncio.run(fetcher.fetch("https://example.com/jobs/1"))

    assert exception_info.value.reason == "unsafe_redirect_target"
    assert exception_info.value.redirect_target_host == "169.254.169.254"


def test_fetch_error_includes_status_and_content_type_diagnostics() -> None:
    fetcher = _fetcher_for_response(
        httpx.Response(
            403,
            headers={"content-type": "text/html; charset=utf-8"},
            content=b"forbidden",
        )
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://example.com/jobs/1"))

    assert exception_info.value.reason == "http_status_error"
    assert exception_info.value.status_code == 403
    assert exception_info.value.content_type == "text/html; charset=utf-8"


def test_successful_fetch_result_includes_status_code() -> None:
    fetcher = _fetcher_for_response(
        httpx.Response(
            200,
            headers={"content-type": "text/html; charset=utf-8"},
            content=b"<html><body>Job description</body></html>",
        )
    )

    result = asyncio.run(fetcher.fetch("https://example.com/jobs/1"))

    assert result.status_code == 200
    assert result.content_type == "text/html; charset=utf-8"


def test_default_limit_accepts_google_sized_response() -> None:
    body = b"x" * 1_300_000
    fetcher = _fetcher_for_response(
        httpx.Response(200, headers={"content-type": "text/html"}, content=body)
    )

    result = asyncio.run(fetcher.fetch("https://example.com/jobs/1"))

    assert result.body == body


def test_rejects_non_success_status_outside_redirect_handling() -> None:
    fetcher = _fetcher_for_response(
        httpx.Response(
            304,
            headers={"content-type": "text/html"},
        )
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://example.com/jobs/1"))

    assert exception_info.value.reason == "http_status_error"
    assert exception_info.value.status_code == 304


def test_rejects_unsupported_content_type() -> None:
    fetcher = _fetcher_for_response(
        httpx.Response(200, headers={"content-type": "image/png"}, content=b"png")
    )

    with pytest.raises(JobFetchError):
        asyncio.run(fetcher.fetch("https://example.com/jobs/1"))


def test_rejects_oversized_content_length_before_reading() -> None:
    fetcher = _fetcher_for_response(
        httpx.Response(
            200,
            headers={"content-type": "text/html", "content-length": "11"},
            content=b"x" * 11,
        ),
        max_response_bytes=10,
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://example.com/jobs/1"))

    assert exception_info.value.reason == "content_length_too_large"


def test_rejects_oversized_streamed_body() -> None:
    fetcher = _fetcher_for_response(
        httpx.Response(
            200,
            headers={"content-type": "text/html"},
            stream=AsyncBytesStream(b"x" * 11),
        ),
        max_response_bytes=10,
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://example.com/jobs/1"))

    assert exception_info.value.reason == "response_too_large"


def test_env_configured_low_response_limit_is_enforced(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_MAX_RESPONSE_BYTES", "10")
    settings = Settings.from_env()
    fetcher = _fetcher_for_response(
        httpx.Response(
            200,
            headers={"content-type": "text/html"},
            stream=AsyncBytesStream(b"x" * 11),
        ),
        settings=settings,
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://example.com/jobs/1"))

    assert exception_info.value.reason == "response_too_large"


def _fetcher_for_response(
    response: httpx.Response,
    max_response_bytes: int | None = None,
    settings: Settings | None = None,
) -> JobPageFetcher:
    async def handler(request: httpx.Request) -> httpx.Response:
        return response

    resolved_settings = settings
    if resolved_settings is None:
        resolved_settings = (
            Settings(
                openai_api_key="",
                openai_model="test",
                max_response_bytes=max_response_bytes,
            )
            if max_response_bytes is not None
            else Settings(
                openai_api_key="",
                openai_model="test",
            )
        )

    return JobPageFetcher(
        resolved_settings,
        resolver=lambda host, port: [PUBLIC_IP],
        transport=httpx.MockTransport(handler),
    )

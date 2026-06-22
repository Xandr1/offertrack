import asyncio
import logging
from collections.abc import Callable

import pytest

from app.composite_fetcher import CompositeJobPageFetcher
from app.fetcher import FetchResult, JobFetchError, UnsafeJobUrlError
from app.settings import Settings


class FakeFetcher:
    def __init__(
        self,
        result: FetchResult | None = None,
        exception: Exception | None = None,
    ) -> None:
        self.result = result
        self.exception = exception
        self.calls: list[str] = []

    async def fetch(self, job_url: str) -> FetchResult:
        self.calls.append(job_url)

        if self.exception is not None:
            raise self.exception

        if self.result is None:
            raise AssertionError("FakeFetcher requires a result or exception.")

        return self.result


def test_returns_primary_result_when_primary_readable_text_is_sufficient() -> None:
    primary_result = _result(
        b"<html><body>Acme is hiring a backend engineer for platform APIs.</body></html>"
    )
    browser_result = _result(b"<html><body>Browser result.</body></html>")
    primary = FakeFetcher(primary_result)
    browser = FakeFetcher(browser_result)

    result = asyncio.run(
        _fetch(
            Settings(
                openai_api_key="",
                openai_model="test",
                browser_min_text_length=20,
            ),
            primary,
            browser,
        )
    )

    assert result is primary_result
    assert primary.calls == ["https://example.com/jobs/1"]
    assert browser.calls == []


def test_uses_browser_fallback_when_primary_text_is_too_short() -> None:
    primary = FakeFetcher(_result(b"<html><body>Apply</body></html>"))
    browser_result = _result(
        b"<html><body>Acme is hiring a backend engineer for platform APIs.</body></html>",
        url="https://jobs.example.com/rendered",
    )
    browser = FakeFetcher(browser_result)

    result = asyncio.run(
        _fetch(
            Settings(
                openai_api_key="",
                openai_model="test",
                browser_min_text_length=20,
            ),
            primary,
            browser,
        )
    )

    assert result is browser_result
    assert browser.calls == ["https://example.com/jobs/1"]


@pytest.mark.parametrize(
    "exception_factory",
    [
        lambda: JobFetchError(
            "forbidden",
            reason="http_status_error",
            status_code=403,
        ),
        lambda: JobFetchError(
            "rate limited",
            reason="http_status_error",
            status_code=429,
        ),
        lambda: JobFetchError("client failed", reason="http_client_error"),
        lambda: JobFetchError(
            "unsupported content type",
            reason="unsupported_content_type",
            content_type="application/json",
        ),
    ],
)
def test_uses_browser_fallback_for_retryable_primary_fetch_errors(
    exception_factory: Callable[[], JobFetchError],
) -> None:
    browser_result = _result(b"<html><body>Rendered vacancy content.</body></html>")
    primary = FakeFetcher(exception=exception_factory())
    browser = FakeFetcher(browser_result)

    result = asyncio.run(
        _fetch(
            Settings(
                openai_api_key="",
                openai_model="test",
            ),
            primary,
            browser,
        )
    )

    assert result is browser_result
    assert browser.calls == ["https://example.com/jobs/1"]


def test_does_not_use_browser_fallback_for_unsafe_url_errors() -> None:
    primary = FakeFetcher(exception=UnsafeJobUrlError("unsafe", reason="unsupported_scheme"))
    browser = FakeFetcher(_result(b"<html><body>Browser result.</body></html>"))

    with pytest.raises(UnsafeJobUrlError):
        asyncio.run(
            _fetch(
                Settings(
                    openai_api_key="",
                    openai_model="test",
                ),
                primary,
                browser,
            )
        )

    assert browser.calls == []


@pytest.mark.parametrize("reason", ["content_length_too_large", "response_too_large"])
def test_does_not_use_browser_fallback_for_oversized_response_errors(reason: str) -> None:
    browser_result = _result(b"<html><body>Browser result.</body></html>")
    primary_exception = JobFetchError("too large", reason=reason)
    primary = FakeFetcher(exception=primary_exception)
    browser = FakeFetcher(browser_result)

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(
            _fetch(
                Settings(
                    openai_api_key="",
                    openai_model="test",
                ),
                primary,
                browser,
            )
        )

    assert exception_info.value is primary_exception
    assert browser.calls == []


def test_returns_browser_result_as_fetch_result() -> None:
    browser_result = FetchResult(
        url="https://jobs.example.com/rendered",
        body=b"<html><body>Rendered vacancy content.</body></html>",
        content_type="text/html",
    )
    primary = FakeFetcher(_result(b"<html><body>Apply</body></html>"))
    browser = FakeFetcher(browser_result)

    result = asyncio.run(
        _fetch(
            Settings(
                openai_api_key="",
                openai_model="test",
                browser_min_text_length=20,
            ),
            primary,
            browser,
        )
    )

    assert result == browser_result


@pytest.mark.parametrize("browser_reason", ["browser_unavailable", "browser_fetch_failed"])
def test_logs_browser_failure_and_preserves_primary_short_text_result(
    browser_reason: str,
    caplog,
) -> None:
    primary_result = _result(b"<html><body>Apply</body></html>")
    primary = FakeFetcher(primary_result)
    browser = FakeFetcher(exception=JobFetchError("browser failed", reason=browser_reason))
    caplog.set_level(logging.INFO, logger="app.composite_fetcher")

    result = asyncio.run(
        _fetch(
            Settings(
                openai_api_key="",
                openai_model="test",
                browser_min_text_length=20,
            ),
            primary,
            browser,
        )
    )

    assert result is primary_result
    assert f"browser_reason={browser_reason}" in caplog.text
    assert "outcome=failed" in caplog.text
    assert "url_host=example.com" in caplog.text
    assert "https://example.com/jobs/1" not in caplog.text


def test_browser_failure_preserves_retryable_primary_error(
    caplog,
) -> None:
    primary_exception = JobFetchError(
        "forbidden",
        reason="http_status_error",
        status_code=403,
    )
    primary = FakeFetcher(exception=primary_exception)
    browser = FakeFetcher(exception=JobFetchError("browser failed", reason="browser_fetch_failed"))
    caplog.set_level(logging.INFO, logger="app.composite_fetcher")

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(
            _fetch(
                Settings(
                    openai_api_key="",
                    openai_model="test",
                ),
                primary,
                browser,
            )
        )

    assert exception_info.value is primary_exception
    assert "browser_reason=browser_fetch_failed" in caplog.text
    assert "outcome=failed" in caplog.text


async def _fetch(
    settings: Settings,
    primary: FakeFetcher,
    browser: FakeFetcher,
) -> FetchResult:
    fetcher = CompositeJobPageFetcher(
        settings,
        primary_fetcher=primary,
        browser_fetcher=browser,
    )

    return await fetcher.fetch("https://example.com/jobs/1")


def _result(
    body: bytes,
    *,
    url: str = "https://example.com/jobs/1",
) -> FetchResult:
    return FetchResult(url=url, body=body, content_type="text/html")

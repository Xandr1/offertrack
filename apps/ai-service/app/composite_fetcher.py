import logging
from typing import Protocol

import httpx

from app.browser_fetcher import BrowserJobPageFetcher
from app.fetcher import FetchResult, JobFetchError, JobPageFetcher
from app.html_extractor import extract_readable_text
from app.settings import Settings

logger = logging.getLogger(__name__)

RETRYABLE_STATUS_CODES = {403, 429}
RETRYABLE_REASONS = {
    "http_client_error",
    "unsupported_content_type",
}


class JobPageFetcherProtocol(Protocol):
    async def fetch(self, job_url: str) -> FetchResult: ...


class CompositeJobPageFetcher:
    def __init__(
        self,
        settings: Settings,
        primary_fetcher: JobPageFetcherProtocol | None = None,
        browser_fetcher: JobPageFetcherProtocol | None = None,
    ) -> None:
        self.settings = settings
        self.primary_fetcher = primary_fetcher or JobPageFetcher(settings)
        self.browser_fetcher = browser_fetcher or BrowserJobPageFetcher(settings)

    async def fetch(self, job_url: str) -> FetchResult:
        try:
            primary_result = await self.primary_fetcher.fetch(job_url)
        except JobFetchError as exception:
            if not _is_retryable_fetch_error(exception):
                raise

            return await self._try_browser_after_primary_error(job_url, exception)

        readable_text = extract_readable_text(primary_result.body, primary_result.content_type)
        if len(readable_text) >= self.settings.browser_min_text_length:
            return primary_result

        return await self._try_browser_after_short_text(job_url, primary_result, len(readable_text))

    async def _try_browser_after_primary_error(
        self,
        job_url: str,
        primary_exception: JobFetchError,
    ) -> FetchResult:
        fallback_reason = _safe_log_value(primary_exception.reason)
        host = _url_host(job_url)

        _log_browser_fallback(host, fallback_reason, "attempted")

        try:
            result = await self.browser_fetcher.fetch(job_url)
        except JobFetchError as browser_exception:
            _log_browser_fallback(
                host,
                fallback_reason,
                "failed",
                browser_exception,
            )
            raise primary_exception from browser_exception

        _log_browser_fallback(host, fallback_reason, "succeeded")
        return result

    async def _try_browser_after_short_text(
        self,
        job_url: str,
        primary_result: FetchResult,
        readable_text_length: int,
    ) -> FetchResult:
        fallback_reason = "short_readable_text"
        host = _url_host(job_url)

        _log_browser_fallback(
            host,
            fallback_reason,
            "attempted",
            readable_text_length=readable_text_length,
        )

        try:
            result = await self.browser_fetcher.fetch(job_url)
        except JobFetchError as browser_exception:
            _log_browser_fallback(
                host,
                fallback_reason,
                "failed",
                browser_exception,
                readable_text_length=readable_text_length,
            )
            return primary_result

        _log_browser_fallback(
            host,
            fallback_reason,
            "succeeded",
            readable_text_length=readable_text_length,
        )
        return result


def _is_retryable_fetch_error(exception: JobFetchError) -> bool:
    if exception.reason == "http_status_error":
        return exception.status_code in RETRYABLE_STATUS_CODES

    return exception.reason in RETRYABLE_REASONS


def _log_browser_fallback(
    host: str,
    reason: str,
    outcome: str,
    browser_exception: JobFetchError | None = None,
    *,
    readable_text_length: int | None = None,
) -> None:
    browser_reason = _safe_log_value(getattr(browser_exception, "reason", None))
    status_code = _safe_status_code(getattr(browser_exception, "status_code", None))
    text_length = str(readable_text_length) if readable_text_length is not None else "-"

    logger.info(
        "ai_service_browser_fallback url_host=%s reason=%s outcome=%s "
        "browser_reason=%s status_code=%s readable_text_length=%s",
        host,
        reason,
        outcome,
        browser_reason,
        status_code,
        text_length,
    )


def _url_host(job_url: str) -> str:
    try:
        return httpx.URL(job_url).host or "unknown"
    except httpx.InvalidURL:
        return "invalid"


def _safe_log_value(value: object) -> str:
    if value is None:
        return "-"

    text = " ".join(str(value).split())
    return text[:120] if text else "-"


def _safe_status_code(value: object) -> str:
    if isinstance(value, int):
        return str(value)

    return "-"

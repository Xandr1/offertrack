import hmac
import logging
import time
from collections.abc import Callable
from typing import Protocol
from uuid import uuid4

import httpx
from fastapi import FastAPI, Header
from pydantic import ValidationError
from starlette.concurrency import run_in_threadpool
from starlette.responses import JSONResponse

from app.draft_builder import build_draft_response
from app.fetcher import (
    FetchResult,
    JobFetchError,
    JobFetchTimeoutError,
    JobPageFetcher,
    UnsafeJobUrlError,
)
from app.html_extractor import extract_readable_text
from app.models import (
    DraftResponse,
    ExtractedDraft,
    HealthResponse,
    ParseJobRequest,
    ServiceErrorCode,
    ServiceErrorResponse,
)
from app.openai_extractor import ExtractionError, OpenAiDraftExtractor, OpenAiTimeoutError
from app.settings import Settings

logger = logging.getLogger(__name__)


class JobPageNotReadableError(ExtractionError):
    pass


class JobPageFetcherProtocol(Protocol):
    async def fetch(self, job_url: str) -> FetchResult: ...


class DraftExtractorProtocol(Protocol):
    def extract(self, job_text: str) -> ExtractedDraft: ...


def create_app(
    settings: Settings | None = None,
    fetcher_factory: Callable[[Settings], JobPageFetcherProtocol] | None = None,
    extractor_factory: Callable[[Settings], DraftExtractorProtocol] | None = None,
) -> FastAPI:
    resolved_settings = settings or Settings.from_env()
    fetcher = (
        fetcher_factory(resolved_settings)
        if fetcher_factory is not None
        else JobPageFetcher(resolved_settings)
    )
    extractor = (
        extractor_factory(resolved_settings)
        if extractor_factory is not None
        else OpenAiDraftExtractor(resolved_settings)
    )

    app = FastAPI(title="OfferTrack AI Service")

    @app.get("/health", response_model=HealthResponse)
    async def health() -> HealthResponse:
        return HealthResponse()

    @app.post("/parse-job", response_model=DraftResponse)
    async def parse_job(
        request: ParseJobRequest,
        x_internal_api_key: str | None = Header(default=None, alias="X-Internal-Api-Key"),
        x_request_id: str | None = Header(default=None, alias="X-Request-Id"),
    ) -> DraftResponse | JSONResponse:
        request_id = _request_id(x_request_id)
        url_host = _url_host(request.job_url)
        start = time.perf_counter()

        try:
            auth_error = _authenticate(resolved_settings, x_internal_api_key)
            if auth_error is not None:
                status_code, code, message = auth_error
                _log_failure(request_id, url_host, code, start)
                return _error_response(status_code, code, message)

            fetched_page = await fetcher.fetch(request.job_url)
            page_text = extract_readable_text(
                fetched_page.body,
                fetched_page.content_type,
                max_chars=resolved_settings.max_job_text_chars,
            )

            if not page_text:
                raise JobPageNotReadableError("Job page did not contain readable text.")

            extracted = await run_in_threadpool(extractor.extract, page_text)
            response = build_draft_response(request.job_url, extracted, page_text)
            duration_ms = _duration_ms(start)
            logger.info(
                "ai_service_parse_job_succeeded request_id=%s source_type=url "
                "url_host=%s duration_ms=%s",
                request_id,
                url_host,
                duration_ms,
            )
            return response
        except UnsafeJobUrlError as exception:
            return _log_and_error(
                request_id,
                url_host,
                "INVALID_JOB_URL",
                "Job URL is invalid or unsafe.",
                400,
                start,
                exception,
            )
        except JobFetchTimeoutError as exception:
            return _log_and_error(
                request_id,
                url_host,
                "JOB_FETCH_TIMEOUT",
                "Timed out fetching job URL.",
                504,
                start,
                exception,
            )
        except JobFetchError as exception:
            return _log_and_error(
                request_id,
                url_host,
                "JOB_FETCH_FAILED",
                "Could not fetch job URL.",
                502,
                start,
                exception,
            )
        except JobPageNotReadableError as exception:
            return _log_and_error(
                request_id,
                url_host,
                "JOB_PAGE_NOT_READABLE",
                "Job page did not contain readable text.",
                502,
                start,
                exception,
            )
        except (OpenAiTimeoutError, ExtractionError, ValidationError) as exception:
            return _log_and_error(
                request_id,
                url_host,
                "AI_EXTRACTION_FAILED",
                "Could not extract application draft.",
                502,
                start,
                exception,
            )
        except Exception as exception:
            return _log_and_error(
                request_id,
                url_host,
                "AI_SERVICE_INTERNAL_ERROR",
                "AI service is misconfigured or unavailable.",
                500,
                start,
                exception,
            )

    return app


app = create_app()


def _authenticate(
    settings: Settings, provided_api_key: str | None
) -> tuple[int, ServiceErrorCode, str] | None:
    if not settings.internal_api_key:
        return 500, "AI_SERVICE_INTERNAL_ERROR", "AI service internal API key is not configured."

    if provided_api_key is None or not provided_api_key.strip():
        return 401, "MISSING_INTERNAL_API_KEY", "Internal API key is required."

    if not hmac.compare_digest(provided_api_key, settings.internal_api_key):
        return 403, "INVALID_INTERNAL_API_KEY", "Internal API key is invalid."

    return None


def _error_response(status_code: int, code: ServiceErrorCode, message: str) -> JSONResponse:
    error = ServiceErrorResponse(code=code, message=message)
    return JSONResponse(status_code=status_code, content=error.model_dump())


def _log_and_error(
    request_id: str,
    url_host: str,
    code: ServiceErrorCode,
    message: str,
    status_code: int,
    start: float,
    exception: Exception,
) -> JSONResponse:
    _log_failure(request_id, url_host, code, start, exception)
    return _error_response(status_code, code, message)


def _log_failure(
    request_id: str,
    url_host: str,
    code: ServiceErrorCode,
    start: float,
    exception: Exception | None = None,
) -> None:
    duration_ms = _duration_ms(start)

    if exception is None:
        logger.warning(
            "ai_service_parse_job_failed request_id=%s source_type=url url_host=%s error_code=%s "
            "duration_ms=%s",
            request_id,
            url_host,
            code,
            duration_ms,
        )
        return

    reason = _safe_log_value(getattr(exception, "reason", None))
    status_code = _safe_status_code(getattr(exception, "status_code", None))
    content_type = _safe_log_value(getattr(exception, "content_type", None))
    redirect_target_host = _safe_log_value(getattr(exception, "redirect_target_host", None))

    logger.warning(
        "ai_service_parse_job_failed request_id=%s source_type=url url_host=%s error_code=%s "
        "duration_ms=%s reason=%s status_code=%s content_type=%s redirect_target_host=%s "
        "exception_type=%s",
        request_id,
        url_host,
        code,
        duration_ms,
        reason,
        status_code,
        content_type,
        redirect_target_host,
        type(exception).__name__,
    )


def _request_id(header_value: str | None) -> str:
    if header_value is not None and header_value.strip():
        return header_value.strip()[:128]

    return str(uuid4())


def _url_host(job_url: str) -> str:
    try:
        return httpx.URL(job_url).host or "unknown"
    except httpx.InvalidURL:
        return "invalid"


def _duration_ms(start: float) -> int:
    return int((time.perf_counter() - start) * 1000)


def _safe_log_value(value: object) -> str:
    if value is None:
        return "-"

    text = " ".join(str(value).split())
    return text[:120] if text else "-"


def _safe_status_code(value: object) -> str:
    if isinstance(value, int):
        return str(value)

    return "-"

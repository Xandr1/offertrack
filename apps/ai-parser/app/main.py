from collections.abc import Callable

from fastapi import FastAPI, HTTPException
from pydantic import ValidationError
from starlette.concurrency import run_in_threadpool

from app.draft_builder import build_draft_response
from app.fetcher import JobFetchError, JobFetchTimeoutError, JobPageFetcher, UnsafeJobUrlError
from app.html_extractor import extract_readable_text
from app.models import DraftResponse, ParseJobRequest
from app.openai_extractor import ExtractionError, OpenAiDraftExtractor
from app.settings import Settings


def create_app(
    settings: Settings | None = None,
    fetcher_factory: Callable[[Settings], JobPageFetcher] | None = None,
    extractor_factory: Callable[[Settings], OpenAiDraftExtractor] | None = None,
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

  app = FastAPI(title="OfferTrack AI Parser")

  @app.post("/parse-job", response_model=DraftResponse)
  async def parse_job(request: ParseJobRequest) -> DraftResponse:
    try:
      fetched_page = await fetcher.fetch(request.job_url)
      page_text = extract_readable_text(fetched_page.body, fetched_page.content_type)

      if not page_text:
        raise ExtractionError("Job page did not contain readable text.")

      extracted = await run_in_threadpool(extractor.extract, page_text)
      return build_draft_response(request.job_url, extracted, page_text)
    except UnsafeJobUrlError as exception:
      raise HTTPException(status_code=400, detail="Job URL is invalid or unsafe.") from exception
    except JobFetchTimeoutError as exception:
      raise HTTPException(status_code=504, detail="Timed out fetching job URL.") from exception
    except JobFetchError as exception:
      raise HTTPException(status_code=502, detail="Could not fetch job URL.") from exception
    except (ExtractionError, ValidationError) as exception:
      raise HTTPException(status_code=502, detail="Could not extract application draft.") from exception

  return app


app = create_app()

import asyncio
from typing import Any

from playwright.async_api import Error as PlaywrightError
from playwright.async_api import TimeoutError as PlaywrightTimeoutError
from playwright.async_api import async_playwright

from app.fetcher import (
    FetchResult,
    JobFetchError,
    JobFetchTimeoutError,
    Resolver,
    resolve_host_ips,
    validate_public_url,
)
from app.settings import Settings

BLOCKED_RESOURCE_TYPES = {"image", "media", "font"}
NETWORK_IDLE_TIMEOUT_MS = 2_000


class BrowserJobPageFetcher:
    def __init__(
        self,
        settings: Settings,
        resolver: Resolver = resolve_host_ips,
    ) -> None:
        self.settings = settings
        self.resolver = resolver
        self._semaphore = asyncio.Semaphore(max(1, settings.browser_max_concurrency))

    async def fetch(self, job_url: str) -> FetchResult:
        current_url = validate_public_url(job_url, self.resolver)

        async with self._semaphore:
            return await self._fetch_with_browser(str(current_url))

    async def _fetch_with_browser(self, url: str) -> FetchResult:
        timeout_ms = self.settings.browser_timeout_seconds * 1000

        try:
            async with async_playwright() as playwright:
                try:
                    browser = await playwright.chromium.launch(
                        headless=True,
                        timeout=timeout_ms,
                    )
                except PlaywrightTimeoutError as exception:
                    raise JobFetchTimeoutError(
                        "Timed out launching browser.", reason="browser_timeout"
                    ) from exception
                except PlaywrightError as exception:
                    raise JobFetchError(
                        "Browser is unavailable for job page fetching.",
                        reason="browser_unavailable",
                    ) from exception

                try:
                    context = await browser.new_context()
                    try:
                        page = await context.new_page()
                        page.set_default_timeout(timeout_ms)
                        page.set_default_navigation_timeout(timeout_ms)
                        await page.route("**/*", _route_request)

                        await page.goto(url, wait_until="domcontentloaded", timeout=timeout_ms)

                        try:
                            await page.wait_for_load_state(
                                "networkidle",
                                timeout=min(NETWORK_IDLE_TIMEOUT_MS, timeout_ms),
                            )
                        except PlaywrightTimeoutError:
                            pass

                        body = (await page.content()).encode("utf-8")

                        if len(body) > self.settings.browser_max_response_bytes:
                            raise JobFetchError(
                                "Browser-rendered job page was too large.",
                                reason="response_too_large",
                                content_type="text/html",
                            )

                        return FetchResult(url=page.url, body=body, content_type="text/html")
                    finally:
                        await context.close()
                finally:
                    await browser.close()
        except JobFetchError:
            raise
        except PlaywrightTimeoutError as exception:
            raise JobFetchTimeoutError(
                "Timed out rendering job URL.", reason="browser_timeout"
            ) from exception
        except PlaywrightError as exception:
            raise JobFetchError(
                "Could not render job URL with browser.",
                reason="browser_fetch_failed",
            ) from exception


async def _route_request(route: Any) -> None:
    if route.request.resource_type in BLOCKED_RESOURCE_TYPES:
        await route.abort()
        return

    await route.continue_()

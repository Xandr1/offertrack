import asyncio

import httpx
import pytest

from app.fetcher import JobPageFetcher, UnsafeJobUrlError, validate_public_url
from app.settings import Settings


def test_rejects_localhost_private_and_loopback_urls() -> None:
  resolver = lambda host, port: ["127.0.0.1"]

  unsafe_urls = [
      "http://localhost/jobs/1",
      "http://127.0.0.1/jobs/1",
      "http://10.0.0.1/jobs/1",
      "http://172.16.0.1/jobs/1",
      "http://192.168.1.10/jobs/1",
      "http://169.254.169.254/latest/meta-data",
      "http://[::1]/jobs/1",
  ]

  for url in unsafe_urls:
    with pytest.raises(UnsafeJobUrlError):
      validate_public_url(url, resolver)


def test_validates_redirect_target_safety() -> None:
  async def handler(request: httpx.Request) -> httpx.Response:
    if request.url.host == "example.com":
      return httpx.Response(302, headers={"Location": "http://127.0.0.1/admin"})

    return httpx.Response(200, content=b"unexpected")

  fetcher = JobPageFetcher(
      Settings(openai_api_key="", openai_model="test"),
      resolver=lambda host, port: ["93.184.216.34"],
      transport=httpx.MockTransport(handler),
  )

  with pytest.raises(UnsafeJobUrlError):
    asyncio.run(fetcher.fetch("https://example.com/jobs/1"))

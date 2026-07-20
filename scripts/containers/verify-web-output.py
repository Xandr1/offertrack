#!/usr/bin/env python3
"""Verify the bounded production Web output used by container smoke tests."""

from __future__ import annotations

import argparse
from html.parser import HTMLParser
from urllib.parse import quote, urljoin, urlsplit
from urllib.request import Request, urlopen


MAX_SCRIPTS = 64
MAX_SCRIPT_BYTES = 4 * 1024 * 1024
MAX_TOTAL_SCRIPT_BYTES = 16 * 1024 * 1024


class ScriptParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.sources: list[str] = []

    def handle_starttag(
        self, tag: str, attrs: list[tuple[str, str | None]]
    ) -> None:
        if tag.lower() != "script":
            return
        attributes = dict(attrs)
        source = attributes.get("src")
        if source:
            self.sources.append(source)


def fetch(url: str, *, accept: str | None = None, limit: int = 8 * 1024 * 1024):
    headers = {"Accept": accept} if accept else {}
    with urlopen(Request(url, headers=headers), timeout=10) as response:
        body = response.read(limit + 1)
        if len(body) > limit:
            raise AssertionError(f"response exceeded {limit} bytes: {url}")
        return response.status, response.headers, body


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--expected-api-url", required=True)
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/") + "/"
    status, _, login_body = fetch(urljoin(base_url, "login"))
    assert status == 200, f"/login returned {status}"
    login_text = login_body.decode("utf-8", errors="replace")
    assert "Sign in" in login_text, "/login did not contain the expected content"

    script_parser = ScriptParser()
    script_parser.feed(login_text)
    script_urls = list(dict.fromkeys(script_parser.sources))
    assert script_urls, "/login did not reference any JavaScript"
    assert len(script_urls) <= MAX_SCRIPTS, "the page referenced too many scripts"

    expected = args.expected_api_url.encode("utf-8")
    total_bytes = 0
    found_api_url = False
    for source in script_urls:
        script_url = urljoin(base_url, source)
        resolved = urlsplit(script_url)
        expected_origin = urlsplit(base_url)
        assert (resolved.scheme, resolved.netloc) == (
            expected_origin.scheme,
            expected_origin.netloc,
        ), f"/login referenced cross-origin JavaScript: {source}"
        script_status, _, script_body = fetch(script_url, limit=MAX_SCRIPT_BYTES)
        assert script_status == 200, f"JavaScript asset returned {script_status}: {source}"
        total_bytes += len(script_body)
        assert total_bytes <= MAX_TOTAL_SCRIPT_BYTES, "JavaScript verification exceeded its bound"
        found_api_url = found_api_url or expected in script_body

    assert found_api_url, "no dynamically discovered JavaScript contained the baked API URL"

    status, headers, asset_body = fetch(urljoin(base_url, "offertrack-logo.png"))
    assert status == 200, f"public asset returned {status}"
    assert headers.get_content_type() == "image/png", "public asset was not image/png"
    assert asset_body, "public asset was empty"

    optimized_path = (
        "_next/image?url="
        + quote("/offertrack-logo.png", safe="")
        + "&w=64&q=75"
    )
    status, headers, optimized_body = fetch(
        urljoin(base_url, optimized_path), accept="image/webp,image/*"
    )
    assert status == 200, f"/_next/image returned {status}"
    assert headers.get_content_type().startswith("image/"), "optimized asset was not an image"
    assert optimized_body, "optimized asset was empty"

    print("Web production output checks passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

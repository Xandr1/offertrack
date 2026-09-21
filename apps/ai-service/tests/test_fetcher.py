import asyncio
import ipaddress
import logging
import socket
import threading
import time
from collections.abc import Awaitable, Callable

import httpx
import pytest

from app.fetcher import (
    JobFetchError,
    JobFetchTimeoutError,
    JobPageFetcher,
    UnsafeJobUrlError,
    _create_transport,
    resolve_host_ips,
    validate_public_url,
)
from app.settings import Settings

PUBLIC_IP = "93.184.216.34"
SECOND_PUBLIC_IP = "142.250.191.142"
PUBLIC_IPV6 = "2606:4700:4700::1111"


class AsyncBytesStream(httpx.AsyncByteStream):
    def __init__(self, *chunks: bytes) -> None:
        self.chunks = chunks
        self.closed = False
        self.iterated = False

    async def __aiter__(self):
        self.iterated = True
        for chunk in self.chunks:
            yield chunk

    async def aclose(self) -> None:
        self.closed = True


class BlockingStream(httpx.AsyncByteStream):
    def __init__(self) -> None:
        self.release = asyncio.Event()
        self.closed = False

    async def __aiter__(self):
        await self.release.wait()
        yield b"unreachable"

    async def aclose(self) -> None:
        self.closed = True


class RecordingTransport(httpx.AsyncBaseTransport):
    def __init__(
        self,
        handler: Callable[[httpx.Request], Awaitable[httpx.Response]],
    ) -> None:
        self.handler = handler
        self.requests: list[httpx.Request] = []
        self.closed = False

    async def handle_async_request(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        response = await self.handler(request)
        if response.is_stream_consumed:
            return httpx.Response(
                response.status_code,
                headers=response.headers,
                stream=AsyncBytesStream(response.content),
                extensions=response.extensions,
            )
        return response

    async def aclose(self) -> None:
        self.closed = True


class RecordingTransportFactory:
    def __init__(
        self,
        handler: Callable[[httpx.Request], Awaitable[httpx.Response]],
    ) -> None:
        self.handler = handler
        self.transports: list[RecordingTransport] = []

    def __call__(self) -> RecordingTransport:
        transport = RecordingTransport(self.handler)
        self.transports.append(transport)
        return transport


class FakeClock:
    def __init__(self) -> None:
        self.now = 0.0

    def __call__(self) -> float:
        return self.now

    def advance(self, seconds: float) -> None:
        self.now += seconds


def _settings(
    *,
    max_response_bytes: int = 10_000_000,
    fetch_timeout_seconds: float = 10.0,
    max_redirects: int = 5,
) -> Settings:
    return Settings(
        openai_api_key="",
        openai_model="test",
        max_response_bytes=max_response_bytes,
        fetch_timeout_seconds=fetch_timeout_seconds,
        max_redirects=max_redirects,
    )


def _resolver_for(
    addresses: list[str],
    calls: list[tuple[str, int]] | None = None,
):
    async def resolver(host: str, port: int) -> list[str]:
        if calls is not None:
            calls.append((host, port))
        return addresses

    return resolver


def _fetcher_for_handler(
    handler: Callable[[httpx.Request], Awaitable[httpx.Response]],
    *,
    resolver=None,
    settings: Settings | None = None,
    clock=None,
) -> tuple[JobPageFetcher, RecordingTransportFactory]:
    factory = RecordingTransportFactory(handler)
    fetcher = JobPageFetcher(
        settings or _settings(),
        resolver=resolver or _resolver_for([PUBLIC_IP]),
        transport_factory=factory,
        clock=clock or time.monotonic,
    )
    return fetcher, factory


@pytest.mark.parametrize(
    ("url", "expected_scheme", "expected_sni"),
    [
        ("http://jobs.example/jobs/1", "http", None),
        ("https://jobs.example/jobs/1", "https", "jobs.example"),
    ],
)
def test_public_http_and_https_connect_to_validated_ip_with_logical_identity(
    url: str,
    expected_scheme: str,
    expected_sni: str | None,
) -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, headers={"content-type": "text/html"}, content=b"job")

    fetcher, factory = _fetcher_for_handler(handler)
    result = asyncio.run(fetcher.fetch(url))
    request = factory.transports[0].requests[0]

    assert request.url.host == PUBLIC_IP
    assert request.url.scheme == expected_scheme
    assert request.headers["host"] == "jobs.example"
    assert request.headers["accept-encoding"] == "identity"
    assert request.extensions.get("sni_hostname") == expected_sni
    assert result.url == url
    assert result.body == b"job"
    assert result.redirect_count == 0
    assert factory.transports[0].closed


def test_normalized_idna_host_is_used_for_dns_host_header_and_sni() -> None:
    resolver_calls: list[tuple[str, int]] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b"job")

    fetcher, factory = _fetcher_for_handler(
        handler,
        resolver=_resolver_for([PUBLIC_IP], resolver_calls),
    )
    result = asyncio.run(fetcher.fetch("https://b\u00fccher.example/jobs"))
    request = factory.transports[0].requests[0]

    assert resolver_calls == [("xn--bcher-kva.example", 443)]
    assert request.headers["host"] == "xn--bcher-kva.example"
    assert request.extensions["sni_hostname"] == "xn--bcher-kva.example"
    assert result.url == "https://xn--bcher-kva.example/jobs"


def test_ipv6_literal_uses_bracketed_host_and_unbracketed_sni() -> None:
    async def unused_resolver(host: str, port: int) -> list[str]:
        raise AssertionError("IP literals must not use DNS")

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b"job")

    fetcher, factory = _fetcher_for_handler(handler, resolver=unused_resolver)
    asyncio.run(fetcher.fetch(f"https://[{PUBLIC_IPV6}]/jobs"))
    request = factory.transports[0].requests[0]

    assert request.url.host == PUBLIC_IPV6
    assert request.headers["host"] == f"[{PUBLIC_IPV6}]"
    assert request.extensions["sni_hostname"] == PUBLIC_IPV6


@pytest.mark.parametrize(
    "address",
    [
        "10.0.0.1",
        "127.0.0.1",
        "169.254.169.254",
        "0.0.0.0",
        "224.0.0.1",
        "240.0.0.1",
        "192.0.2.1",
        "100.100.100.200",
        "::1",
        "::",
        "fe80::1",
        "fd00:ec2::254",
        "ff02::1",
        "2001:db8::1",
        "::ffff:127.0.0.1",
        "::ffff:169.254.169.254",
    ],
)
def test_rejects_non_public_dns_addresses(address: str) -> None:
    with pytest.raises(UnsafeJobUrlError) as exception_info:
        asyncio.run(
            validate_public_url(
                "https://jobs.example/role",
                _resolver_for([address]),
            )
        )

    assert exception_info.value.reason == "non_public_ip"


@pytest.mark.parametrize(
    "url",
    [
        "http://127.0.0.1/jobs",
        "http://169.254.169.254/latest/meta-data",
        "https://[::1]/jobs",
        "https://[::ffff:127.0.0.1]/jobs",
    ],
)
def test_direct_non_public_literals_cannot_be_overridden_by_dns(url: str) -> None:
    async def resolver(host: str, port: int) -> list[str]:
        raise AssertionError("IP literals must not use DNS")

    with pytest.raises(UnsafeJobUrlError) as exception_info:
        asyncio.run(validate_public_url(url, resolver))

    assert exception_info.value.reason == "non_public_ip"


def test_accepts_ipv4_mapped_public_address() -> None:
    target = asyncio.run(
        validate_public_url(
            "https://jobs.example/role",
            _resolver_for([f"::ffff:{PUBLIC_IP}"]),
        )
    )

    assert tuple(ipaddress.ip_address(address) for address in target.addresses) == (
        ipaddress.ip_address(f"::ffff:{PUBLIC_IP}"),
    )


def test_rejects_entire_mixed_public_and_private_answer_set() -> None:
    with pytest.raises(UnsafeJobUrlError) as exception_info:
        asyncio.run(
            validate_public_url(
                "https://jobs.example/role",
                _resolver_for([PUBLIC_IP, "10.0.0.1"]),
            )
        )

    assert exception_info.value.reason == "non_public_ip"


@pytest.mark.parametrize("host", ["127.1", "2130706433", "0x7f000001"])
def test_alternate_numeric_hosts_are_resolved_and_rejected(host: str) -> None:
    calls: list[tuple[str, int]] = []

    with pytest.raises(UnsafeJobUrlError):
        asyncio.run(
            validate_public_url(
                f"http://{host}/jobs",
                _resolver_for(["127.0.0.1"], calls),
            )
        )

    assert calls == [(host, 80)]


@pytest.mark.parametrize(
    ("url", "reason"),
    [
        ("ftp://example.com/jobs", "unsupported_scheme"),
        ("file:///etc/passwd", "unsupported_scheme"),
        ("https://user:pass@example.com/jobs", "url_credentials"),
        ("https://@example.com/jobs", "url_credentials"),
        ("https://example.com:80/jobs", "unsupported_port"),
        ("http://example.com:443/jobs", "unsupported_port"),
        ("http://0177.0.0.1/jobs", "malformed_url"),
    ],
)
def test_rejects_unsupported_urls(url: str, reason: str) -> None:
    with pytest.raises(UnsafeJobUrlError) as exception_info:
        asyncio.run(validate_public_url(url, _resolver_for([PUBLIC_IP])))

    assert exception_info.value.reason == reason


def test_redirect_to_private_target_is_rejected_before_connection() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            302,
            headers={"Location": "http://169.254.169.254/latest/meta-data"},
        )

    fetcher, factory = _fetcher_for_handler(handler)

    with pytest.raises(UnsafeJobUrlError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "unsafe_redirect_target"
    assert exception_info.value.redirect_count == 1
    assert len(factory.transports) == 1


def test_each_redirect_is_resolved_independently_and_relative_redirects_work() -> None:
    resolver_calls: list[tuple[str, int]] = []

    async def resolver(host: str, port: int) -> list[str]:
        resolver_calls.append((host, port))
        return [PUBLIC_IP if host == "jobs.example" else SECOND_PUBLIC_IP]

    async def handler(request: httpx.Request) -> httpx.Response:
        if request.headers["host"] == "jobs.example" and request.url.path == "/start":
            return httpx.Response(302, headers={"Location": "/next"})
        if request.headers["host"] == "jobs.example" and request.url.path == "/next":
            return httpx.Response(
                302,
                headers={"Location": "https://careers.example/final"},
            )
        return httpx.Response(200, content=b"final")

    fetcher, factory = _fetcher_for_handler(handler, resolver=resolver)
    result = asyncio.run(fetcher.fetch("https://jobs.example/start"))

    assert resolver_calls == [
        ("jobs.example", 443),
        ("jobs.example", 443),
        ("careers.example", 443),
    ]
    assert [transport.requests[0].url.host for transport in factory.transports] == [
        PUBLIC_IP,
        PUBLIC_IP,
        SECOND_PUBLIC_IP,
    ]
    assert result.url == "https://careers.example/final"
    assert result.redirect_count == 2


def test_dns_rebinding_cannot_change_the_connected_address() -> None:
    calls = 0

    async def rebinding_resolver(host: str, port: int) -> list[str]:
        nonlocal calls
        calls += 1
        return [PUBLIC_IP] if calls == 1 else ["127.0.0.1"]

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b"job")

    fetcher, factory = _fetcher_for_handler(handler, resolver=rebinding_resolver)
    asyncio.run(fetcher.fetch("https://jobs.example/role?token=secret"))

    assert calls == 1
    assert factory.transports[0].requests[0].url.host == PUBLIC_IP


def test_logical_authorities_sharing_an_ip_use_separate_closed_transports() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        if request.headers["host"] == "one.example":
            return httpx.Response(302, headers={"Location": "https://two.example/final"})
        return httpx.Response(200, content=b"final")

    fetcher, factory = _fetcher_for_handler(handler)
    asyncio.run(fetcher.fetch("https://one.example/start"))

    assert len(factory.transports) == 2
    assert factory.transports[0] is not factory.transports[1]
    assert [transport.requests[0].headers["host"] for transport in factory.transports] == [
        "one.example",
        "two.example",
    ]
    assert all(transport.closed for transport in factory.transports)


def test_redirect_limit_preserves_configured_maximum() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(302, headers={"Location": "/again"})

    fetcher, factory = _fetcher_for_handler(
        handler,
        settings=_settings(max_redirects=2),
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/start"))

    assert exception_info.value.reason == "too_many_redirects"
    assert exception_info.value.redirect_count == 3
    assert len(factory.transports) == 3
    assert all(transport.closed for transport in factory.transports)


def test_only_connection_failures_try_another_validated_address() -> None:
    attempts = 0

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            raise httpx.ConnectError("connect failed", request=request)
        return httpx.Response(200, content=b"job")

    fetcher, factory = _fetcher_for_handler(
        handler,
        resolver=_resolver_for([PUBLIC_IP, SECOND_PUBLIC_IP]),
    )
    result = asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert result.body == b"job"
    assert [transport.requests[0].url.host for transport in factory.transports] == [
        PUBLIC_IP,
        SECOND_PUBLIC_IP,
    ]
    assert all(transport.closed for transport in factory.transports)


def test_large_public_answer_set_has_bounded_connection_attempts() -> None:
    addresses = [f"8.8.8.{last_octet}" for last_octet in range(1, 11)]

    async def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("connect failed", request=request)

    fetcher, factory = _fetcher_for_handler(
        handler,
        resolver=_resolver_for(addresses),
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "http_client_error"
    assert len(factory.transports) == 4
    assert [transport.requests[0].url.host for transport in factory.transports] == addresses[:4]
    assert all(transport.closed for transport in factory.transports)


def test_proxy_environment_variables_do_not_change_routing(monkeypatch) -> None:
    for name in ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY"):
        monkeypatch.setenv(name, "http://proxy-user:proxy-secret@127.0.0.1:9")

    trust_env_values: list[object] = []
    original_init = httpx.AsyncClient.__init__

    def recording_init(self, *args, **kwargs):
        trust_env_values.append(kwargs.get("trust_env"))
        original_init(self, *args, **kwargs)

    monkeypatch.setattr(httpx.AsyncClient, "__init__", recording_init)

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b"direct")

    fetcher, factory = _fetcher_for_handler(handler)
    result = asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert result.body == b"direct"
    assert trust_env_values == [False]
    assert factory.transports[0].requests[0].url.host == PUBLIC_IP
    assert "proxy-authorization" not in factory.transports[0].requests[0].headers


def test_default_transport_disables_environment_and_retries(monkeypatch) -> None:
    captured: dict[str, object] = {}
    sentinel = httpx.MockTransport(lambda request: httpx.Response(200))

    def transport_factory(**kwargs):
        captured.update(kwargs)
        return sentinel

    monkeypatch.setattr(httpx, "AsyncHTTPTransport", transport_factory)

    assert _create_transport() is sentinel
    assert captured == {"verify": True, "trust_env": False, "retries": 0}


def test_default_dns_resolution_runs_off_the_event_loop_thread(monkeypatch) -> None:
    event_loop_thread = threading.get_ident()
    resolver_threads: list[int] = []

    def fake_getaddrinfo(host, port, family=0, type=0, proto=0, flags=0):
        resolver_threads.append(threading.get_ident())
        return [(socket.AF_INET, type, proto, "", (PUBLIC_IP, port))]

    monkeypatch.setattr(socket, "getaddrinfo", fake_getaddrinfo)
    result = asyncio.run(resolve_host_ips("jobs.example", 443))

    assert result == [PUBLIC_IP]
    assert resolver_threads
    assert resolver_threads[0] != event_loop_thread


def test_blocked_dns_is_bounded_by_total_timeout() -> None:
    async def blocked_resolver(host: str, port: int) -> list[str]:
        await asyncio.Event().wait()
        return [PUBLIC_IP]

    transports_created = 0

    def transport_factory() -> httpx.AsyncBaseTransport:
        nonlocal transports_created
        transports_created += 1
        return httpx.MockTransport(lambda request: httpx.Response(200))

    fetcher = JobPageFetcher(
        _settings(fetch_timeout_seconds=0.01),
        resolver=blocked_resolver,
        transport_factory=transport_factory,
    )

    with pytest.raises(JobFetchTimeoutError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "fetch_timeout"
    assert transports_created == 0


def test_total_deadline_does_not_reset_across_redirects() -> None:
    clock = FakeClock()
    requests = 0

    async def resolver(host: str, port: int) -> list[str]:
        clock.advance(2.0 if host != "three.example" else 3.0)
        return [PUBLIC_IP]

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal requests
        requests += 1
        clock.advance(2.0)
        next_host = "two.example" if requests == 1 else "three.example"
        return httpx.Response(302, headers={"Location": f"https://{next_host}/next"})

    fetcher, _ = _fetcher_for_handler(
        handler,
        resolver=resolver,
        settings=_settings(fetch_timeout_seconds=10.0),
        clock=clock,
    )

    with pytest.raises(JobFetchTimeoutError):
        asyncio.run(fetcher.fetch("https://one.example/start"))

    assert requests == 2
    assert clock.now == 11.0


def test_remaining_http_timeout_decreases_across_redirects() -> None:
    clock = FakeClock()
    connect_timeouts: list[float] = []

    async def resolver(host: str, port: int) -> list[str]:
        clock.advance(1.0)
        return [PUBLIC_IP]

    async def handler(request: httpx.Request) -> httpx.Response:
        connect_timeouts.append(request.extensions["timeout"]["connect"])
        clock.advance(2.0)
        if len(connect_timeouts) == 1:
            return httpx.Response(302, headers={"Location": "https://two.example/final"})
        return httpx.Response(200, content=b"done")

    fetcher, _ = _fetcher_for_handler(
        handler,
        resolver=resolver,
        settings=_settings(fetch_timeout_seconds=10.0),
        clock=clock,
    )
    asyncio.run(fetcher.fetch("https://one.example/start"))

    assert connect_timeouts == [9.0, 6.0]


def test_timeout_during_body_stream_closes_response_and_transport() -> None:
    stream = BlockingStream()

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, stream=stream)

    fetcher, factory = _fetcher_for_handler(
        handler,
        settings=_settings(fetch_timeout_seconds=0.01),
    )

    with pytest.raises(JobFetchTimeoutError):
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert stream.closed
    assert factory.transports[0].closed


def test_timeout_waiting_for_headers_closes_transport() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        await asyncio.Event().wait()
        return httpx.Response(200)

    fetcher, factory = _fetcher_for_handler(
        handler,
        settings=_settings(fetch_timeout_seconds=0.01),
    )

    with pytest.raises(JobFetchTimeoutError):
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert factory.transports[0].closed


def test_stream_failure_closes_response_and_transport() -> None:
    class FailingStream(AsyncBytesStream):
        async def __aiter__(self):
            raise httpx.ReadError("read failed")
            yield b"unreachable"  # pragma: no cover

    stream = FailingStream()
    fetcher, factory = _fetcher_for_response(httpx.Response(200, stream=stream))

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "http_client_error"
    assert stream.closed
    assert factory.transports[0].closed


def test_redirect_rebuilds_headers_without_cookie_or_authorization() -> None:
    seen_headers: list[httpx.Headers] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        seen_headers.append(request.headers)
        if len(seen_headers) == 1:
            return httpx.Response(
                302,
                headers={
                    "Location": "https://two.example/final",
                    "Set-Cookie": "session=secret",
                },
            )
        return httpx.Response(200, content=b"done")

    fetcher, _ = _fetcher_for_handler(handler)
    asyncio.run(fetcher.fetch("https://one.example/start"))

    for headers in seen_headers:
        assert "cookie" not in headers
        assert "authorization" not in headers
        assert "proxy-authorization" not in headers
    assert seen_headers[1]["host"] == "two.example"


def test_fetch_error_includes_status_and_content_type_diagnostics() -> None:
    fetcher, _ = _fetcher_for_response(
        httpx.Response(
            403,
            headers={"content-type": "text/html; charset=utf-8"},
            content=b"forbidden",
        )
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "http_status_error"
    assert exception_info.value.status_code == 403
    assert exception_info.value.content_type == "text/html; charset=utf-8"


def test_successful_fetch_result_includes_status_code() -> None:
    fetcher, _ = _fetcher_for_response(
        httpx.Response(
            200,
            headers={"content-type": "text/html; charset=utf-8"},
            content=b"<html><body>Job description</body></html>",
        )
    )
    result = asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert result.status_code == 200
    assert result.content_type == "text/html; charset=utf-8"


def test_default_limit_accepts_google_sized_response() -> None:
    body = b"x" * 1_300_000
    fetcher, _ = _fetcher_for_response(
        httpx.Response(200, headers={"content-type": "text/html"}, content=body)
    )

    assert asyncio.run(fetcher.fetch("https://jobs.example/role")).body == body


def test_rejects_non_success_status_outside_redirect_handling() -> None:
    fetcher, _ = _fetcher_for_response(httpx.Response(304))

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "http_status_error"


def test_rejects_unsupported_content_type() -> None:
    fetcher, _ = _fetcher_for_response(
        httpx.Response(200, headers={"content-type": "image/png"}, content=b"png")
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "unsupported_content_type"


def test_rejects_oversized_content_length_before_reading() -> None:
    stream = AsyncBytesStream(b"x" * 11)
    fetcher, _ = _fetcher_for_response(
        httpx.Response(
            200,
            headers={"content-type": "text/html", "content-length": "11"},
            stream=stream,
        ),
        settings=_settings(max_response_bytes=10),
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "content_length_too_large"
    assert not stream.iterated
    assert stream.closed


def test_rejects_oversized_streamed_body() -> None:
    fetcher, _ = _fetcher_for_response(
        httpx.Response(
            200,
            headers={"content-type": "text/html"},
            stream=AsyncBytesStream(b"123456", b"78901"),
        ),
        settings=_settings(max_response_bytes=10),
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "response_too_large"


@pytest.mark.parametrize("encoding", ["gzip", "br", "deflate", "gzip, identity"])
def test_rejects_non_identity_content_encoding_without_reading(encoding: str) -> None:
    stream = AsyncBytesStream(b"compressed")
    fetcher, _ = _fetcher_for_response(
        httpx.Response(
            200,
            headers={"content-encoding": encoding},
            stream=stream,
        )
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "unsupported_content_encoding"
    assert not stream.iterated


@pytest.mark.parametrize("encoding", ["gzip", "br", "deflate"])
def test_redirect_ignores_unused_body_content_encoding(encoding: str) -> None:
    redirect_stream = AsyncBytesStream(b"unused")
    requests = 0

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal requests
        requests += 1
        if requests == 1:
            return httpx.Response(
                302,
                headers={
                    "location": "https://two.example/final",
                    "content-encoding": encoding,
                },
                stream=redirect_stream,
            )
        return httpx.Response(200, stream=AsyncBytesStream(b"final"))

    fetcher, _ = _fetcher_for_handler(handler)
    result = asyncio.run(fetcher.fetch("https://one.example/start"))

    assert result.body == b"final"
    assert not redirect_stream.iterated
    assert redirect_stream.closed


def test_identity_content_encoding_streams_raw_bytes() -> None:
    body = b"not-compressed"
    fetcher, _ = _fetcher_for_response(
        httpx.Response(
            200,
            headers={"content-encoding": "Identity"},
            stream=AsyncBytesStream(body),
        )
    )

    assert asyncio.run(fetcher.fetch("https://jobs.example/role")).body == body


def test_env_configured_low_response_limit_is_enforced(monkeypatch) -> None:
    monkeypatch.setenv("AI_SERVICE_MAX_RESPONSE_BYTES", "10")
    fetcher, _ = _fetcher_for_response(
        httpx.Response(200, stream=AsyncBytesStream(b"x" * 11)),
        settings=Settings.from_env(),
    )

    with pytest.raises(JobFetchError) as exception_info:
        asyncio.run(fetcher.fetch("https://jobs.example/role"))

    assert exception_info.value.reason == "response_too_large"


def test_routine_http_client_request_logging_is_suppressed(caplog) -> None:
    from app.main import _suppress_http_client_logging

    _suppress_http_client_logging()
    caplog.set_level(logging.DEBUG)

    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, content=b"job")

    fetcher, _ = _fetcher_for_handler(handler)
    asyncio.run(fetcher.fetch("https://jobs.example/private/path?token=secret"))

    logging.getLogger("httpx").info("GET https://93.184.216.34/private/path?token=secret")
    logging.getLogger("httpcore.connection").debug("connect_tcp host=93.184.216.34 token=secret")
    logging.getLogger("httpx").warning("preserved-http-warning")

    assert "93.184.216.34" not in caplog.text
    assert "token=secret" not in caplog.text
    assert "preserved-http-warning" in caplog.text


def _fetcher_for_response(
    response: httpx.Response,
    *,
    settings: Settings | None = None,
) -> tuple[JobPageFetcher, RecordingTransportFactory]:
    async def handler(request: httpx.Request) -> httpx.Response:
        return response

    return _fetcher_for_handler(handler, settings=settings)

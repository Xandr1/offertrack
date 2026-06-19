import ipaddress
import socket
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from urllib.parse import urljoin

import httpx

from app.settings import Settings

Resolver = Callable[[str, int], Iterable[str]]

SAFE_HEADERS = {
    "User-Agent": "OfferTrackAIParser/0.1",
    "Accept": "text/html,application/xhtml+xml,text/plain;q=0.8,*/*;q=0.1",
}
REDIRECT_STATUSES = {301, 302, 303, 307, 308}
SUPPORTED_CONTENT_TYPES = (
    "text/html",
    "application/xhtml+xml",
    "text/plain",
)


class UnsafeJobUrlError(ValueError):
    pass


class JobFetchError(RuntimeError):
    pass


class JobFetchTimeoutError(JobFetchError):
    pass


@dataclass(frozen=True)
class FetchResult:
    url: str
    body: bytes
    content_type: str | None


def resolve_host_ips(host: str, port: int) -> list[str]:
    try:
        records = socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)
    except socket.gaierror as exception:
        raise UnsafeJobUrlError("Job URL host could not be resolved.") from exception

    return sorted({record[4][0] for record in records})


def validate_public_url(raw_url: str, resolver: Resolver = resolve_host_ips) -> httpx.URL:
    try:
        url = httpx.URL(raw_url)
    except httpx.InvalidURL as exception:
        raise UnsafeJobUrlError("Job URL is malformed.") from exception

    if url.scheme.lower() not in {"http", "https"}:
        raise UnsafeJobUrlError("Job URL must use http or https.")

    if not url.host:
        raise UnsafeJobUrlError("Job URL must include a host.")

    if url.username or url.password:
        raise UnsafeJobUrlError("Job URL must not include credentials.")

    port = url.port or (443 if url.scheme.lower() == "https" else 80)
    ips = _resolve_url_ips(url.host, port, resolver)

    if not ips:
        raise UnsafeJobUrlError("Job URL host could not be resolved.")

    for address in ips:
        if not _is_public_ip(address):
            raise UnsafeJobUrlError("Job URL resolves to a non-public address.")

    # TODO: This strict pre-request DNS validation does not fully prevent DNS rebinding because
    # httpx resolves again when opening the connection. Full mitigation belongs in future
    # production hardening via a custom transport or network-level egress restrictions.
    return url


class JobPageFetcher:
    def __init__(
        self,
        settings: Settings,
        resolver: Resolver = resolve_host_ips,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self.settings = settings
        self.resolver = resolver
        self.transport = transport

    async def fetch(self, job_url: str) -> FetchResult:
        current_url = validate_public_url(job_url, self.resolver)
        timeout = httpx.Timeout(self.settings.fetch_timeout_seconds)

        async with httpx.AsyncClient(
            follow_redirects=False, timeout=timeout, transport=self.transport
        ) as client:
            for redirect_count in range(self.settings.max_redirects + 1):
                client.cookies.clear()
                request = client.build_request("GET", str(current_url), headers=SAFE_HEADERS)
                request.headers.pop("cookie", None)
                request.headers.pop("authorization", None)

                try:
                    response = await client.send(request, stream=True)
                except httpx.TimeoutException as exception:
                    raise JobFetchTimeoutError("Timed out fetching job URL.") from exception
                except httpx.HTTPError as exception:
                    raise JobFetchError("Could not fetch job URL.") from exception

                try:
                    if response.status_code in REDIRECT_STATUSES:
                        location = response.headers.get("location")
                        if not location:
                            raise JobFetchError("Redirect response did not include a target.")

                        if redirect_count >= self.settings.max_redirects:
                            raise JobFetchError("Too many redirects while fetching job URL.")

                        redirected_url = urljoin(str(current_url), location)
                        current_url = validate_public_url(redirected_url, self.resolver)
                        continue

                    if response.status_code >= 400:
                        raise JobFetchError("Job URL returned an unsuccessful status.")

                    content_type = response.headers.get("content-type")
                    if not _is_supported_content_type(content_type):
                        raise JobFetchError("Job URL returned an unsupported content type.")

                    content_length = response.headers.get("content-length")
                    if _is_oversized_content_length(
                        content_length, self.settings.max_response_bytes
                    ):
                        raise JobFetchError("Job URL response was too large.")

                    body = await _read_limited_response(response, self.settings.max_response_bytes)
                    return FetchResult(
                        url=str(current_url),
                        body=body,
                        content_type=content_type,
                    )
                finally:
                    await response.aclose()

        raise JobFetchError("Too many redirects while fetching job URL.")


def _resolve_url_ips(host: str, port: int, resolver: Resolver) -> list[str]:
    try:
        return [str(ipaddress.ip_address(host))]
    except ValueError:
        return [str(ipaddress.ip_address(address)) for address in resolver(host, port)]


def _is_public_ip(address: str) -> bool:
    ip = ipaddress.ip_address(address)

    return (
        ip.is_global
        and not ip.is_loopback
        and not ip.is_private
        and not ip.is_link_local
        and not ip.is_multicast
        and not ip.is_reserved
        and not ip.is_unspecified
    )


def _is_supported_content_type(content_type: str | None) -> bool:
    if content_type is None or not content_type.strip():
        return True

    normalized_content_type = content_type.split(";", 1)[0].strip().lower()
    return normalized_content_type in SUPPORTED_CONTENT_TYPES


def _is_oversized_content_length(content_length: str | None, max_bytes: int) -> bool:
    if content_length is None or not content_length.strip():
        return False

    try:
        return int(content_length) > max_bytes
    except ValueError:
        return False


async def _read_limited_response(response: httpx.Response, max_bytes: int) -> bytes:
    chunks: list[bytes] = []
    total = 0

    async for chunk in response.aiter_bytes():
        total += len(chunk)

        if total > max_bytes:
            raise JobFetchError("Job URL response was too large.")

        chunks.append(chunk)

    return b"".join(chunks)

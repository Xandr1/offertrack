import asyncio
import ipaddress
import socket
import time
from collections.abc import Awaitable, Callable, Iterable
from dataclasses import dataclass
from urllib.parse import urljoin, urlsplit

import httpx

from app.settings import Settings

Resolver = Callable[[str, int], Awaitable[Iterable[str]]]
TransportFactory = Callable[[], httpx.AsyncBaseTransport]
Clock = Callable[[], float]

SAFE_HEADERS = {
    "User-Agent": "OfferTrackAIService/0.1",
    "Accept": "text/html,application/xhtml+xml,text/plain;q=0.8,*/*;q=0.1",
    "Accept-Encoding": "identity",
}
MAX_ADDRESS_ATTEMPTS = 4
REDIRECT_STATUSES = {301, 302, 303, 307, 308}
SUPPORTED_CONTENT_TYPES = (
    "text/html",
    "application/xhtml+xml",
    "text/plain",
)


class UnsafeJobUrlError(ValueError):
    def __init__(
        self,
        message: str,
        *,
        reason: str = "unsafe_job_url",
        redirect_target_host: str | None = None,
        redirect_count: int = 0,
    ) -> None:
        super().__init__(message)
        self.reason = reason
        self.status_code: int | None = None
        self.content_type: str | None = None
        self.redirect_target_host = redirect_target_host
        self.redirect_count = redirect_count


class JobFetchError(RuntimeError):
    def __init__(
        self,
        message: str,
        *,
        reason: str = "fetch_failed",
        status_code: int | None = None,
        content_type: str | None = None,
        redirect_target_host: str | None = None,
        redirect_count: int = 0,
    ) -> None:
        super().__init__(message)
        self.reason = reason
        self.status_code = status_code
        self.content_type = content_type
        self.redirect_target_host = redirect_target_host
        self.redirect_count = redirect_count


class JobFetchTimeoutError(JobFetchError):
    pass


@dataclass(frozen=True)
class FetchResult:
    url: str
    body: bytes
    content_type: str | None
    status_code: int | None = None
    redirect_count: int = 0


@dataclass(frozen=True)
class ResolvedTarget:
    logical_url: httpx.URL
    host: str
    authority: str
    port: int
    addresses: tuple[str, ...]


@dataclass(frozen=True)
class _HopResult:
    redirect_location: str | None
    body: bytes | None
    content_type: str | None
    status_code: int


async def resolve_host_ips(host: str, port: int) -> list[str]:
    loop = asyncio.get_running_loop()

    try:
        # The event-loop API keeps the loop responsive, but cancellation cannot guarantee that
        # the platform resolver call already running in its executor is stopped.
        records = await loop.getaddrinfo(
            host,
            port,
            family=socket.AF_UNSPEC,
            type=socket.SOCK_STREAM,
            proto=socket.IPPROTO_TCP,
        )
    except OSError as exception:
        raise UnsafeJobUrlError(
            "Job URL host could not be resolved.", reason="host_not_resolved"
        ) from exception

    return _deduplicate(str(record[4][0]) for record in records)


async def validate_public_url(
    raw_url: str,
    resolver: Resolver = resolve_host_ips,
) -> ResolvedTarget:
    try:
        url = httpx.URL(raw_url)
    except httpx.InvalidURL as exception:
        raise UnsafeJobUrlError("Job URL is malformed.", reason="malformed_url") from exception

    scheme = url.scheme.lower()
    if scheme not in {"http", "https"}:
        raise UnsafeJobUrlError("Job URL must use http or https.", reason="unsupported_scheme")

    if not url.raw_host:
        raise UnsafeJobUrlError("Job URL must include a host.", reason="missing_host")

    if url.userinfo or "@" in urlsplit(raw_url).netloc:
        raise UnsafeJobUrlError("Job URL must not include credentials.", reason="url_credentials")

    expected_port = 443 if scheme == "https" else 80
    if url.port is not None and url.port != expected_port:
        raise UnsafeJobUrlError(
            "Job URL must use the default port for its scheme.", reason="unsupported_port"
        )

    host = url.raw_host.decode("ascii")
    addresses = await _resolve_url_ips(host, expected_port, resolver)

    if not addresses:
        raise UnsafeJobUrlError("Job URL host could not be resolved.", reason="host_not_resolved")

    if any(not _is_public_ip(address) for address in addresses):
        raise UnsafeJobUrlError("Job URL resolves to a non-public address.", reason="non_public_ip")

    return ResolvedTarget(
        logical_url=url,
        host=host,
        authority=url.netloc.decode("ascii"),
        port=expected_port,
        addresses=tuple(addresses),
    )


class JobPageFetcher:
    def __init__(
        self,
        settings: Settings,
        resolver: Resolver = resolve_host_ips,
        transport_factory: TransportFactory | None = None,
        clock: Clock = time.monotonic,
    ) -> None:
        self.settings = settings
        self.resolver = resolver
        self.transport_factory = transport_factory or _create_transport
        self.clock = clock

    async def fetch(self, job_url: str) -> FetchResult:
        redirect_count = 0
        deadline = self.clock() + self.settings.fetch_timeout_seconds

        try:
            async with asyncio.timeout(self.settings.fetch_timeout_seconds):
                _remaining_seconds(deadline, self.clock)
                current_target = await validate_public_url(job_url, self.resolver)
                _remaining_seconds(deadline, self.clock)

                while True:
                    hop = await self._fetch_target(current_target, deadline, redirect_count)

                    if hop.redirect_location is None:
                        assert hop.body is not None
                        return FetchResult(
                            url=str(current_target.logical_url),
                            body=hop.body,
                            content_type=hop.content_type,
                            status_code=hop.status_code,
                            redirect_count=redirect_count,
                        )

                    redirected_url = urljoin(str(current_target.logical_url), hop.redirect_location)
                    redirect_target_host = _safe_url_host(redirected_url)
                    next_redirect_count = redirect_count + 1

                    if redirect_count >= self.settings.max_redirects:
                        raise JobFetchError(
                            "Too many redirects while fetching job URL.",
                            reason="too_many_redirects",
                            status_code=hop.status_code,
                            content_type=hop.content_type,
                            redirect_target_host=redirect_target_host,
                            redirect_count=next_redirect_count,
                        )

                    _remaining_seconds(deadline, self.clock)
                    try:
                        current_target = await validate_public_url(redirected_url, self.resolver)
                    except UnsafeJobUrlError as exception:
                        raise UnsafeJobUrlError(
                            "Redirect target is invalid or unsafe.",
                            reason="unsafe_redirect_target",
                            redirect_target_host=redirect_target_host,
                            redirect_count=next_redirect_count,
                        ) from exception

                    redirect_count = next_redirect_count
                    _remaining_seconds(deadline, self.clock)
        except (UnsafeJobUrlError, JobFetchError):
            raise
        except TimeoutError as exception:
            raise JobFetchTimeoutError(
                "Timed out fetching job URL.",
                reason="fetch_timeout",
                redirect_count=redirect_count,
            ) from exception
        except httpx.TimeoutException as exception:
            raise JobFetchTimeoutError(
                "Timed out fetching job URL.",
                reason="fetch_timeout",
                redirect_count=redirect_count,
            ) from exception
        except httpx.HTTPError as exception:
            raise JobFetchError(
                "Could not fetch job URL.",
                reason="http_client_error",
                redirect_count=redirect_count,
            ) from exception

    async def _fetch_target(
        self,
        target: ResolvedTarget,
        deadline: float,
        redirect_count: int,
    ) -> _HopResult:
        last_connect_error: httpx.ConnectError | httpx.ConnectTimeout | None = None
        saw_connect_timeout = False

        for address in target.addresses[:MAX_ADDRESS_ATTEMPTS]:
            remaining = _remaining_seconds(deadline, self.clock)

            try:
                return await self._fetch_address(
                    target,
                    address,
                    remaining,
                    redirect_count,
                )
            except (httpx.ConnectError, httpx.ConnectTimeout) as exception:
                last_connect_error = exception
                saw_connect_timeout = saw_connect_timeout or isinstance(
                    exception, httpx.ConnectTimeout
                )

        if saw_connect_timeout:
            raise JobFetchTimeoutError(
                "Timed out fetching job URL.",
                reason="fetch_timeout",
                redirect_count=redirect_count,
            ) from last_connect_error

        raise JobFetchError(
            "Could not fetch job URL.",
            reason="http_client_error",
            redirect_count=redirect_count,
        ) from last_connect_error

    async def _fetch_address(
        self,
        target: ResolvedTarget,
        address: str,
        remaining: float,
        redirect_count: int,
    ) -> _HopResult:
        transport = self.transport_factory()
        client = httpx.AsyncClient(
            follow_redirects=False,
            timeout=httpx.Timeout(remaining),
            transport=transport,
            trust_env=False,
        )
        response: httpx.Response | None = None

        try:
            physical_url = target.logical_url.copy_with(host=address)
            headers = {**SAFE_HEADERS, "Host": target.authority}
            extensions = (
                {"sni_hostname": target.host}
                if target.logical_url.scheme.lower() == "https"
                else None
            )
            request = httpx.Request(
                "GET",
                physical_url,
                headers=headers,
                extensions=extensions,
            )
            response = await client.send(request, stream=True)

            content_type = response.headers.get("content-type")
            if response.status_code in REDIRECT_STATUSES:
                location = response.headers.get("location")
                if not location:
                    raise JobFetchError(
                        "Redirect response did not include a target.",
                        reason="redirect_missing_location",
                        status_code=response.status_code,
                        content_type=content_type,
                        redirect_count=redirect_count,
                    )

                return _HopResult(
                    redirect_location=location,
                    body=None,
                    content_type=content_type,
                    status_code=response.status_code,
                )

            content_encoding = response.headers.get("content-encoding")
            if not _is_identity_content_encoding(content_encoding):
                raise JobFetchError(
                    "Job URL returned an unsupported content encoding.",
                    reason="unsupported_content_encoding",
                    status_code=response.status_code,
                    content_type=content_type,
                    redirect_count=redirect_count,
                )

            if not 200 <= response.status_code < 300:
                raise JobFetchError(
                    "Job URL returned an unsuccessful status.",
                    reason="http_status_error",
                    status_code=response.status_code,
                    content_type=content_type,
                    redirect_count=redirect_count,
                )

            if not _is_supported_content_type(content_type):
                raise JobFetchError(
                    "Job URL returned an unsupported content type.",
                    reason="unsupported_content_type",
                    status_code=response.status_code,
                    content_type=content_type,
                    redirect_count=redirect_count,
                )

            content_length = response.headers.get("content-length")
            if _is_oversized_content_length(content_length, self.settings.max_response_bytes):
                raise JobFetchError(
                    "Job URL response was too large.",
                    reason="content_length_too_large",
                    status_code=response.status_code,
                    content_type=content_type,
                    redirect_count=redirect_count,
                )

            body = await _read_limited_response(
                response,
                self.settings.max_response_bytes,
                redirect_count,
            )
            return _HopResult(
                redirect_location=None,
                body=body,
                content_type=content_type,
                status_code=response.status_code,
            )
        finally:
            await _close_resources(response, client)


def _create_transport() -> httpx.AsyncBaseTransport:
    return httpx.AsyncHTTPTransport(verify=True, trust_env=False, retries=0)


async def _resolve_url_ips(host: str, port: int, resolver: Resolver) -> list[str]:
    try:
        literal = ipaddress.ip_address(host)
    except ValueError:
        try:
            resolved = await resolver(host, port)
            addresses = _deduplicate(resolved)
        except UnsafeJobUrlError:
            raise
        except (OSError, ValueError) as exception:
            raise UnsafeJobUrlError(
                "Job URL host could not be resolved.", reason="host_not_resolved"
            ) from exception
        return _normalize_addresses(addresses)

    return [str(literal)]


def _normalize_addresses(addresses: Iterable[str]) -> list[str]:
    normalized: list[str] = []

    try:
        for address in addresses:
            normalized.append(str(ipaddress.ip_address(address)))
    except ValueError as exception:
        raise UnsafeJobUrlError(
            "Job URL host could not be resolved.", reason="host_not_resolved"
        ) from exception

    return _deduplicate(normalized)


def _deduplicate(values: Iterable[str]) -> list[str]:
    return list(dict.fromkeys(str(value) for value in values))


def _is_public_ip(address: str) -> bool:
    ip = ipaddress.ip_address(address)
    classified_ip = ip.ipv4_mapped if isinstance(ip, ipaddress.IPv6Address) else None
    if classified_ip is None:
        classified_ip = ip

    return (
        classified_ip.is_global
        and not classified_ip.is_loopback
        and not classified_ip.is_private
        and not classified_ip.is_link_local
        and not classified_ip.is_multicast
        and not classified_ip.is_reserved
        and not classified_ip.is_unspecified
    )


def _is_supported_content_type(content_type: str | None) -> bool:
    if content_type is None or not content_type.strip():
        return True

    normalized_content_type = content_type.split(";", 1)[0].strip().lower()
    return normalized_content_type in SUPPORTED_CONTENT_TYPES


def _is_identity_content_encoding(content_encoding: str | None) -> bool:
    if content_encoding is None or not content_encoding.strip():
        return True

    return content_encoding.strip().lower() == "identity"


def _is_oversized_content_length(content_length: str | None, max_bytes: int) -> bool:
    if content_length is None or not content_length.strip():
        return False

    try:
        return int(content_length) > max_bytes
    except ValueError:
        return False


async def _read_limited_response(
    response: httpx.Response,
    max_bytes: int,
    redirect_count: int,
) -> bytes:
    chunks: list[bytes] = []
    total = 0

    async for chunk in response.aiter_raw():
        total += len(chunk)

        if total > max_bytes:
            raise JobFetchError(
                "Job URL response was too large.",
                reason="response_too_large",
                status_code=response.status_code,
                content_type=response.headers.get("content-type"),
                redirect_count=redirect_count,
            )

        chunks.append(chunk)

    return b"".join(chunks)


async def _close_resources(
    response: httpx.Response | None,
    client: httpx.AsyncClient,
) -> None:
    async def close() -> None:
        try:
            if response is not None:
                await response.aclose()
        finally:
            await client.aclose()

    cleanup_task = asyncio.create_task(close())
    try:
        await asyncio.shield(cleanup_task)
    except asyncio.CancelledError:
        await cleanup_task
        raise


def _remaining_seconds(deadline: float, clock: Clock) -> float:
    remaining = deadline - clock()
    if remaining <= 0:
        raise TimeoutError("Job URL fetch deadline exhausted.")

    return remaining


def _safe_url_host(raw_url: str) -> str | None:
    try:
        return httpx.URL(raw_url).host
    except httpx.InvalidURL:
        return None

# ADR 0001: AI fetcher egress and SSRF defense

- Status: Accepted; application DNS pinning implemented, staging VPC isolation configured in Terraform
- Date: 2026-07-13
- Revised: 2026-09-22

## Context

The AI service accepts a job-posting URL and retrieves it before extracting structured data. The
previous fetcher validated a hostname's DNS answers and then passed the original hostname to
HTTPX. HTTPX resolved that hostname again while opening the connection, so the validated address
and the connected address could differ. That time-of-check/time-of-use gap allowed DNS rebinding.

The fetcher must also remain responsive while resolving DNS, enforce one resource budget across a
redirect chain, avoid decompression bombs, and ensure deployment proxy variables and third-party
HTTP logging cannot expose or alter user-supplied fetches.

An attacker is assumed to control the supplied URL, redirect responses, authoritative DNS answers,
response headers, response compression, and response timing. The attacker may return different
answers to different lookups or a mixture of public and non-public addresses.

## Decision

### URL and address validation

Every initial URL and redirect target is parsed and normalized with HTTPX. Only HTTP on port 80 and
HTTPS on port 443 are accepted, and URL credentials are rejected. The normalized ASCII host is
used for DNS and TLS identity; the normalized authority is used for the HTTP `Host` header. This
preserves IDNA handling and the required bracket syntax for IPv6 authorities.

Hostnames are resolved once per logical hop through the event loop's asynchronous `getaddrinfo`
interface. All A and AAAA results are normalized and validated before a connection is attempted.
The complete target is rejected if the answer set is empty or contains any private, loopback,
link-local, unspecified, multicast, reserved, metadata/control-plane, or otherwise non-public
address. IPv4-mapped IPv6 addresses are classified using their embedded IPv4 address.

The asynchronous interface prevents an OS lookup from blocking the event-loop thread. Cancelling
the await does not guarantee cancellation of an underlying platform resolver call already running
in an executor. A late result is discarded and can never authorize a connection after the fetch
deadline.

### Pinned connection identity

For each attempt, the physical HTTPX request URL contains one address from the already validated
answer set. The transport therefore receives a numeric destination and cannot perform another
hostname lookup for the TCP destination. The logical URL is retained separately for redirects and
the returned fetch result.

The HTTP `Host` authority remains the normalized logical authority. HTTPS requests also carry the
normalized, unbracketed logical host in HTTPX/httpcore's supported `sni_hostname` request extension.
TLS verification remains enabled, so certificate verification and SNI use the original logical
host while the TCP connection uses the pinned address.

A fresh HTTPX client and transport are created for each address attempt. No pool can therefore
reuse a connection across logical authorities that happen to share an address. Later validated
addresses may be attempted only after connection-establishment failures, with at most four address
attempts per answer set. Responses, status codes, protocol/read failures, and body reads are not
retried, and DNS is not repeated within a hop.

### Redirects, proxies, and headers

Redirects remain manual and are limited to five by default. Relative locations are resolved
against the logical URL. Every resulting target receives independent scheme, port, credential,
DNS, and address validation before its own pinned request.

Each request is rebuilt from a fixed header allowlist. Cookies, authorization, and proxy
authorization are never forwarded. Both the client and default transport disable environment
inheritance with `trust_env=False`; `HTTP_PROXY`, `HTTPS_PROXY`, `ALL_PROXY`, and `NO_PROXY` cannot
change the route.

### Deadline and response bounds

`AI_SERVICE_FETCH_TIMEOUT_SECONDS` is one monotonic wall-clock budget for initial DNS, every address
attempt, connect, TLS, response headers, redirects, and body reads. Remaining time is passed to
each HTTPX attempt, and an outer cancellation boundary prevents any phase from resetting the
budget. Responses and clients are closed in shielded `finally` cleanup before timeout or other
errors are propagated; cleanup cannot start another lookup, connection, retry, or body read.

Requests explicitly send `Accept-Encoding: identity`. Redirect bodies are not read, so their
content encoding is ignored. For non-redirect responses, any non-empty content encoding other than
`identity` is rejected before reading the body. The fetcher streams raw bytes and enforces
`AI_SERVICE_MAX_RESPONSE_BYTES`, currently 10,000,000 bytes. `Content-Length` is only an early
rejection hint; streamed and chunked responses are counted independently.

### Observability

Application logs retain sanitized reason categories, upstream status, content type, redirect
count, duration, and request correlation. They do not include full URLs, paths, query strings,
resolved addresses, DNS answer sets, credentials, cookies, authorization values, or tokens.

The `httpx` and `httpcore` logger namespaces are set to `WARNING`. This suppresses routine request
and connection diagnostics that could expose pinned IP URLs or user-controlled paths and queries,
while preserving warning and error diagnostics. Application-owned sanitized diagnostics remain
enabled.

## Infrastructure defense in depth

The staging AI service uses Direct VPC egress with `ALL_TRAFFIC` through the existing IPv4-only
regional subnet. A regional Cloud Router and Public Cloud NAT provide public IPv4 connectivity
with automatic NAT addresses. Only AI carries the `offertrack-stg-ai-egress` network tag.

Three AI-scoped egress rules deny private/non-public IPv4 ranges at priority 900, allow public TCP
80/443 at priority 1000, and deny remaining IPv4 traffic at priority 1100. The private deny includes
the Cloud SQL allocation, RFC1918, CGNAT, link-local, loopback, documentation, benchmarking,
multicast, and reserved ranges. This is independent defense against private/VPC access; Core and
the migration job retain `PRIVATE_RANGES_ONLY`, and Web has no VPC attachment. The AI service
depends on NAT and all three firewall rules before Terraform can create its revision.

Cloud Run platform metadata access is a constraint, not a firewall guarantee. Metadata traffic can
bypass ordinary VPC firewall enforcement; platform DNS remains available through that path.
The application-level metadata/control-plane rejection, complete DNS validation, and pinned-IP
connections remain authoritative for user-supplied fetches. Service identity/IAM are unchanged.
See [Google Cloud firewall limitations](https://docs.cloud.google.com/firewall/docs/firewalls).

The IPv4-only subnet and NAT make IPv6-only external sites unreachable. Dual-stack networking is
not configured. The fetcher still validates every DNS answer; this network compatibility trade-off
does not weaken its application policy.

Terraform validation and contract tests establish intended configuration only. Live enforcement
requires a separately authorized apply and post-apply staging verification.

## Alternatives rejected

- **Validate and then request the hostname normally:** retains the DNS-rebinding TOCTOU gap.
- **Monkey-patch HTTPX/httpcore internals:** creates an unsupported and version-fragile security
  boundary. The implementation uses the supported physical URL and `sni_hostname` interfaces.
- **Share a general-purpose connection pool:** complicates authority and pinned-destination
  isolation. One client/transport per attempt is simpler and auditable.
- **Rely on environment proxy configuration:** permits deployment variables to silently change the
  enforced route.
- **Automatically decompress while counting decoded bytes:** leaves compressed transfer and ratio
  ambiguity. Identity-only raw streaming provides one explicit byte limit.
- **Treat Docker Compose or Terraform presence as proof of egress isolation:** neither alone proves
  the runtime route. Network enforcement remains a separate deployment control.

## Verification

- Public HTTP and HTTPS targets connect to their validated numeric address while retaining logical
  `Host`, SNI, certificate identity, and result URL.
- Every prohibited address class, mixed DNS answer, alternate numeric spelling, unsupported port,
  and URL credential is rejected.
- A simulated rebinding resolver cannot change the address received by the transport.
- Redirects are independently resolved and validated, and do not forward cookies or authorization
  headers.
- Authorities sharing an IP use distinct, closed transports.
- Hostile proxy environment variables cannot affect routing.
- DNS does not block the event loop, and all awaited network phases share the original deadline.
- Declared, streamed, and chunked oversize responses fail; non-identity encodings fail before body
  reads.
- HTTPX/httpcore request logs and application logs do not expose URLs, paths, queries, IPs,
  credentials, or tokens.
- Terraform contracts verify AI-only tags, all-traffic routing, NAT, firewall ordering/ranges,
  Cloud SQL range denial, and unchanged Core/Web/migration networking.

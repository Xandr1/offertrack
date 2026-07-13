# ADR 0001: AI fetcher egress and SSRF defense

- Status: Proposed
- Date: 2026-07-13
- Follow-up: separate application and deployment implementation work after PR #16

## Context

The AI service accepts a job-posting URL and retrieves it before extracting structured data. The
current fetcher rejects non-HTTP schemes, credentials in URLs, non-public literal addresses, DNS
answers that are not public, unsafe redirects, large decoded responses, slow responses, and long
redirect chains. Those checks reduce risk but do not create a complete SSRF boundary.

In particular, resolving a hostname during validation and then allowing `httpx` to resolve the
hostname again while opening the connection does **not** prevent DNS rebinding. The validated
address and the connected address can differ. This is a time-of-check/time-of-use boundary that
cannot be fixed by adding another preflight DNS lookup.

This ADR defines the target architecture. PR #16 does not implement the network architecture or a
custom HTTP transport.

## Threat model

The eventual control must reject or contain:

- Private, loopback, unspecified, link-local, multicast, reserved, and documentation IPv4/IPv6
  targets, including alternative numeric spellings and IPv4-mapped IPv6.
- Cloud metadata and platform control-plane addresses, whether supplied directly or returned by
  DNS.
- DNS answers containing a mixture of public and non-public addresses.
- DNS rebinding and DNS changes between validation and connection.
- Redirects from an allowed public URL to a disallowed address, scheme, or port.
- Unintended proxy selection through `HTTP_PROXY`, `HTTPS_PROXY`, `ALL_PROXY`, or `NO_PROXY`.
- User information in URLs, alternate schemes, and ports other than public HTTP/HTTPS ports.
- Excessive redirects, connection/read stalls, oversized responses, and decompression bombs.
- Connection-pool reuse that crosses target authorities or reuses a connection validated for a
  different destination.
- Leakage of internal API credentials, cookies, authorization headers, or proxy credentials to a
  fetched origin.

An attacker is assumed to control the supplied URL, redirect responses, authoritative DNS answers,
response headers, response compression, and response timing. The attacker may race DNS answers or
return different answers to different resolvers.

## Decision

### Deployment boundary

Production AI-fetcher traffic will use deny-by-default egress. The workload may reach only:

1. Its explicitly configured egress proxy.
2. Trusted DNS resolvers required by the proxy or platform.
3. Explicit internal services and external APIs required by the AI service, preferably through the
   same controlled egress layer.

Direct workload access to public networks, private subnets, link-local ranges, and cloud metadata
will be blocked independently of application validation. Cloud metadata protections supplied by
the deployment platform will also be enabled; a route-level block is not the only metadata
control.

The egress proxy will resolve and connect as one policy operation using trusted DNS. It will reject
the complete DNS result if any A or AAAA answer is non-public, including IPv4-mapped IPv6. It will
pin the selected validated address for the lifetime of that outbound connection and repeat
resolution and policy validation for every redirect target and new connection. DNS answers are not
cached beyond their trusted TTL, and cached decisions never authorize a different address.

Only `http` on port 80 and `https` on port 443 are allowed for fetched job pages. The application
continues to reject URL credentials. Deployment policy may later disable plain HTTP without an API
contract change, but the first implementation retains it for compatibility.

Docker Compose remains a local integration environment. Compose networks and service settings are
not represented as a complete production egress solution.

### HTTPS CONNECT and plain HTTP proxying

For an HTTPS target, the application sends HTTP `CONNECT original-host:443` to the controlled
proxy. Before opening the tunnel, the proxy resolves the original hostname, validates the complete
answer set, selects a validated address, and connects to that address. TLS remains end-to-end
between the application and the target: certificate verification and SNI use the original
hostname, not the resolved IP. The HTTP `Host` authority also remains the original hostname.

For a plain HTTP target, the application sends an absolute-form request through the proxy. The
proxy resolves and validates the destination before making the outbound connection and forwards
the original `Host` value. Plain HTTP has no origin confidentiality or authentication; this is an
accepted compatibility limitation, not equivalent to the HTTPS tunnel.

Proxy authentication is sent only to the proxy. `Proxy-Authorization`, the Core API internal key,
cookies, browser authorization values, and request-specific credentials are never forwarded to
the target origin. Redirect requests are rebuilt from the fixed safe-header allowlist.

### Application behavior

The application will instantiate its fetch client with `trust_env=False` and an explicit proxy
configuration. Absence of the required protected-environment proxy configuration will fail closed.
The proxy is the connection enforcement point; the existing application URL validation remains
defense in depth and continues to run for the initial URL and every redirect.

Connections and pools are isolated by proxy route and original target authority. A connection
validated for one authority cannot be reused for another authority. Both IPv4 and IPv6 are covered
by the same classification rules.

The initial implementation keeps these current functional limits:

- Maximum five redirects.
- Maximum 10 seconds for the fetch operation.
- Maximum 10,000,000 decoded response bytes.

The implementation will additionally cap transferred compressed bytes at 10,000,000, enforce a
maximum 20:1 decoded-to-compressed ratio after the first 64 KiB of decoded content, stream rather
than buffer unbounded data, and stop decompression immediately when a limit is crossed. The
10-second budget is an overall monotonic deadline across DNS, proxy connection, redirects, headers,
and body reads, rather than a fresh allowance for each hop.

Only the currently supported textual content types are accepted. Content length is an early
rejection hint, not a substitute for streaming limits.

### Observability

Security events record a reason category, HTTP status, redirect count, duration, and a generated
request ID. They do not record full URLs, query strings, resolved addresses, cookies, tokens, proxy
credentials, or raw host values when the host is an IP literal. Metrics distinguish validation,
DNS, proxy-policy, timeout, size, and upstream-status failures without identifying the subject.

## Alternatives rejected

- **Pre-resolve and call `httpx` normally:** rejected because the connection performs another DNS
  resolution and remains vulnerable to rebinding.
- **Application-only IP filtering:** useful defense in depth but cannot enforce container egress,
  metadata isolation, or atomic validation and connection.
- **A custom DNS-pinning `httpx` transport in PR #16:** rejected because correct TLS hostname
  verification, SNI, `Host`, redirect validation, pool isolation, and dual-stack behavior require a
  separate security review and extensive tests.
- **Docker Compose-only controls:** rejected as a production claim because the repository contains
  no deployable production network policy.
- **Allowing environment proxy inheritance:** rejected because deployment environment variables
  could silently bypass the selected enforcement path.

## Implementation and migration plan

1. Select and configure a controlled egress proxy with trusted DNS and atomic resolve/connect
   policy; define the production network policy in the deployment repository.
2. Block direct public/private/metadata egress from the AI workload while retaining required proxy,
   DNS, internal-service, and external-API paths.
3. Add explicit protected-environment proxy configuration and `trust_env=False` to the fetcher.
4. Add transfer/decompression/deadline enforcement and safe metrics.
5. Deploy in report-only proxy mode in staging, compare denials with expected public job sites, then
   enable fail-closed enforcement.
6. Roll back application proxy selection and deployment policy together if a production issue is
   found; retain current URL validation throughout rollback.

This work belongs in a separate implementation PR linked to this ADR. Because this repository has
no Kubernetes, Terraform, Helm, or cloud-network manifests, the deployment portion must be tracked
in the actual infrastructure repository rather than approximated here.

## Required verification

- A normal public HTTPS address succeeds.
- Direct private, loopback, link-local, unspecified, reserved, multicast, metadata, and
  IPv4-mapped addresses fail.
- A DNS answer set containing both public and private addresses fails in full.
- A public redirect to a private or metadata target fails before connection.
- Simulated DNS rebinding cannot change the address that receives the connection.
- TLS certificate verification and SNI still use the original hostname through CONNECT.
- Plain HTTP uses proxy absolute-form requests and the original `Host` value.
- IPv4 and IPv6 public targets follow identical policy.
- Environment proxy variables cannot alter or bypass the configured route.
- Alternate schemes, ports, URL credentials, and excessive redirects fail.
- Slow headers/bodies, oversized compressed/decoded bodies, and excessive compression ratios fail
  within the total resource budget.
- Connection-pool reuse cannot cross original target authorities.
- No internal, proxy, cookie, authorization, URL, host-IP, or DNS-answer data appears in logs.

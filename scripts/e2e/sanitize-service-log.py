#!/usr/bin/env python3
"""Redact sensitive values from bounded E2E and container-smoke service logs.

This tool deliberately operates only on plain-text Core API, Next.js, AI, and
restricted Compose status output. Trivy JSON, Playwright reports, and binary
artifacts are outside its scope.
"""

from __future__ import annotations

import ipaddress
import re
import sys
from pathlib import Path


UUID = r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"

REQUEST_ID_UUID = re.compile(
    rf"(?P<prefix>(?:[\"']?(?:request[_-]?id|x-request-id)[\"']?)"
    rf"\s*[:=]\s*[\"']?)(?P<value>{UUID})",
    re.IGNORECASE,
)
CONNECTION_URL = re.compile(
    r"\b(?:jdbc:postgresql|postgres(?:ql)?|redis(?:s)?)://[^\s\"'<>]+",
    re.IGNORECASE,
)
URL_USERINFO = re.compile(
    r"(?P<scheme>\b[a-z][a-z0-9+.-]{1,31}://)[^/@\s\"'<>]+@",
    re.IGNORECASE,
)
SENSITIVE_HEADER = re.compile(
    r"(?P<prefix>(?<![A-Za-z0-9_-])[\"']?(?:authorization|proxy-authorization|cookie|set-cookie|"
    r"x-csrf-token|x-xsrf-token|x-api-key|x-internal-api-key|"
    r"x-ai-service-key|x-ai-service-internal-key)\b[\"']?\s*[:=]\s*)"
    r"(?:\"[^\"\r\n]*\"|'[^'\r\n]*'|[^\r\n}]*)",
    re.IGNORECASE,
)
SENSITIVE_KEY_VALUE = re.compile(
    r"(?P<prefix>[\"']?(?:"
    r"[a-z0-9_.-]*(?:password|passwd|secret|token|api[_-]?key|apikey|csrf|"
    r"client[_-]?id|credential|private[_-]?key)"
    r"[a-z0-9_.-]*|(?:user|session|application|subject)[_-]?id"
    r")[\"']?\s*[:=]\s*)"
    r"(?:\"[^\"\r\n]*\"|'[^'\r\n]*'|[^\s,;}\]]+)",
    re.IGNORECASE,
)
SENSITIVE_QUERY_VALUE = re.compile(
    r"(?P<prefix>[?&](?:access_token|refresh_token|reset_token|token|code|"
    r"api[_-]?key|key|csrf|password|passwd|secret|client[_-]?id|"
    r"client[_-]?secret|username|user|email)\s*=)[^&#\s]+",
    re.IGNORECASE,
)
EMAIL = re.compile(
    r"(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}(?![A-Z0-9.-])",
    re.IGNORECASE,
)
JWT_VALUE = re.compile(
    r"(?<![A-Za-z0-9_-])(?:[A-Za-z0-9_-]{8,}\.){2}[A-Za-z0-9_-]{8,}"
    r"(?![A-Za-z0-9_-])"
)
UUID_VALUE = re.compile(UUID, re.IGNORECASE)
IPV4 = re.compile(r"(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])")
IPV6_CANDIDATE = re.compile(
    r"(?<![0-9A-Za-z])(?:\[[0-9A-Fa-f:.%]+\]|"
    r"(?:[0-9A-Fa-f]{0,4}:){2,}[0-9A-Fa-f:.%]*)(?![0-9A-Za-z])"
)
REQUEST_ID_PLACEHOLDER = "__OFFERTRACK_CORRELATION_ID_{}__"


def _redact_ipv6(match: re.Match[str]) -> str:
    value = match.group(0)
    bracketed = value.startswith("[") and value.endswith("]")
    suffix = ""
    candidate = value[1:-1] if bracketed else value

    if not bracketed:
        stripped = candidate.rstrip(".,;")
        suffix = candidate[len(stripped) :]
        candidate = stripped

    candidate_without_scope = candidate.split("%", maxsplit=1)[0]
    try:
        address = ipaddress.ip_address(candidate_without_scope)
    except ValueError:
        return value

    if address.version != 6:
        return value
    return "[REDACTED_IP]" + suffix


def sanitize(text: str) -> str:
    preserved_request_ids: list[str] = []

    def preserve_request_id(match: re.Match[str]) -> str:
        index = len(preserved_request_ids)
        preserved_request_ids.append(match.group("value"))
        return match.group("prefix") + REQUEST_ID_PLACEHOLDER.format(index)

    text = REQUEST_ID_UUID.sub(preserve_request_id, text)
    text = CONNECTION_URL.sub("[REDACTED_CONNECTION_URL]", text)
    text = URL_USERINFO.sub(r"\g<scheme>[REDACTED_CREDENTIALS]@", text)
    text = SENSITIVE_HEADER.sub(r"\g<prefix>[REDACTED]", text)
    text = SENSITIVE_QUERY_VALUE.sub(r"\g<prefix>[REDACTED]", text)
    text = SENSITIVE_KEY_VALUE.sub(r"\g<prefix>[REDACTED]", text)
    text = JWT_VALUE.sub("[REDACTED_TOKEN]", text)
    text = EMAIL.sub("[REDACTED_EMAIL]", text)
    text = IPV6_CANDIDATE.sub(_redact_ipv6, text)
    text = IPV4.sub("[REDACTED_IP]", text)
    text = UUID_VALUE.sub("[REDACTED_ID]", text)

    for index, request_id in enumerate(preserved_request_ids):
        text = text.replace(REQUEST_ID_PLACEHOLDER.format(index), request_id)
    return text


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: sanitize-service-log.py INPUT OUTPUT", file=sys.stderr)
        return 2

    source = Path(sys.argv[1])
    destination = Path(sys.argv[2])
    sanitized = sanitize(source.read_text(encoding="utf-8", errors="replace"))
    destination.write_text(sanitized, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

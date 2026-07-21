#!/usr/bin/env python3
"""Exercise the bounded CORS, CSRF, SSRF, and persistence smoke contract."""

from __future__ import annotations

import argparse
import http.cookiejar
import json
from pathlib import Path
from typing import Any
from urllib.error import HTTPError
from urllib.request import HTTPCookieProcessor, Request, build_opener


ALLOWED_ORIGIN = "http://127.0.0.1:13001"
DENIED_ORIGIN = "http://127.0.0.1:13002"
EMAIL = "application-user@e2e.invalid"
PASSWORD = "E2e-Test-Password-123!"
USER_ID = "00000000-0000-0000-0000-000000000002"
USER_NAME = "Application Test User"
APPLICATION = {
    "companyName": "Container Smoke Company",
    "positionTitle": "Container Smoke Engineer",
    "jobUrl": "https://example.com/jobs/container-smoke",
    "location": "Warsaw",
    "workMode": "remote",
    "stage": "applied",
    "notes": "container-smoke-persistence",
    "interviews": [],
}


def request(
    opener,
    base_url: str,
    path: str,
    *,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    body: dict[str, Any] | None = None,
):
    data = None if body is None else json.dumps(body).encode("utf-8")
    merged_headers = dict(headers or {})
    if body is not None:
        merged_headers["Content-Type"] = "application/json"
    prepared = Request(
        base_url.rstrip("/") + path,
        data=data,
        headers=merged_headers,
        method=method,
    )
    try:
        response = opener.open(prepared, timeout=10)
        payload = response.read(2 * 1024 * 1024 + 1)
        assert len(payload) <= 2 * 1024 * 1024, f"response too large: {path}"
        return response.status, response.headers, payload
    except HTTPError as error:
        payload = error.read(2 * 1024 * 1024 + 1)
        assert len(payload) <= 2 * 1024 * 1024, f"error response too large: {path}"
        return error.code, error.headers, payload


def json_body(payload: bytes) -> dict[str, Any]:
    decoded = json.loads(payload.decode("utf-8"))
    assert isinstance(decoded, dict)
    return decoded


def assert_cors(opener, base_url: str) -> None:
    preflight_headers = {
        "Origin": ALLOWED_ORIGIN,
        "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "content-type,x-xsrf-token",
    }
    status, headers, _ = request(
        opener,
        base_url,
        "/api/applications",
        method="OPTIONS",
        headers=preflight_headers,
    )
    assert status == 200, f"allowed CORS preflight returned {status}"
    assert headers.get("Access-Control-Allow-Origin") == ALLOWED_ORIGIN
    assert headers.get("Access-Control-Allow-Credentials") == "true"
    allowed_methods = {
        method.strip()
        for method in headers.get("Access-Control-Allow-Methods", "").split(",")
        if method.strip()
    }
    assert allowed_methods == {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}
    allowed_headers = {
        header.strip().lower()
        for header in headers.get("Access-Control-Allow-Headers", "").split(",")
        if header.strip()
    }
    assert allowed_headers == {"content-type", "x-xsrf-token"}

    denied_headers = dict(preflight_headers)
    denied_headers["Origin"] = DENIED_ORIGIN
    status, headers, _ = request(
        opener,
        base_url,
        "/api/applications",
        method="OPTIONS",
        headers=denied_headers,
    )
    assert status == 403, f"denied CORS preflight returned {status}"
    assert headers.get("Access-Control-Allow-Origin") is None
    assert headers.get("Access-Control-Allow-Credentials") is None


def issue_csrf(opener, base_url: str, jar: http.cookiejar.CookieJar):
    status, headers, payload = request(
        opener,
        base_url,
        "/auth/csrf",
        headers={"Origin": ALLOWED_ORIGIN},
    )
    assert status == 200
    assert headers.get("Cache-Control") == "no-store"
    assert headers.get("Access-Control-Allow-Origin") == ALLOWED_ORIGIN
    assert headers.get("Access-Control-Allow-Credentials") == "true"
    body = json_body(payload)
    assert body.get("headerName") == "X-XSRF-TOKEN"
    assert isinstance(body.get("token"), str) and body["token"]
    csrf_cookie = next(
        (cookie for cookie in jar if cookie.name == "XSRF-TOKEN"),
        None,
    )
    assert csrf_cookie is not None
    assert csrf_cookie.path == "/"
    assert csrf_cookie.has_nonstandard_attr("HttpOnly")
    return body["headerName"], body["token"]


def assert_application(body: dict[str, Any], application_id: str) -> None:
    assert body.get("id") == application_id
    for field in (
        "companyName",
        "positionTitle",
        "jobUrl",
        "location",
        "workMode",
        "stage",
        "notes",
    ):
        assert body.get(field) == APPLICATION[field], f"unexpected {field}"


def before_restart(base_url: str, cookie_path: Path, id_path: Path) -> None:
    jar = http.cookiejar.MozillaCookieJar(str(cookie_path))
    opener = build_opener(HTTPCookieProcessor(jar))
    assert_cors(opener, base_url)

    status, _, payload = request(
        opener,
        base_url,
        "/auth/login",
        method="POST",
        body={"email": EMAIL, "password": PASSWORD},
    )
    assert status == 403
    error = json_body(payload)
    assert error.get("status") == 403
    assert error.get("code") == "CSRF_INVALID"
    assert error.get("message") == "CSRF token is missing or invalid."
    assert error.get("path") == "/auth/login"

    initial_header, initial_token = issue_csrf(opener, base_url, jar)
    status, headers, payload = request(
        opener,
        base_url,
        "/auth/login",
        method="POST",
        headers={initial_header: initial_token, "Origin": ALLOWED_ORIGIN},
        body={"email": EMAIL, "password": PASSWORD},
    )
    assert status == 200
    user = json_body(payload).get("user")
    assert user == {"id": USER_ID, "email": EMAIL, "name": USER_NAME}
    access_headers = headers.get_all("Set-Cookie", [])
    access_cookie = next(
        (value for value in access_headers if value.lower().startswith("access_token=")),
        "",
    )
    assert access_cookie
    cookie_attributes = {
        attribute.strip().lower() for attribute in access_cookie.split(";")[1:]
    }
    assert "httponly" in cookie_attributes
    assert "path=/" in cookie_attributes
    assert "samesite=lax" in cookie_attributes
    assert "secure" not in cookie_attributes

    status, _, payload = request(
        opener,
        base_url,
        "/api/applications",
        method="POST",
        headers={initial_header: initial_token, "Origin": ALLOWED_ORIGIN},
        body={},
    )
    assert status == 403
    error = json_body(payload)
    assert error.get("status") == 403
    assert error.get("code") == "CSRF_INVALID"
    assert error.get("message") == "CSRF token is missing or invalid."
    assert error.get("path") == "/api/applications"

    csrf_header, csrf_token = issue_csrf(opener, base_url, jar)
    mutation_headers = {
        csrf_header: csrf_token,
        "Origin": ALLOWED_ORIGIN,
    }

    status, _, payload = request(
        opener,
        base_url,
        "/api/applications/draft",
        method="POST",
        headers=mutation_headers,
        body={"jobUrl": "http://127.0.0.1/"},
    )
    assert status == 400
    error = json_body(payload)
    assert error.get("status") == 400
    assert error.get("code") == "AI_SERVICE_INVALID_URL"
    assert error.get("message") == "Job URL is invalid or unsafe."
    assert error.get("path") == "/api/applications/draft"

    create_header, create_token = issue_csrf(opener, base_url, jar)
    status, _, payload = request(
        opener,
        base_url,
        "/api/applications",
        method="POST",
        headers={create_header: create_token, "Origin": ALLOWED_ORIGIN},
        body=APPLICATION,
    )
    created = json_body(payload)
    assert status == 201, (
        f"application creation returned {status} "
        f"with code {created.get('code', '<none>')}"
    )
    assert created.get("interviews") == []
    application = created.get("application")
    assert isinstance(application, dict)
    application_id = application.get("id")
    assert isinstance(application_id, str) and application_id
    assert_application(application, application_id)

    status, _, payload = request(
        opener, base_url, f"/api/applications/{application_id}"
    )
    assert status == 200
    assert_application(json_body(payload), application_id)

    cookie_path.parent.mkdir(parents=True, exist_ok=True)
    jar.save(ignore_discard=True, ignore_expires=True)
    id_path.write_text(application_id + "\n", encoding="utf-8")
    print("CORS, CSRF, login, SSRF, and create/read checks passed.")


def after_restart(base_url: str, cookie_path: Path, id_path: Path) -> None:
    jar = http.cookiejar.MozillaCookieJar(str(cookie_path))
    jar.load(ignore_discard=True, ignore_expires=True)
    opener = build_opener(HTTPCookieProcessor(jar))
    application_id = id_path.read_text(encoding="utf-8").strip()
    assert application_id

    status, _, payload = request(
        opener, base_url, f"/api/applications/{application_id}"
    )
    assert status == 200
    assert_application(json_body(payload), application_id)
    print("PostgreSQL persistence check passed after Core restart.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("before-restart", "after-restart"))
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--cookie-jar", type=Path, required=True)
    parser.add_argument("--application-id-file", type=Path, required=True)
    args = parser.parse_args()

    if args.phase == "before-restart":
        before_restart(args.base_url, args.cookie_jar, args.application_id_file)
    else:
        after_restart(args.base_url, args.cookie_jar, args.application_id_file)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

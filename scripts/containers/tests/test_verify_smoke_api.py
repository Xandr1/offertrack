from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from email.message import Message
from pathlib import Path
from unittest import mock


REPO_ROOT = Path(__file__).resolve().parents[3]
SCRIPT_PATH = REPO_ROOT / "scripts" / "containers" / "verify-smoke-api.py"
SPEC = importlib.util.spec_from_file_location("verify_smoke_api", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
VERIFY_SMOKE_API = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFY_SMOKE_API)


def response_headers(*cookies: str) -> Message:
    headers = Message()
    for cookie in cookies:
        headers.add_header("Set-Cookie", cookie)
    return headers


def encoded(payload: dict[str, object]) -> bytes:
    return json.dumps(payload).encode("utf-8")


class SmokeRequestFlow:
    APPLICATION_ID = "10000000-0000-0000-0000-000000000001"

    def __init__(self, stale_error_code: str = "CSRF_INVALID") -> None:
        application = dict(VERIFY_SMOKE_API.APPLICATION)
        application["id"] = self.APPLICATION_ID
        self.calls: list[dict[str, object]] = []
        self.responses = [
            (
                "POST",
                "/auth/login",
                403,
                response_headers(),
                {
                    "status": 403,
                    "code": "CSRF_INVALID",
                    "message": "CSRF token is missing or invalid.",
                    "path": "/auth/login",
                },
            ),
            (
                "POST",
                "/auth/login",
                200,
                response_headers(
                    "access_token=synthetic-cookie; HttpOnly; Path=/; SameSite=Lax"
                ),
                {
                    "user": {
                        "id": VERIFY_SMOKE_API.USER_ID,
                        "email": VERIFY_SMOKE_API.EMAIL,
                        "name": VERIFY_SMOKE_API.USER_NAME,
                    }
                },
            ),
            (
                "POST",
                "/api/applications",
                403,
                response_headers(),
                {
                    "status": 403,
                    "code": stale_error_code,
                    "message": "CSRF token is missing or invalid.",
                    "path": "/api/applications",
                },
            ),
            (
                "POST",
                "/api/applications/draft",
                400,
                response_headers(),
                {
                    "status": 400,
                    "code": "AI_SERVICE_INVALID_URL",
                    "message": "Job URL is invalid or unsafe.",
                    "path": "/api/applications/draft",
                },
            ),
            (
                "POST",
                "/api/applications",
                201,
                response_headers(),
                {"interviews": [], "application": application},
            ),
            (
                "GET",
                f"/api/applications/{self.APPLICATION_ID}",
                200,
                response_headers(),
                application,
            ),
        ]

    def __call__(self, opener, base_url: str, path: str, **kwargs):
        index = len(self.calls)
        if index >= len(self.responses):
            raise AssertionError(f"unexpected request: {kwargs.get('method', 'GET')} {path}")
        expected_method, expected_path, status, headers, payload = self.responses[index]
        method = kwargs.get("method", "GET")
        if (method, path) != (expected_method, expected_path):
            raise AssertionError(
                f"request {index}: expected {expected_method} {expected_path}, "
                f"got {method} {path}"
            )
        self.calls.append(
            {
                "opener": opener,
                "base_url": base_url,
                "path": path,
                "method": method,
                "headers": kwargs.get("headers", {}),
                "body": kwargs.get("body"),
            }
        )
        return status, headers, encoded(payload)


class CsrfRotationSmokeTest(unittest.TestCase):
    def run_flow(
        self,
        *,
        initial_token: str = "pre-login-masked-token",
        fresh_token: str = "post-login-masked-token",
        stale_error_code: str = "CSRF_INVALID",
    ) -> tuple[SmokeRequestFlow, list[int]]:
        flow = SmokeRequestFlow(stale_error_code)
        csrf_call_positions: list[int] = []
        csrf_values = iter(
            [
                ("X-XSRF-TOKEN", initial_token),
                ("X-XSRF-TOKEN", fresh_token),
            ]
        )

        def issue_csrf(_opener, _base_url):
            csrf_call_positions.append(len(flow.calls))
            return next(csrf_values)

        with tempfile.TemporaryDirectory() as temporary_directory:
            temporary_root = Path(temporary_directory)
            with (
                mock.patch.object(VERIFY_SMOKE_API, "assert_cors"),
                mock.patch.object(VERIFY_SMOKE_API, "request", side_effect=flow),
                mock.patch.object(
                    VERIFY_SMOKE_API, "issue_csrf", side_effect=issue_csrf
                ),
            ):
                VERIFY_SMOKE_API.before_restart(
                    "http://127.0.0.1:18081",
                    temporary_root / "cookies.txt",
                    temporary_root / "application-id.txt",
                )

        return flow, csrf_call_positions

    def test_stale_token_is_rejected_before_fresh_token_mutations(self) -> None:
        flow, csrf_call_positions = self.run_flow()

        self.assertEqual([1, 3], csrf_call_positions)
        stale_probe = flow.calls[2]
        self.assertEqual("/api/applications", stale_probe["path"])
        self.assertEqual({}, stale_probe["body"])
        self.assertEqual(
            "pre-login-masked-token",
            stale_probe["headers"]["X-XSRF-TOKEN"],
        )
        self.assertIs(flow.calls[1]["opener"], stale_probe["opener"])

        for fresh_mutation in flow.calls[3:5]:
            self.assertEqual(
                "post-login-masked-token",
                fresh_mutation["headers"]["X-XSRF-TOKEN"],
            )

    def test_masked_token_strings_are_not_used_as_rotation_proof(self) -> None:
        flow, csrf_call_positions = self.run_flow(
            initial_token="same-masked-value",
            fresh_token="same-masked-value",
        )

        self.assertEqual([1, 3], csrf_call_positions)
        self.assertEqual(6, len(flow.calls))

    def test_stale_probe_requires_exact_csrf_error_code(self) -> None:
        with self.assertRaises(AssertionError):
            self.run_flow(stale_error_code="UNEXPECTED_ERROR")


if __name__ == "__main__":
    unittest.main()

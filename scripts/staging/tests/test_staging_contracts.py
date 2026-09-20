from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]


def read(relative_path: str) -> str:
    return (REPO_ROOT / relative_path).read_text(encoding="utf-8")


def resource_block(terraform: str, resource_type: str, name: str, keyword: str = "resource") -> str:
    declaration = (
        rf'{keyword} "{re.escape(resource_type)}" "{re.escape(name)}"'
        if keyword == "resource"
        else rf'{keyword} "{re.escape(resource_type)}"'
    )
    start_match = re.search(
        rf'^{declaration} \{{$',
        terraform,
        flags=re.MULTILINE,
    )
    if start_match is None:
        raise AssertionError(f"Terraform {keyword} {resource_type}.{name} is missing")

    depth = 0
    start = start_match.start()
    for index in range(start_match.end() - 1, len(terraform)):
        if terraform[index] == "{":
            depth += 1
        elif terraform[index] == "}":
            depth -= 1
            if depth == 0:
                return terraform[start : index + 1]
    raise AssertionError(f"Terraform resource {resource_type}.{name} is unbalanced")


def assignment_block(terraform: str, name: str) -> str:
    start_match = re.search(rf"^  {re.escape(name)} = \{{$", terraform, flags=re.MULTILINE)
    if start_match is None:
        raise AssertionError(f"Terraform local {name} is missing")

    depth = 0
    start = start_match.start()
    for index in range(start_match.end() - 1, len(terraform)):
        if terraform[index] == "{":
            depth += 1
        elif terraform[index] == "}":
            depth -= 1
            if depth == 0:
                return terraform[start : index + 1]
    raise AssertionError(f"Terraform local {name} is unbalanced")


class CloudRunContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.cloud_run = read("infra/terraform/staging/cloud-run.tf")
        cls.iam = read("infra/terraform/staging/iam.tf")

    def test_exact_topology_and_immutable_seed_images(self) -> None:
        for name in (
            "offertrack-stg-ai",
            "offertrack-stg-core",
            "offertrack-stg-migrate",
            "offertrack-stg-web",
        ):
            self.assertIn(f'"{name}"', self.cloud_run)

        self.assertNotRegex(self.cloud_run, r"(?i)(?:image\s*=|version\s*=)\s*\"latest\"")
        self.assertEqual(3, self.cloud_run.count('resource "google_cloud_run_v2_service"'))
        self.assertEqual(1, self.cloud_run.count('resource "google_cloud_run_v2_job"'))
        self.assertEqual(4, self.cloud_run.count("ignore_changes = ["))

    def test_core_and_migration_use_private_only_direct_vpc(self) -> None:
        core = resource_block(self.cloud_run, "google_cloud_run_v2_service", "core")
        migration = resource_block(self.cloud_run, "google_cloud_run_v2_job", "migrate")
        ai = resource_block(self.cloud_run, "google_cloud_run_v2_service", "ai")
        web = resource_block(self.cloud_run, "google_cloud_run_v2_service", "web")

        self.assertIn('egress = "PRIVATE_RANGES_ONLY"', core)
        self.assertIn('egress = "PRIVATE_RANGES_ONLY"', migration)
        self.assertNotIn("vpc_access", ai)
        self.assertNotIn("vpc_access", web)
        self.assertIn("invoker_iam_disabled = true", core)
        self.assertIn("invoker_iam_disabled = true", web)
        self.assertNotIn("invoker_iam_disabled", ai)

    def test_migration_job_has_only_database_runtime_configuration(self) -> None:
        migration = resource_block(self.cloud_run, "google_cloud_run_v2_job", "migrate")
        migration_env = re.search(
            r"migration_environment = \{(.*?)\n  \}", self.cloud_run, re.DOTALL
        )
        self.assertIsNotNone(migration_env)
        environment = migration_env.group(1) if migration_env else ""
        for required in (
            "DATABASE_URL",
            "DB_USER",
            "OFFERTRACK_RUN_MODE",
            "SPRING_PROFILES_ACTIVE",
        ):
            self.assertIn(required, environment)
        for forbidden in ("REDIS", "SMTP", "JWT", "OAUTH", "AI_SERVICE"):
            self.assertNotIn(forbidden, environment)
            self.assertNotIn(forbidden, migration)
        self.assertIn('name = "DB_PASSWORD"', migration)
        self.assertIn("max_retries           = 0", migration)

    def test_postgres_runtime_has_no_managed_cache_dependency(self) -> None:
        terraform = "\n".join(
            path.read_text(encoding="utf-8")
            for path in (REPO_ROOT / "infra/terraform/staging").glob("*.tf")
        )
        self.assertNotRegex(terraform.lower(), r"redis|memorystore|ai_draft_cache")
        self.assertIn("sqladmin.googleapis.com", read("infra/terraform/staging/apis.tf"))
        self.assertRegex(read("infra/terraform/staging/apis.tf"), r"disable_on_destroy\s*=\s*false")
        self.assertIn('secret  = "rate_limit_key"', self.cloud_run)
        self.assertIn("RATE_LIMIT_KEY_SECRET", self.cloud_run)
        self.assertRegex(self.cloud_run, r'RATE_LIMIT_FAIL_OPEN\s*=\s*"false"')

    def test_cloud_sql_cost_default_preserves_database_safeguards(self) -> None:
        variables = read("infra/terraform/staging/variables.tf")
        tier = resource_block(variables, "cloud_sql_tier", "cloud_sql_tier", keyword="variable")
        disk = resource_block(variables, "cloud_sql_disk_size_gb", "cloud_sql_disk_size_gb", keyword="variable")
        self.assertRegex(tier, r'default\s*=\s*"db-f1-micro"')
        self.assertRegex(disk, r"default\s*=\s*10\b")
        database = read("infra/terraform/staging/cloud-sql.tf")
        for name, value in {
            "database_version": '"POSTGRES_16"',
            "edition": '"ENTERPRISE"',
            "availability_type": '"ZONAL"',
            "disk_type": '"PD_SSD"',
            "tier": "var.cloud_sql_tier",
            "disk_size": "var.cloud_sql_disk_size_gb",
            "deletion_protection": "true",
            "deletion_protection_enabled": "true",
            "point_in_time_recovery_enabled": "true",
            "ipv4_enabled": "false",
            "private_network": "google_compute_network.staging.id",
        }.items():
            self.assertRegex(database, rf"(?m)^\s*{name}\s*=\s*{re.escape(value)}\s*$")
        self.assertRegex(database, r"backup_configuration\s*\{\s*enabled\s*=\s*true")
        self.assertIn("google_service_networking_connection.private_services", database)

    def test_ai_http_health_probes_use_the_canonical_trusted_host(self) -> None:
        ai = resource_block(self.cloud_run, "google_cloud_run_v2_service", "ai")

        for probe_name in ("startup_probe", "liveness_probe"):
            with self.subTest(probe=probe_name):
                probe = re.search(
                    rf"(?ms)^      {probe_name} \{{(.*?)^      \}}", ai
                )
                self.assertIsNotNone(probe)
                probe_contents = probe.group(1) if probe else ""
                self.assertIn('path = "/health"', probe_contents)
                self.assertIn("port = 8080", probe_contents)
                self.assertIn(
                    'http_headers {\n            name  = "Host"\n'
                    "            value = local.ai_service_host\n          }",
                    probe_contents,
                )
        self.assertNotIn("readiness_probe", ai)

    def test_application_urls_and_oauth_configuration_remain_canonical(self) -> None:
        expected_hosts = {
            "ai": '${local.cloud_run_names.ai}-${var.project_number}.${var.region}.run.app',
            "core": 'api.staging.${var.staging_base_domain}',
            "web": 'staging.${var.staging_base_domain}',
        }
        for service, host in expected_hosts.items():
            self.assertIn(f'{service}_service_host = "{host}"', " ".join(self.cloud_run.split()))
            self.assertRegex(self.cloud_run, rf'{service}_service_url\\s*=\\s*"https://\\$\\{{local.{service}_service_host\\}}"')

        core_environment = assignment_block(self.cloud_run, "core_environment")
        for name, url in (
            ("AI_SERVICE_BASE_URL", "ai"),
            ("AI_SERVICE_AUDIENCE", "ai"),
            ("APP_WEB_URL", "web"),
            ("CORS_ALLOWED_ORIGINS", "web"),
        ):
            with self.subTest(environment=name):
                self.assertRegex(
                    core_environment,
                    rf"(?m)^\s*{name}\s*=\s*local.{url}_service_url$",
                )

        self.assertIn(
            "https://api.staging.<domain>"
            "/login/oauth2/code/google",
            read("docs/gcp-staging-infrastructure.md"),
        )

    def test_deployer_permissions_are_resource_scoped_and_explicit(self) -> None:
        ai_invoker = resource_block(
            self.iam, "google_cloud_run_v2_service_iam_member", "ai_invoker"
        )
        service_developer = resource_block(
            self.iam,
            "google_cloud_run_v2_service_iam_member",
            "deployer_service_developer",
        )
        job_developer = resource_block(
            self.iam, "google_cloud_run_v2_job_iam_member", "deployer_job_developer"
        )
        job_executor = resource_block(
            self.iam, "google_cloud_run_v2_job_iam_member", "deployer_job_executor"
        )
        repository_writer = resource_block(
            self.iam,
            "google_artifact_registry_repository_iam_member",
            "deployer_writer",
        )
        service_account_grants = assignment_block(
            self.iam, "runtime_service_account_user_grants"
        )

        self.assertIn('role     = "roles/run.invoker"', ai_invoker)
        self.assertIn('toset(["core", "deployer"])', ai_invoker)
        self.assertNotIn('member   = "allUsers"', self.iam)
        self.assertIn('role     = "roles/run.developer"', service_developer)
        self.assertIn('google_service_account.staging["deployer"].email', service_developer)
        self.assertEqual(3, service_developer.count("google_cloud_run_v2_service."))
        self.assertIn('role     = "roles/run.developer"', job_developer)
        self.assertIn('google_service_account.staging["deployer"].email', job_developer)
        self.assertIn('role     = "roles/run.jobsExecutor"', job_executor)
        self.assertIn('google_service_account.staging["deployer"].email', job_executor)
        self.assertIn('role       = "roles/artifactregistry.writer"', repository_writer)
        self.assertIn('google_service_account.staging["deployer"].email', repository_writer)

        deployer_runtimes = set(
            re.findall(
                r'deployer_[a-z]+ = \{\s+actor\s+= "deployer"\s+runtime = "([a-z]+)"',
                service_account_grants,
            )
        )
        self.assertEqual({"ai", "core", "migrator", "web"}, deployer_runtimes)

    def test_runtime_secret_access_is_exact_per_identity(self) -> None:
        grants = assignment_block(self.iam, "secret_access_grants")
        runtime_access = resource_block(
            self.iam, "google_secret_manager_secret_iam_member", "runtime_access"
        )
        parsed = re.findall(
            r'[a-z0-9_]+ = \{\s+service_account = "([a-z]+)"\s+secret\s+= "([a-z0-9_]+)"',
            grants,
        )
        by_identity: dict[str, set[str]] = {}
        for identity, secret in parsed:
            by_identity.setdefault(identity, set()).add(secret)

        self.assertEqual(
            {
                "ai_internal_key",
                "db_app_password",
                "dependency_health_key",
                "google_client_secret",
                "jwt_secret",
                "oauth_cookie_secret",
                "rate_limit_key",
                "smtp_password",
            },
            by_identity["core"],
        )
        self.assertEqual({"ai_internal_key", "openai_api_key"}, by_identity["ai"])
        self.assertEqual({"db_migrator_password"}, by_identity["migrator"])
        self.assertEqual({"dependency_health_key"}, by_identity["deployer"])
        self.assertNotIn("ai_internal_key", by_identity["deployer"])
        self.assertNotIn("web", by_identity)
        self.assertIn("for_each = local.secret_access_grants", runtime_access)
        self.assertIn('role      = "roles/secretmanager.secretAccessor"', runtime_access)
        self.assertEqual(1, self.iam.count('"roles/secretmanager.secretAccessor"'))

        secrets = read("infra/terraform/staging/secrets.tf")
        self.assertIn('dependency_health_key = "offertrack-stg-dependency-health-key"', secrets)

    def test_nullable_runtime_strings_have_empty_fallbacks(self) -> None:
        environment = assignment_block(self.cloud_run, "core_environment")
        inputs = {
            "GOOGLE_CLIENT_ID": (
                "google_oauth_client_id",
                "client.apps.googleusercontent.com",
            ),
            "MAIL_FROM": ("mail_from", "mail@example.test"),
            "SMTP_HOST": ("smtp_host", "smtp.example.test"),
            "SMTP_USERNAME": ("smtp_username", "mailer"),
        }

        expressions = {}
        for name in inputs:
            match = re.search(
                rf"(?ms)^\s*{name}\s*=\s*(.*?)(?=^\s*[A-Z][A-Z_0-9]*\s*=|^  \}})",
                environment,
            )
            if match is None:
                self.fail(f"Missing runtime environment value: {name}")
            expressions[name] = "${" + match.group(1).strip() + "}"

        with tempfile.TemporaryDirectory() as directory:
            fixture = Path(directory)
            (fixture / "main.tf.json").write_text(
                json.dumps(
                    {
                        "variable": {
                            variable: {"type": "string"} for variable, _ in inputs.values()
                        },
                        "locals": {"environment": expressions},
                    }
                ),
                encoding="utf-8",
            )

            for scenario in ("null", "empty", "padded"):
                with self.subTest(scenario=scenario):
                    values = {
                        variable: (
                            None
                            if scenario == "null"
                            else ""
                            if scenario == "empty"
                            else f" \t{value}\n "
                        )
                        for variable, value in inputs.values()
                    }

                    (fixture / "terraform.tfvars.json").write_text(
                        json.dumps(values),
                        encoding="utf-8",
                    )

                    result = subprocess.run(
                        ["terraform", "console", "-no-color"],
                        input="jsonencode(local.environment)\n",
                        cwd=fixture,
                        capture_output=True,
                        text=True,
                        timeout=30,
                    )

                    self.assertEqual(
                        0,
                        result.returncode,
                        f"terraform console failed:\n{result.stderr}",
                    )

                    expected = {
                        name: value if scenario == "padded" else ""
                        for name, (_, value) in inputs.items()
                    }
                    actual = json.loads(json.loads(result.stdout))

                    self.assertEqual(expected, actual)

    def test_runtime_inputs_are_nullable_only_for_foundation(self) -> None:
        variables = read("infra/terraform/staging/variables.tf")
        for name in ("google_oauth_client_id", "smtp_host", "smtp_port", "smtp_username", "mail_from"):
            block = resource_block(variables, name, name, keyword="variable")
            self.assertIn("default     = null", block)
            self.assertIn("nullable    = true", block)
            self.assertIn("!var.enable_cloud_run_runtime", block)

        secret_versions = resource_block(
            variables, "secret_versions", "secret_versions", keyword="variable"
        )
        self.assertIn("dependency_health_key", secret_versions)
        self.assertIn("default  = null", secret_versions)
        self.assertIn("nullable = true", secret_versions)
        self.assertIn("!var.enable_cloud_run_runtime || var.secret_versions != null", secret_versions)
        self.assertNotRegex(secret_versions, r'=\s*"1"')

class DeploymentWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = read(".github/workflows/deploy-staging.yml")
        cls.steps = re.findall(
            r"(?ms)^      - name: (.*?)(?=^      - name: |\Z)", cls.workflow
        )

    def step(self, name: str) -> str:
        return next(step for step in self.steps if step.splitlines()[0] == name)

    def test_ai_id_tokens_are_fresh_and_do_not_replace_gcloud_credentials(self) -> None:
        original_auth = self.step("Authenticate to Google Cloud with WIF")
        auth_pin = re.search(r"uses: (google-github-actions/auth@[0-9a-f]{40})", original_auth)
        self.assertIsNotNone(auth_pin)
        expected_inputs = {
            "workload_identity_provider": (
                "projects/765846644391/locations/global/"
                "workloadIdentityPools/offertrack-github/providers/github-actions"
            ),
            "service_account": (
                "offertrack-stg-deployer@offertrack-staging.iam.gserviceaccount.com"
            ),
            "token_format": "id_token",
            "id_token_audience": (
                "https://offertrack-stg-ai-765846644391.europe-central2.run.app"
            ),
            "id_token_include_email": "true",
            "create_credentials_file": "false",
            "export_environment_variables": "false",
        }
        self.assertNotIn("token_format:", original_auth)
        self.assertNotIn("create_credentials_file: false", original_auth)
        self.assertNotIn("export_environment_variables: false", original_auth)
        self.assertEqual(2, self.workflow.count("token_format: id_token"))

        for consumer_name, token_id in (
            ("Verify and promote the AI candidate revision", "ai_verification_token"),
            ("Run bounded staging smoke checks", "ai_smoke_token"),
        ):
            with self.subTest(consumer=consumer_name):
                consumer = self.step(consumer_name)
                mint = self.steps[self.steps.index(consumer) - 1]
                self.assertIn(f"uses: {auth_pin.group(1)}", mint)
                self.assertRegex(mint, rf"(?m)^        id: {token_id}$")
                for key, value in expected_inputs.items():
                    self.assertRegex(mint, rf"(?m)^          {key}: {re.escape(value)}$")
                self.assertIn(
                    "        env:\n"
                    f"          AI_ID_TOKEN: ${{{{ steps.{token_id}.outputs.id_token }}}}",
                    consumer,
                )
                self.assertEqual(1, self.workflow.count(f"steps.{token_id}.outputs.id_token"))
                self.assertNotRegex(mint + consumer, r"(?m)^        (?:if|continue-on-error):")

    def test_ai_tokens_stay_in_step_environment_and_curl_standard_input(self) -> None:
        smoke = read("scripts/staging/smoke.sh")
        verification = self.step("Verify and promote the AI candidate revision")
        for source in (self.workflow, smoke):
            self.assertNotIn("gcloud auth print-identity-token", source)
            self.assertNotRegex(source, r"(?m)^.*(?:AI_ID_TOKEN|id_token).*GITHUB_(?:ENV|OUTPUT)")
            self.assertNotRegex(source, r"(?:echo|printf)\s+[^\n]*(?:AI_ID_TOKEN|id_token)")
            self.assertNotRegex(
                source,
                r"--(?:header|ai-id-token|id-token)\s+[\"']?[^\n<]*\$\{?AI_ID_TOKEN",
            )
        for source, expected_headers in ((verification, 2), (smoke, 1)):
            self.assertIn("set +x", source)
            self.assertIn("unset AI_ID_TOKEN", source)
            self.assertEqual(expected_headers, source.count("--header @-"))
            self.assertEqual(
                expected_headers,
                source.count('<<< "Authorization: Bearer $AI_ID_TOKEN"'),
            )
        self.assertIn('"$candidate_url/health"', verification)
        self.assertIn('"$canonical_url/health"', verification)
        self.assertNotIn("id_token_audience:", verification)
        self.assertNotIn(
            "roles/iam.serviceAccountTokenCreator", read("infra/terraform/staging/iam.tf")
        )

    def test_deployment_is_manual_only_and_restricted_to_main(self) -> None:
        trigger = self.workflow[
            self.workflow.index("\non:\n") + 1 : self.workflow.index("\nconcurrency:")
        ]
        self.assertIn("workflow_dispatch:", trigger)
        self.assertNotIn("workflow_run:", trigger)
        self.assertNotRegex(trigger, r"(?m)^  (?!workflow_dispatch:)[a-z_]+:")
        self.assertIn("github.ref == 'refs/heads/main'", self.workflow)
        self.assertIn("vars.STAGING_DEPLOY_ENABLED == 'true'", self.workflow)
        self.assertNotRegex(self.workflow, r"(?m)^  STAGING_DEPLOY_ENABLED:")
        self.assertIn("ref: ${{ github.sha }}", self.workflow)
        self.assertNotIn("github.event.workflow_run", self.workflow)
        self.assertIn("workload_identity_provider:", self.workflow)
        self.assertNotIn("credentials_json:", self.workflow)
        self.assertNotIn("secrets.", self.workflow)

    def test_exact_selected_commit_requires_a_successful_ci_push_run(self) -> None:
        verification_start = self.workflow.index("Verify successful CI push run")
        verification_end = self.workflow.index("      - name: Setup Node")
        verification = self.workflow[verification_start:verification_end]
        self.assertIn("      actions: read", self.workflow)
        self.assertIn("GITHUB_TOKEN: ${{ github.token }}", verification)
        self.assertIn("APPROVED_SHA: ${{ github.sha }}", verification)
        self.assertIn("set -euo pipefail", verification)
        self.assertIn("curl --fail", verification)
        self.assertIn(
            "/actions/workflows/ci.yml/runs?branch=main&event=push&status=success"
            "&head_sha=${APPROVED_SHA}",
            verification,
        )
        self.assertIn("jq -e '.total_count > 0'", verification)
        self.assertLess(
            self.workflow.index("Verify successful CI push run for the selected commit"),
            self.workflow.index("Authenticate to Google Cloud with WIF"),
        )

    def test_dependency_health_key_is_scoped_and_uses_the_core_revision_pin(self) -> None:
        self.assertIn('select(.name == "DEPENDENCY_HEALTH_KEY")', self.workflow)
        self.assertIn("offertrack-stg-dependency-health-key", self.workflow)
        self.assertIn('gcloud secrets versions access "$health_secret_version"', self.workflow)
        self.assertIn("X-Dependency-Health-Key", self.workflow)
        self.assertNotIn("offertrack-stg-ai-internal-key", self.workflow)
        self.assertNotIn("gcloud secrets versions access 1", self.workflow)

        github_env_lines = [
            line for line in self.workflow.splitlines() if "$GITHUB_ENV" in line
        ]
        self.assertFalse(
            any("HEALTH" in line or "health_key" in line for line in github_env_lines)
        )

    def test_rollout_order_blocks_on_ai_and_migration(self) -> None:
        ordered_markers = (
            "Build production Core and AI images",
            "Deploy the AI candidate revision",
            "Verify and promote the AI candidate revision",
            "Update and execute the migration job",
            "Deploy and verify the Core candidate revision",
            "Build the Web image with the canonical Core URL",
            "Deploy and verify the Web candidate revision",
            "Run bounded staging smoke checks",
        )
        indexes = [self.workflow.index(marker) for marker in ordered_markers]
        self.assertEqual(indexes, sorted(indexes))
        self.assertEqual(3, self.workflow.count("--no-traffic"))
        self.assertEqual(3, self.workflow.count("--to-latest --clear-tags"))
        self.assertIn('gcloud run jobs execute "$MIGRATION_JOB"', self.workflow)
        self.assertIn("--wait", self.workflow)
        self.assertNotIn("continue-on-error:", self.workflow)
        for name in ordered_markers[1:]:
            self.assertIn("set -euo pipefail", self.step(name))
            self.assertNotRegex(self.step(name), r"(?m)^        if:")

    def test_rollout_http_checks_have_bounded_propagation_retry(self) -> None:
        retry_policy = {
            "--connect-timeout 5": None,
            "--max-time 20": None,
            "--retry 4": None,
            "--retry-all-errors": None,
            "--retry-delay 2": None,
        }

        steps = (
            ("Verify and promote the AI candidate revision", 2),
            ("Deploy and verify the Core candidate revision", 3),
            ("Deploy and verify the Web candidate revision", 1),
        )

        for step_name, expected_checks in steps:
            with self.subTest(step=step_name):
                step = self.step(step_name)

                self.assertEqual(expected_checks, step.count("curl --fail"))
                self.assertNotIn("--retry-max-time", step)

                for option in retry_policy:
                    self.assertEqual(
                        expected_checks,
                        step.count(option),
                        f"{step_name} must apply '{option}' to every rollout HTTP check",
                    )

    def test_rollout_uses_canonical_urls_without_service_uri_equality(self) -> None:
        self.assertIn('PROJECT_NUMBER: "765846644391"', self.workflow)
        steps = (
            ("AI", "Verify and promote the AI", "Update and execute the migration job", "/health"),
            (
                "CORE", "Deploy and verify the Core", "Build the Web image with the canonical Core URL",
                "/actuator/health/readiness",
            ),
            ("WEB", "Deploy and verify the Web", "Run bounded staging smoke checks", "/login"),
        )
        for service, label, next_step, health_path in steps:
            with self.subTest(service=service):
                start = self.workflow.index(
                    f"{label} candidate revision"
                )
                step = self.workflow[start : self.workflow.index(next_step, start)]
                if service == "AI":
                    self.assertIn('canonical_url="https://${AI_SERVICE}-${PROJECT_NUMBER}.${REGION}.run.app"', step)
                    self.assertIn('candidate_url="https://candidate---${AI_SERVICE}-${PROJECT_NUMBER}.${REGION}.run.app"', step)
                else:
                    self.assertIn(f'canonical_url="${service}_PUBLIC_URL"', step)
                    self.assertIn(f'candidate_url="${service}_PUBLIC_URL"', step)
                    self.assertIn('--header "X-OfferTrack-Route: candidate"', step)
                self.assertIn('[[ "$ready_revision" == "$expected_revision" ]]', step)
                self.assertIn(f'"$candidate_url{health_path}"', step)
                self.assertIn(f'"{service}_URL=$canonical_url"', step)
                for line in step.splitlines():
                    if re.search(r"\$(?:canonical_url|\{canonical_url\})", line):
                        self.assertNotRegex(line, r"\s(?:==|=|!=)\s")
                self.assertLess(
                    step.index("--to-latest --clear-tags"),
                    step.index(f'"{service}_URL=$canonical_url"'),
                )

        ai_step = self.workflow[
            self.workflow.index("Verify and promote the AI candidate revision") :
            self.workflow.index("Update and execute the migration job")
        ]
        self.assertLess(
            ai_step.index('"$candidate_url/health"'),
            ai_step.index("--to-latest --clear-tags"),
        )
        self.assertLess(
            ai_step.index('jq -e \'.status == "ok"\''),
            ai_step.index("--to-latest --clear-tags"),
        )
        self.assertLess(
            ai_step.index("--to-latest --clear-tags"),
            ai_step.index('"$canonical_url/health"'),
        )
        self.assertRegex(
            ai_step,
            r'"\$canonical_url/health"[^\n]*\s+jq -e \'\.status == "ok"\'',
        )

        core_step = self.workflow[
            self.workflow.index("Deploy and verify the Core candidate revision") :
            self.workflow.index("Build the Web image with the canonical Core URL")
        ]
        self.assertLess(
            core_step.index("--to-latest --clear-tags"),
            core_step.index('"$canonical_url/actuator/health/readiness"'),
        )
        self.assertRegex(
            core_step,
            r'"\$canonical_url/actuator/health/readiness"\s+'
            r'jq -e \'\.status == "UP"\'',
        )
        self.assertIn('"$candidate_url/actuator/health/dependencies"', core_step)
        self.assertIn('--next-public-api-url "$CORE_URL"', self.workflow)

        smoke = read("scripts/staging/smoke.sh")
        for service, path in (
            ("AI", "/health"),
            ("CORE", "/actuator/health/readiness"),
            ("WEB", "/login"),
        ):
            with self.subTest(smoke=service):
                self.assertIn(f'--{service.lower()}-url "${service}_URL"', self.workflow)
                self.assertIn(f'"${service}_URL{path}"', smoke)

    def test_images_are_commit_tagged_and_deployed_by_digest(self) -> None:
        self.assertIn('offertrack/${component}:$DEPLOY_SHA', self.workflow)
        self.assertIn('${registry}/web:$DEPLOY_SHA', self.workflow)
        self.assertIn("image_summary.fully_qualified_digest", self.workflow)
        self.assertNotRegex(self.workflow, r"(?i)/(?:web|core-api|ai-service):latest")
        self.assertIn("--next-public-api-url \"$CORE_URL\"", self.workflow)
        self.assertIn("image_summary.fully_qualified_digest", self.workflow)
        self.assertIn("X-Dependency-Health-Key", self.workflow)


class SmokeTokenContractTest(unittest.TestCase):
    def test_smoke_preserves_health_and_immutable_identity_checks(self) -> None:
        smoke = read("scripts/staging/smoke.sh")
        self.assertIn("set -euo pipefail", smoke)
        self.assertIn("trap cleanup EXIT", smoke)
        for service, path, expected_status in (
            ("AI", "/health", "ok"),
            ("CORE", "/actuator/health/liveness", "UP"),
            ("CORE", "/actuator/health/readiness", "UP"),
            ("CORE", "/actuator/health/dependencies", "UP"),
        ):
            start = smoke.index(f'retry_curl "${service}_URL{path}"')
            self.assertIn(f'jq -e \'.status == "{expected_status}"\'', smoke[start:].split("\n\n")[0])
        self.assertIn('retry_curl "$WEB_URL/login"', smoke)
        for service in ("AI", "CORE", "WEB"):
            self.assertIn(
                f'check_service_revision offertrack-stg-{service.lower()} "${service}_REVISION" "${service}_IMAGE"',
                smoke,
            )
        self.assertIn('[[ "$ready_revision" == "$expected_revision" ]]', smoke)
        self.assertIn('[[ "$actual_image" == "$expected_image" ]]', smoke)
        self.assertIn("gcloud run jobs describe offertrack-stg-migrate", smoke)
        self.assertIn('[[ "$job_image" == "$CORE_IMAGE" ]]', smoke)

    def test_missing_or_blank_token_fails_before_requests_and_valid_token_uses_stdin(self) -> None:
        git_bash = Path("C:/Program Files/Git/bin/bash.exe")
        bash = str(git_bash) if os.name == "nt" and git_bash.is_file() else shutil.which("bash")
        self.assertIsNotNone(bash, "Bash is required for staging smoke contracts")
        commit = "a" * 40
        args = ["scripts/staging/smoke.sh", "--commit", commit]
        for service, component in (("ai", "ai-service"), ("core", "core-api"), ("web", "web")):
            args.extend([
                f"--{service}-url",
                ({"ai": "https://offertrack-stg-ai-765846644391.europe-central2.run.app",
                  "core": "https://api.staging.example.com", "web": "https://staging.example.com"}[service]),
                f"--{service}-image",
                f"europe-central2-docker.pkg.dev/offertrack-staging/offertrack/{component}@sha256:{'b' * 64}",
                f"--{service}-revision",
                f"offertrack-stg-{service}-g{commit[:12]}-123-1",
            ])
        wrapper = textwrap.dedent("""\
            curl() {
              printf '%s\\n' "$@" > "$CALL_LOG"
              cat > "$HEADER_LOG"
              return 42
            }
            gcloud() { echo 'unexpected gcloud call' >&2; return 99; }
            jq() { echo 'unexpected jq call' >&2; return 99; }
            export -f curl gcloud jq
            bash -x "$@"
            """)
        sentinel = "offline-test-ai-token"
        for token in (None, "", " \t\n ", sentinel):
            with self.subTest(token="valid" if token == sentinel else repr(token)):
                with tempfile.TemporaryDirectory() as directory:
                    call_log = Path(directory) / "curl-arguments.txt"
                    header_log = Path(directory) / "curl-stdin.txt"
                    environment = dict(os.environ)
                    environment.pop("AI_ID_TOKEN", None)
                    if token is not None:
                        environment["AI_ID_TOKEN"] = token
                    environment["CALL_LOG"] = call_log.as_posix()
                    environment["HEADER_LOG"] = header_log.as_posix()
                    result = subprocess.run(
                        [bash, "-c", wrapper, "staging-smoke-test", *args],
                        cwd=REPO_ROOT,
                        env=environment,
                        stdin=subprocess.DEVNULL,
                        capture_output=True,
                        text=True,
                        timeout=15,
                    )
                    self.assertNotIn(sentinel, result.stdout + result.stderr)
                    if token != sentinel:
                        self.assertEqual(1, result.returncode, result.stderr)
                        self.assertIn("AI_ID_TOKEN is required", result.stderr)
                        self.assertFalse(call_log.exists())
                        self.assertNotIn("unexpected", result.stderr)
                    else:
                        self.assertEqual(42, result.returncode, result.stderr)
                        arguments = call_log.read_text(encoding="utf-8").splitlines()
                        self.assertNotIn(sentinel, " ".join(arguments))
                        self.assertEqual("@-", arguments[arguments.index("--header") + 1])
                        self.assertEqual(
                            f"Authorization: Bearer {sentinel}\n",
                            header_log.read_text(encoding="utf-8"),
                        )
                        self.assertEqual("5", arguments[arguments.index("--connect-timeout") + 1])
                        self.assertEqual("20", arguments[arguments.index("--max-time") + 1])
                        self.assertEqual("4", arguments[arguments.index("--retry") + 1])
                        self.assertEqual(
                            "https://offertrack-stg-ai-765846644391.europe-central2.run.app/health",
                            arguments[-1],
                        )

class DatabaseProvisioningContractTest(unittest.TestCase):
    def test_passwords_come_from_separate_secret_versions(self) -> None:
        script = read("scripts/staging/provision-database-users.sh")
        self.assertIn("offertrack-stg-db-app-password", script)
        self.assertIn("offertrack-stg-db-migrator-password", script)
        self.assertIn("--out-file", script)
        self.assertNotIn("--password=", script)
        self.assertNotIn("set -x", script)

    def test_migrator_owns_schema_and_app_is_dml_only(self) -> None:
        sql = read("scripts/staging/database/provision-users.sql")
        self.assertIn("ALTER SCHEMA public OWNER TO offertrack_migrator", sql)
        self.assertIn(
            "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO offertrack_app",
            sql,
        )
        self.assertNotRegex(sql, r"GRANT\s+(?:ALL|CREATE).*TO offertrack_app")

    def test_bootstrap_privileges_are_temporary_and_transactional(self) -> None:
        sql = read("scripts/staging/database/provision-users.sql")
        ordered_markers = (
            "BEGIN;",
            "GRANT offertrack_migrator TO CURRENT_USER WITH INHERIT FALSE, SET TRUE;",
            "GRANT CREATE ON DATABASE offertrack TO offertrack_migrator;",
            "ALTER SCHEMA public OWNER TO offertrack_migrator;",
            "ALTER TABLE %I.%I OWNER TO %I",
            "ALTER SEQUENCE %I.%I OWNER TO %I",
            "SET ROLE offertrack_migrator;",
            "GRANT USAGE ON SCHEMA public TO offertrack_app;",
            "ALTER DEFAULT PRIVILEGES FOR ROLE offertrack_migrator",
            "GRANT SELECT, USAGE ON SEQUENCES TO offertrack_app;",
            "RESET ROLE;",
            "REVOKE CREATE ON DATABASE offertrack FROM offertrack_migrator;",
            "REVOKE offertrack_migrator FROM CURRENT_USER;",
            "DO $$",
            "COMMIT;",
        )
        indexes = [sql.index(marker) for marker in ordered_markers]
        self.assertEqual(indexes, sorted(indexes))

    def test_roles_use_cloud_sql_compatible_attributes(self) -> None:
        sql = read("scripts/staging/database/provision-users.sql")
        self.assertEqual(
            2,
            sql.count(
                "ALTER ROLE %I WITH LOGIN PASSWORD %L NOCREATEDB NOCREATEROLE "
                "NOINHERIT NOBYPASSRLS"
            ),
        )
        self.assertNotRegex(sql, r"\b(?:NOSUPERUSER|NOREPLICATION)\b")

    def test_role_security_postcondition_is_transactional(self) -> None:
        sql = read("scripts/staging/database/provision-users.sql")
        postcondition = re.search(r"DO \$\$(.*?)\$\$;", sql, re.DOTALL)
        self.assertIsNotNone(postcondition)
        check = " ".join(postcondition.group(1).split()) if postcondition else ""
        self.assertIn(
            "IF NOT EXISTS ( SELECT FROM pg_catalog.pg_namespace "
            "WHERE nspname = 'public' AND nspowner = 'offertrack_migrator'::regrole "
            ") THEN RAISE EXCEPTION",
            check,
        )
        self.assertIn(
            "IF has_database_privilege('offertrack_migrator', 'offertrack', 'CREATE') "
            "THEN RAISE EXCEPTION",
            check,
        )
        self.assertIn(
            "IF pg_has_role(CURRENT_USER, 'offertrack_migrator', 'SET') THEN RAISE EXCEPTION",
            check,
        )
        self.assertIn(
            "IF EXISTS ( SELECT FROM pg_catalog.pg_roles "
            "WHERE rolname IN ('offertrack_app', 'offertrack_migrator') "
            "AND (rolsuper OR rolreplication OR rolcreatedb OR rolcreaterole "
            "OR rolbypassrls OR rolinherit OR NOT rolcanlogin) ) THEN RAISE EXCEPTION",
            check,
        )
        self.assertLess(sql.index("BEGIN;"), sql.index("DO $$"))
        self.assertLess(sql.rindex("ALTER ROLE"), sql.index("DO $$"))
        self.assertLess(sql.index("$$;"), sql.index("COMMIT;"))


if __name__ == "__main__":
    unittest.main(verbosity=2)

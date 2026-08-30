from __future__ import annotations

import re
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

    def test_redis_uses_the_complete_provider_ca_set(self) -> None:
        self.assertIn("google_redis_instance.staging.server_ca_certs", self.cloud_run)
        self.assertIn("REDIS_TLS_CA_CERTIFICATES", self.cloud_run)
        self.assertRegex(self.cloud_run, r'REDIS_TLS_ENABLED\s+=\s+"true"')
        self.assertNotIn("insecure", self.cloud_run.lower())

    def test_runtime_iam_is_resource_scoped(self) -> None:
        ai_invoker = resource_block(
            self.iam, "google_cloud_run_v2_service_iam_member", "ai_invoker"
        )
        infra_roles = resource_block(
            self.iam, "google_project_iam_member", "infra_project_roles"
        )
        self.assertIn('role     = "roles/run.invoker"', ai_invoker)
        self.assertIn('"core"', ai_invoker)
        self.assertIn('"deployer"', ai_invoker)
        self.assertNotIn('member   = "allUsers"', self.iam)
        self.assertIn('role     = "roles/run.developer"', self.iam)
        self.assertIn('role     = "roles/run.jobsExecutor"', self.iam)
        self.assertIn('role               = "roles/iam.serviceAccountUser"', self.iam)
        for forbidden in (
            "roles/owner",
            "roles/editor",
            "roles/iam.serviceAccountAdmin",
        ):
            self.assertNotIn(forbidden, infra_roles)

    def test_runtime_inputs_are_nullable_only_for_foundation(self) -> None:
        variables = read("infra/terraform/staging/variables.tf")
        for name in ("google_oauth_client_id", "smtp_host", "smtp_port", "smtp_username", "mail_from"):
            block = resource_block(variables, name, name, keyword="variable")
            self.assertIn("default     = null", block)
            self.assertIn("nullable    = true", block)
            self.assertIn("!var.enable_cloud_run_runtime", block)


class DeploymentWorkflowContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = read(".github/workflows/deploy-staging.yml")

    def test_deploys_only_an_exact_ci_approved_main_commit(self) -> None:
        self.assertIn("workflow_run:", self.workflow)
        self.assertIn("github.event.workflow_run.conclusion == 'success'", self.workflow)
        self.assertIn("github.event.workflow_run.event == 'push'", self.workflow)
        self.assertIn("github.event.workflow_run.head_branch == 'main'", self.workflow)
        self.assertIn("vars.STAGING_DEPLOY_ENABLED == 'true'", self.workflow)
        self.assertIn("ref: ${{ github.event.workflow_run.head_sha }}", self.workflow)
        self.assertIn("workload_identity_provider:", self.workflow)
        self.assertNotIn("credentials_json:", self.workflow)
        self.assertNotIn("secrets.", self.workflow)

    def test_rollout_order_blocks_on_ai_and_migration(self) -> None:
        ordered_markers = (
            "Build production Core and AI images",
            "Deploy and verify the AI candidate revision",
            "Update and execute the migration job",
            "Deploy and verify the Core candidate revision",
            "Build the Web image with the resolved Core URL",
            "Deploy and verify the Web candidate revision",
            "Run bounded staging smoke checks",
        )
        indexes = [self.workflow.index(marker) for marker in ordered_markers]
        self.assertEqual(indexes, sorted(indexes))
        self.assertEqual(3, self.workflow.count("--no-traffic"))
        self.assertEqual(3, self.workflow.count("--to-latest --clear-tags"))
        self.assertIn('gcloud run jobs execute "$MIGRATION_JOB"', self.workflow)
        self.assertIn("--wait", self.workflow)

    def test_images_are_commit_tagged_and_deployed_by_digest(self) -> None:
        self.assertIn('offertrack/${component}:$DEPLOY_SHA', self.workflow)
        self.assertIn('${registry}/web:$DEPLOY_SHA', self.workflow)
        self.assertIn("image_summary.fully_qualified_digest", self.workflow)
        self.assertNotRegex(self.workflow, r"(?i)/(?:web|core-api|ai-service):latest")
        self.assertIn("--next-public-api-url \"$CORE_URL\"", self.workflow)
        self.assertIn("image_summary.fully_qualified_digest", self.workflow)
        self.assertIn("CORE_DEPENDENCY_HEALTH_KEY", self.workflow)


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
        self.assertIn("NOSUPERUSER NOCREATEDB NOCREATEROLE", sql)
        self.assertIn("NOBYPASSRLS", sql)


if __name__ == "__main__":
    unittest.main(verbosity=2)

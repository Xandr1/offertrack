import re
import unittest

from test_staging_contracts import read, resource_block


class AuthSecurityContractTest(unittest.TestCase):
    def test_same_site_cookie_and_callback_contract(self):
        core = read("infra/terraform/staging/cloud-run.tf")
        for key, value in {"AUTH_COOKIE_SAME_SITE": "Lax", "JWT_ACCESS_TOKEN_TTL": "15m",
                           "AUTH_SESSION_INACTIVITY_TTL": "7d", "AUTH_SESSION_ABSOLUTE_TTL": "30d",
                           "AUTH_SESSION_MAX_ACTIVE": "10", "AUTH_COOKIE_DOMAIN": ""}.items():
            self.assertRegex(core, rf'{key}\s*=\s*"{value}"')
        self.assertIn('${local.core_service_url}/login/oauth2/code/google', core)
        for service in ("core", "web"):
            block = resource_block(core, "google_cloud_run_v2_service", service)
            self.assertRegex(block, r'default_uri_disabled\s*=\s*true')
            self.assertIn('"INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"', block)
            self.assertIn('image, traffic]', block)

    def test_edge_replaces_untrusted_forwarding_and_limits_candidate_routes(self):
        edge = read("infra/terraform/staging/browser-edge.tf")
        self.assertIn('"X-Forwarded-For: {client_ip_address},{server_ip_address}"', edge)
        for header in ("Forwarded", "X-Forwarded-Host", "X-Forwarded-Port", "X-Forwarded-Prefix"):
            self.assertIn(f'"{header}"', edge)
        self.assertIn('"X-Forwarded-Proto: https"', edge)
        self.assertIn('"TLS_1_2"', edge)
        self.assertRegex(edge, r'enable_cdn\s*=\s*false')
        self.assertIn('for_each = path_matcher.value.probes', edge)
        self.assertIn('full_path_match = match_rules.value', edge)
        self.assertIn('header_name = ":method"', edge)
        self.assertIn('exact_match = "GET"', edge)
        self.assertIn('var.auth_maintenance_enabled ? [true] : []', edge)
        self.assertRegex(edge, r'http_status\s*=\s*503')
        self.assertNotIn('/auth/', edge.split('resource "google_compute_global_address"')[0])

    def test_logs_keep_diagnostics_and_exclude_only_callback_urls(self):
        edge = read("infra/terraform/staging/browser-edge.tf")
        exclusion = resource_block(edge, "google_logging_project_exclusion", "google_callback")
        self.assertIn('/login/oauth2/code/google([?].*)?$', exclusion)
        self.assertIn('httpRequest.requestUrl', exclusion)
        self.assertIn('resource.type="cloud_run_revision"', exclusion)
        self.assertIn('resource.type="http_load_balancer"', exclusion)
        self.assertRegex(edge, r'log_config\s*\{\s*enable\s*=\s*true')
        database = read("infra/terraform/staging/cloud-sql.tf")
        for key, value in {"log_statement": "none", "log_min_duration_statement": "-1",
                           "log_parameter_max_length": "0", "log_parameter_max_length_on_error": "0",
                           "log_min_error_statement": "error", "log_min_messages": "warning",
                           "log_error_verbosity": "default"}.items():
            self.assertRegex(database, rf'{key}\s*=\s*"{value}"')
        self.assertNotIn('panic', database)

    def test_web_image_identity_includes_public_build_configuration(self):
        workflow = read(".github/workflows/deploy-staging.yml")
        self.assertIn('WEB_BUILD_TAG="${DEPLOY_SHA}-${public_config_hash}"', workflow)
        self.assertIn('"staging" "$CORE_URL" | sha256sum', workflow)
        self.assertIn('web:$WEB_BUILD_TAG', workflow)
        self.assertIn('.GOOGLE_REDIRECT_URI == ($core + "/login/oauth2/code/google")', workflow)
        self.assertIn('STAGING_BASE_DOMAIN: ${{ vars.STAGING_BASE_DOMAIN }}', workflow)


if __name__ == "__main__":
    unittest.main()

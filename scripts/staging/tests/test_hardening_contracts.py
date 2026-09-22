from __future__ import annotations

import ipaddress
import os
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path

from test_staging_contracts import read, resource_block


class DeploymentArtifactTest(unittest.TestCase):
    def test_exact_refs_flow_from_preparation_through_scan_gate_to_deployment(self):
        workflow = read(".github/workflows/deploy-staging.yml")
        steps = re.findall(r"(?ms)^      - name: (.*?)(?=^      - name: |\Z)", workflow)
        scans = [step for step in steps if "continue-on-error:" in step]
        self.assertEqual(3, len(scans))
        gate = next(step for step in steps if step.startswith("Apply exact deployment image scan policy"))
        for component, variable in (("web", "web"), ("core-api", "core"), ("ai-service", "ai")):
            ref = "${{ steps.deployment_images.outputs." + variable + "_image }}"
            scan = next(step for step in scans if f"image-ref: {ref}" in step)
            for policy in ("aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25",
                           "version: v0.70.0", "severity: HIGH,CRITICAL", 'ignore-unfixed: "false"',
                           "format: json", "${{ runner.temp }}/offertrack-container-"):
                self.assertIn(policy, scan)
            self.assertIn(f'--scan {component} "{ref}"', gate)
            self.assertIn(f"steps.trivy_{variable}.outcome", gate)
        self.assertIn("always() && steps.deployment_images.outcome == 'success'", gate)
        self.assertNotIn("continue-on-error", gate)
        self.assertLess(workflow.index(gate), workflow.index("gcloud run services update"))
        self.assertLess(workflow.index(gate), workflow.index("gcloud run jobs update"))
        self.assertEqual(["AI", "CORE", "CORE", "WEB"], re.findall(r'--image "\$(\w+)_IMAGE"', workflow))
        self.assertNotRegex(workflow, r"(?:cat|upload-artifact).*trivy")

    def run_preparation(self, existing="", invalid=""):
        workflow = read(".github/workflows/deploy-staging.yml")
        step = re.search(r"(?ms)^      - name: Prepare immutable deployment images\n(.*?)(?=^      - name:)", workflow).group(1)
        script = textwrap.dedent(step.split("        run: |\n", 1)[1])
        harness = r'''
        gcloud() {
          local component="${5##*/}"
          component="${component%%:*}"
          if [[ "$*" == *"--format=none"* ]]; then
            [[ ",$EXISTING," == *",$component,"* ]]
            return
          fi
          if [[ "$component" == ai-service && -n "$INVALID" ]]; then
            if [[ "$INVALID" == scanner-error ]]; then return 1; fi
            if [[ "$INVALID" == blank ]]; then printf '\n'; return; fi
            printf '%s\n' "$INVALID"
            return
          fi
          printf '%s@sha256:%s\n' "${5%:*}" "$(printf '%064d' 0)"
        }
        docker() { printf 'docker %s\n' "$*" >> "$CALLS"; }
        bash() { printf 'build %s\n' "$*" >> "$CALLS"; }
        '''
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            entry = directory / "prepare.sh"
            entry.write_text(textwrap.dedent(harness) + script, encoding="utf-8")
            env = dict(os.environ, REGION="europe-central2", PROJECT_ID="offertrack-staging",
                       REPOSITORY="offertrack", DEPLOY_SHA="a" * 40,
                       CORE_PUBLIC_URL="https://api.staging.example.test", EXISTING=existing,
                       INVALID=invalid, GITHUB_ENV=(directory / "env").as_posix(),
                       GITHUB_OUTPUT=(directory / "output").as_posix(), CALLS=(directory / "calls").as_posix())
            git_bash = Path("C:/Program Files/Git/bin/bash.exe")
            bash = str(git_bash) if os.name == "nt" and git_bash.is_file() else shutil.which("bash")
            result = subprocess.run([bash, entry.as_posix()], env=env, capture_output=True, text=True, timeout=30)
            calls = (directory / "calls").read_text() if (directory / "calls").exists() else ""
            outputs = (directory / "output").read_text() if (directory / "output").exists() else ""
            return result, calls, outputs

    def test_existing_tags_are_reused_and_each_missing_variant_is_built_once(self):
        for mask in range(8):
            names = ("core-api", "ai-service", "web")
            existing = [name for index, name in enumerate(names) if mask & (1 << index)]
            with self.subTest(existing=existing):
                result, calls, outputs = self.run_preparation(",".join(existing))
                self.assertEqual(0, result.returncode, result.stderr)
                missing = [name for name in names[:2] if name not in existing]
                builds = [line for line in calls.splitlines() if line.startswith("build ")]
                self.assertEqual(int(bool(missing)) + int("web" not in existing), len(builds))
                if missing:
                    self.assertIn("--components " + ",".join(missing) + " --tag " + "a" * 40, calls)
                if "web" not in existing:
                    self.assertIn("--components web --app-env staging --next-public-api-url https://api.staging.example.test", calls)
                self.assertEqual(3 - len(existing), calls.count("docker push "))
                self.assertEqual(3, len(outputs.splitlines()))
                for line in outputs.splitlines():
                    self.assertRegex(line, r"^(?:core|ai|web)_image=europe-central2-docker.pkg.dev/offertrack-staging/offertrack/(?:core-api|ai-service|web)@sha256:[0-9a-f]{64}$")

    def test_resolution_fails_closed_on_malformed_wrong_or_multiple_digests(self):
        good = "europe-central2-docker.pkg.dev/offertrack-staging/offertrack/ai-service@sha256:" + "0" * 64
        for invalid in ("blank", " ", "scanner-error", good + "\n", good + "\n" + good,
                        good.replace("ai-service", "web"), good.replace("offertrack-staging", "wrong-project"),
                        good.replace("@sha256:", ":"), good[:-1], good + "x"):
            with self.subTest(invalid=invalid):
                result, _, outputs = self.run_preparation("core-api,ai-service,web", invalid)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("", outputs)


class InfrastructureHardeningTest(unittest.TestCase):
    def test_database_is_private_and_tls_required_for_core_and_migration(self):
        cloud_run = read("infra/terraform/staging/cloud-run.tf")
        database = read("infra/terraform/staging/cloud-sql.tf")
        self.assertRegex(database, r'ipv4_enabled\s*=\s*false')
        self.assertRegex(database, r'ssl_mode\s*=\s*"ENCRYPTED_ONLY"')
        self.assertRegex(cloud_run, r'database_url = "jdbc:postgresql://[^"\n]+\?sslmode=require"')
        self.assertEqual(2, len(re.findall(r'DATABASE_URL\s*=\s*local.database_url', cloud_run)))
        self.assertRegex(cloud_run, r'CORE_MAX_REQUEST_BODY_BYTES\s*=\s*"262144"')

    def test_ai_alone_gets_all_traffic_tag_and_nat_dependencies(self):
        cloud_run = read("infra/terraform/staging/cloud-run.tf")
        ai = resource_block(cloud_run, "google_cloud_run_v2_service", "ai")
        self.assertIn('egress = "ALL_TRAFFIC"', ai)
        self.assertRegex(ai, r'tags\s*=\s*\[local.ai_egress_tag\]')
        for dependency in ("google_compute_router_nat.ai_egress", "google_compute_firewall.ai_deny_non_public",
                           "google_compute_firewall.ai_allow_web", "google_compute_firewall.ai_deny_other"):
            self.assertIn(dependency, ai)
        for name, kind in (("core", "service"), ("migrate", "job"), ("web", "service")):
            block = resource_block(cloud_run, "google_cloud_run_v2_" + kind, name)
            self.assertNotIn("ai_egress_tag", block)
            if name == "web": self.assertNotIn("vpc_access", block)
            else:
                self.assertIn('egress = "PRIVATE_RANGES_ONLY"', block)
                self.assertIn("google_compute_subnetwork.staging.id", block)

    def test_nat_uses_existing_primary_ipv4_range_and_automatic_addresses(self):
        network = read("infra/terraform/staging/network.tf")
        self.assertIn('ai_egress_tag = "offertrack-stg-ai-egress"', network)
        nat = resource_block(network, "google_compute_router_nat", "ai_egress")
        for key, value in {"type": '"PUBLIC"', "nat_ip_allocate_option": '"AUTO_ONLY"',
                           "source_subnetwork_ip_ranges_to_nat": '"LIST_OF_SUBNETWORKS"',
                           "min_ports_per_vm": "64", "endpoint_types": '["ENDPOINT_TYPE_VM"]',
                           "source_ip_ranges_to_nat": '["PRIMARY_IP_RANGE"]',
                           "name": "google_compute_subnetwork.staging.id"}.items():
            self.assertRegex(nat, rf"{key}\s*=\s*{re.escape(value)}")
        self.assertEqual(1, network.count('resource "google_compute_subnetwork"'))
        self.assertNotIn("IPV4_IPV6", network)

    def test_ai_policy_denies_cloud_sql_and_non_public_ranges_before_allowing_only_web(self):
        network = read("infra/terraform/staging/network.tf")
        ranges = [ipaddress.ip_network(cidr) for cidr in re.findall(r'"([0-9.]+/\d+)"',
                  network.split("ai_non_public_ipv4_ranges = [", 1)[1].split("]", 1)[0])]
        for cidr in ("0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
                     "172.16.0.0/12", "192.168.0.0/16", "192.0.0.0/29", "192.0.0.8/32", "192.0.0.170/31",
                     "192.0.2.0/24", "192.88.99.0/24", "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24",
                     "224.0.0.0/4", "240.0.0.0/4", "10.20.4.0/22"):
            self.assertTrue(any(ipaddress.ip_network(cidr).subnet_of(denied) for denied in ranges), cidr)
        for public in ("8.8.8.8", "1.1.1.1", "192.0.0.9", "192.0.0.10"):
            self.assertFalse(any(ipaddress.ip_address(public) in denied for denied in ranges))
        for name, priority in (("ai_deny_non_public", 900), ("ai_allow_web", 1000), ("ai_deny_other", 1100)):
            rule = resource_block(network, "google_compute_firewall", name)
            self.assertRegex(rule, rf"priority\s*=\s*{priority}")
            self.assertRegex(rule, r'target_tags\s*=\s*\[local.ai_egress_tag\]')
            self.assertRegex(rule, r'direction\s*=\s*"EGRESS"')
            if name == "ai_allow_web":
                self.assertRegex(rule, r'allow\s*\{\s*protocol\s*=\s*"tcp"\s*ports\s*=\s*\["80", "443"\]\s*\}')
            else: self.assertRegex(rule, r'deny\s*\{\s*protocol\s*=\s*"all"\s*\}')
            if name == "ai_deny_non_public": self.assertIn("local.ai_non_public_ipv4_ranges", rule)
            else: self.assertRegex(rule, r'destination_ranges\s*=\s*\["0.0.0.0/0"\]')


if __name__ == "__main__":
    unittest.main()

from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]

NODE_IMAGE = (
    "node:24.18.0-bookworm-slim@"
    "sha256:d45d78e7929b46875bbd4e29bea672d5bc48186c6c3588306521c815e78352d6"
)
TEMURIN_IMAGE = (
    "eclipse-temurin:21.0.11_10-jre-alpine-3.23@"
    "sha256:426401268a42785be73823f6115ee0e721bdb59c12c779947b83fcead1a66645"
)
PYTHON_IMAGE = (
    "python:3.11.15-slim-trixie@"
    "sha256:00af38ae2ed311628970782e8a2d7f014d8909dbc63cb97bc0a158187f4db045"
)
POSTGRES_IMAGE = (
    "postgres:16.14-bookworm@"
    "sha256:c95fd5346040eba2de3c435e14874af18f5d681fb5848d4f081dbead0878af28"
)
REDIS_IMAGE = (
    "redis:7.4.9-alpine@"
    "sha256:b1addbe72465a718643cff9e60a58e6df1841e29d6d7d60c9a85d8d72f08d1a7"
)


def read(relative_path: str) -> str:
    return (REPO_ROOT / relative_path).read_text(encoding="utf-8")


def service_block(compose_text: str, service_name: str) -> str:
    match = re.search(
        rf"^  {re.escape(service_name)}:\s*$\n(.*?)(?=^  [a-zA-Z0-9_-]+:\s*$|^volumes:\s*$|\Z)",
        compose_text,
        flags=re.MULTILINE | re.DOTALL,
    )
    if match is None:
        raise AssertionError(f"Compose service {service_name!r} is missing")
    return match.group(1)


class ImageContractTest(unittest.TestCase):
    def test_web_is_a_pinned_standalone_multistage_image(self) -> None:
        dockerfile = read("apps/web/Dockerfile")
        runtime = dockerfile[dockerfile.rindex("FROM ") :]

        self.assertEqual(2, dockerfile.count(NODE_IMAGE))
        self.assertIn("npm install --global pnpm@10.34.0", dockerfile)
        self.assertIn("pnpm install --frozen-lockfile", dockerfile)
        self.assertIn("ARG APP_ENV", dockerfile)
        self.assertIn("ARG NEXT_PUBLIC_API_URL", dockerfile)
        self.assertIn("pnpm --dir apps/web run build", dockerfile)
        self.assertIn("/workspace/apps/web/.next/standalone/ /app/", runtime)
        self.assertIn("/app/apps/web/public/", runtime)
        self.assertIn("/app/apps/web/.next/static/", runtime)
        self.assertIn("WORKDIR /app/apps/web", runtime)
        self.assertIn("USER 1000:1000", runtime)
        self.assertIn("HOSTNAME=0.0.0.0", runtime)
        self.assertIn("PORT=3000", runtime)
        self.assertIn('CMD ["node", "server.js"]', runtime)
        for build_only_path in (
            "/usr/local/lib/node_modules/npm",
            "/usr/local/lib/node_modules/corepack",
            "/opt/yarn-v1.22.22",
            "/usr/local/bin/npm",
            "/usr/local/bin/corepack",
        ):
            self.assertIn(build_only_path, runtime)
        self.assertNotIn("ARG APP_ENV", runtime)
        self.assertNotIn("ARG NEXT_PUBLIC_API_URL", runtime)
        self.assertNotRegex(runtime, r"(?m)^\s*ENV\s+.*(?:APP_ENV|NEXT_PUBLIC_API_URL)")

    def test_web_context_allowlist_is_repository_root_relative(self) -> None:
        dockerignore = read("apps/web/Dockerfile.dockerignore")
        patterns = [line.strip() for line in dockerignore.splitlines() if line.strip()]

        self.assertEqual("**", patterns[0])
        for expected in (
            "!package.json",
            "!pnpm-lock.yaml",
            "!pnpm-workspace.yaml",
            "!apps/",
            "!apps/web/",
            "!apps/web/Dockerfile",
            "!apps/web/Dockerfile.dockerignore",
            "!apps/web/package.json",
            "!apps/web/next.config.ts",
            "!apps/web/postcss.config.mjs",
            "!apps/web/tsconfig.json",
            "!apps/web/vendor/lucide-react-0.542.0.tgz",
            "!apps/web/app/**",
            "!apps/web/src/**",
            "!apps/web/public/**",
            "apps/web/**/*.test.*",
        ):
            self.assertIn(expected, patterns)
        self.assertFalse(any(pattern.startswith("!/") for pattern in patterns))
        self.assertIn("**/.env*", patterns)
        self.assertIn("**/.next/**", patterns)
        self.assertIn("**/node_modules/**", patterns)
        self.assertIn("**/playwright-report/**", patterns)
        self.assertEqual("apps/web/**/*.test.*", patterns[-1])

    def test_next_config_retains_roots_and_enables_standalone(self) -> None:
        config = read("apps/web/next.config.ts")
        self.assertIn('output: "standalone"', config)
        self.assertIn("outputFileTracingRoot: repoRoot", config)
        self.assertIn("turbopack: {", config)
        self.assertIn("root: repoRoot", config)

    def test_core_is_a_runtime_only_pinned_jre_image(self) -> None:
        dockerfile = read("apps/core-api/Dockerfile")
        self.assertEqual(1, len(re.findall(r"(?m)^FROM\s+", dockerfile)))
        self.assertIn(f"FROM {TEMURIN_IMAGE}", dockerfile)
        self.assertIn(
            "COPY --chown=10001:10001 --chmod=0444 app.jar /app/app.jar",
            dockerfile,
        )
        self.assertIn("ENV SERVER_PORT=8080", dockerfile)
        for fixed_package in (
            "libexpat=2.8.2-r0",
            "p11-kit=0.26.2-r0",
            "p11-kit-trust=0.26.2-r0",
        ):
            self.assertIn(fixed_package, dockerfile)
        self.assertIn("USER 10001:10001", dockerfile)
        self.assertIn('ENTRYPOINT ["java", "-jar", "/app/app.jar"]', dockerfile)
        self.assertNotIn("mvn", dockerfile.lower())
        self.assertNotIn("jdk", dockerfile.lower())

    def test_maven_wrapper_distribution_has_official_sha256(self) -> None:
        wrapper = read("apps/core-api/.mvn/wrapper/maven-wrapper.properties")
        match = re.search(
            r"(?m)^distributionSha256Sum=([0-9a-f]{64})$",
            wrapper,
        )

        self.assertIsNotNone(match)
        self.assertEqual(
            "b0d9292f06c5faded31ddcb6cb69099f316d6fea40a7778624b259bad9fed18a",
            match.group(1) if match is not None else None,
        )

    def test_core_runtime_port_and_shutdown_fallbacks_are_explicit(self) -> None:
        application = read("apps/core-api/src/main/resources/application.yml")
        e2e = read("apps/core-api/src/main/resources/application-e2e.yml")
        smoke = read("apps/core-api/src/main/resources/application-container-smoke.yml")

        self.assertIn("port: ${PORT:${SERVER_PORT:8080}}", application)
        self.assertIn("shutdown: graceful", application)
        self.assertIn(
            "timeout-per-shutdown-phase: ${SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE:10s}",
            application,
        )
        self.assertIn("port: ${PORT:${SERVER_PORT:18080}}", e2e)
        self.assertIn("include: readinessState,db,redis", smoke)
        self.assertIn("show-components: always", smoke)
        self.assertIn("show-details: never", smoke)

    def test_ai_copies_only_an_isolated_virtual_environment(self) -> None:
        dockerfile = read("apps/ai-service/Dockerfile")
        runtime = dockerfile[dockerfile.rindex("FROM ") :]

        self.assertEqual(2, dockerfile.count(PYTHON_IMAGE))
        self.assertIn("python -m venv /opt/venv", dockerfile)
        self.assertIn("COPY --from=dependencies /opt/venv /opt/venv", runtime)
        self.assertNotRegex(runtime, r"COPY\s+--from=dependencies\s+/usr/local")
        self.assertIn("HOST=0.0.0.0", runtime)
        self.assertIn("PORT=8000", runtime)
        self.assertIn("USER 10001:10001", runtime)
        self.assertIn("EXPOSE 8000", runtime)
        self.assertIn("CMD []", runtime)
        self.assertEqual(2, dockerfile.count("pip uninstall --yes setuptools wheel"))
        self.assertEqual(2, dockerfile.count("pip uninstall --yes pip"))
        entrypoint = read("apps/ai-service/docker-entrypoint.sh")
        self.assertIn(
            'exec /opt/venv/bin/python -m uvicorn app.main:app --host "$HOST" --port "$PORT"',
            entrypoint,
        )

    def test_ai_context_final_denies_follow_app_allow_rule(self) -> None:
        dockerignore = read("apps/ai-service/Dockerfile.dockerignore")
        patterns = [
            line.strip()
            for line in dockerignore.splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        ]
        app_allow_index = patterns.index("!app/**")

        for expected in (
            "app/**/.env*",
            "app/**/*.pem",
            "app/**/*.key",
            "app/**/*.p12",
            "app/**/*.pfx",
            "app/**/*credentials*.json",
            "app/**/*service-account*.json",
            "app/**/*service_account*.json",
            "app/**/test/",
            "app/**/tests/",
            "app/**/.pytest_cache/",
            "app/**/.ruff_cache/",
            "app/**/.mypy_cache/",
            "app/**/.pyright/",
            "app/**/__pycache__/",
            "app/**/test.py",
            "app/**/test_*.py",
            "app/**/*_test.py",
            "app/**/conftest.py",
        ):
            self.assertIn(expected, patterns)
            self.assertGreater(patterns.index(expected), app_allow_index)

        verifier = read("apps/ai-service/scripts/verify-runtime-image.sh")
        smoke = read("scripts/containers/run-smoke.sh")
        self.assertIn(".env.synthetic-canary", verifier)
        self.assertIn('application_root = Path("/app/app")', verifier)
        self.assertIn('application_root = Path("/app/app")', smoke)
        self.assertIn('for path in application_root.rglob("*")', verifier)
        self.assertIn('for path in application_root.rglob("*")', smoke)
        for package in ("pip", "setuptools", "wheel", "jaraco.context"):
            self.assertIn(f'"{package}"', verifier)


class BuildScriptContractTest(unittest.TestCase):
    def test_validation_occurs_before_any_docker_command(self) -> None:
        script = read("scripts/containers/build-images.sh")
        validation = script.index('node "$ENV_VALIDATOR"')
        first_docker = script.index("command -v docker")
        self.assertLess(validation, first_docker)
        self.assertIn('IMAGE_TAG="local"', script)
        self.assertIn("APP_ENV_SEEN=false", script)
        self.assertIn("API_URL_SEEN=false", script)
        self.assertIn("TAG_SEEN=false", script)

    def test_codegen_is_isolated_and_uses_docker_assigned_port(self) -> None:
        script = read("scripts/containers/build-images.sh")
        compose = read("compose.container-codegen.yml")

        self.assertIn('--env-file "$CODEGEN_ENV_FILE"', script)
        self.assertIn('--project-directory "$CODEGEN_RUNTIME_DIR"', script)
        self.assertIn(
            "env -u POSTGRES_DB -u POSTGRES_USER -u POSTGRES_PASSWORD docker compose",
            script,
        )
        self.assertNotRegex(script, r"(?m)^\s*(?:source|\.)\s+.*CODEGEN_ENV_FILE")
        self.assertIn("'POSTGRES_DB=offertrack_codegen'", script)
        self.assertIn("'POSTGRES_USER=offertrack_codegen'", script)
        self.assertIn(
            "'POSTGRES_PASSWORD=container-codegen-postgres-password-not-for-production'",
            script,
        )
        self.assertIn("port --index 1 codegen-postgres 5432", script)
        self.assertIn(r"^127\.0\.0\.1:([0-9]+)$", script)
        self.assertIn("target: 5432", compose)
        self.assertIn("host_ip: 127.0.0.1", compose)
        self.assertNotIn("published:", compose)
        self.assertNotRegex(compose, r'127\.0\.0\.1:0:5432|["\']0:5432')

    def test_core_packaging_command_and_jar_validation_are_exact(self) -> None:
        script = read("scripts/containers/build-images.sh")
        self.assertIn(
            'DATABASE_URL="jdbc:postgresql://127.0.0.1:${CODEGEN_PORT}/offertrack_codegen"',
            script,
        )
        self.assertIn('DB_USER="offertrack_codegen"', script)
        self.assertIn(
            'DB_PASSWORD="container-codegen-postgres-password-not-for-production"', script
        )
        self.assertIn(
            '"${MAVEN_COMMAND[@]}" clean flyway:migrate package -DskipTests', script
        )
        self.assertIn("[[ ${#CORE_JARS[@]} -ne 1 ]]", script)
        self.assertIn("META-INF/MANIFEST.MF", script)
        self.assertIn("org.springframework.boot.loader.launch.JarLauncher", script)
        self.assertIn(
            "org/springframework/boot/loader/launch/JarLauncher.class", script
        )
        self.assertIn("Start-Class: com.offertrack.CoreApiApplication", script)
        self.assertIn("BOOT-INF/classes/com/offertrack/CoreApiApplication.class", script)
        self.assertIn("^BOOT-INF/lib/.*\\.jar$", script)
        self.assertIn('cp -- "$CORE_JAR" "$CORE_CONTEXT/app.jar"', script)

    def test_all_builds_load_linux_amd64_and_verify_architecture(self) -> None:
        script = read("scripts/containers/build-images.sh")
        self.assertEqual(
            3, script.count("docker buildx build --platform linux/amd64 --load")
        )
        for image in ("offertrack/web", "offertrack/core-api", "offertrack/ai-service"):
            self.assertIn(f'--tag "{image}:$IMAGE_TAG"', script)
            self.assertIn(f'verify_image "{image}:$IMAGE_TAG"', script)
        self.assertIn("{{.Os}}/{{.Architecture}}", script)
        self.assertIn('[[ "$platform" == "linux/amd64" ]]', script)

    def test_cleanup_is_scoped_to_validated_project_and_temporary_directory(self) -> None:
        script = read("scripts/containers/build-images.sh")
        self.assertIn('^[a-z0-9][a-z0-9_-]*$', script)
        self.assertIn('--project-name "$CODEGEN_PROJECT"', script)
        self.assertIn("down --volumes --remove-orphans", script)
        self.assertIn('"$TEMP_PARENT"/codegen."$CODEGEN_PROJECT".*', script)
        self.assertNotIn("docker system prune", script)
        self.assertNotIn("docker volume prune", script)


class ComposeContractTest(unittest.TestCase):
    def test_all_postgres_and_redis_compose_references_are_pinned(self) -> None:
        for relative_path in (
            "docker-compose.yml",
            "docker-compose.e2e.yml",
            "compose.container-codegen.yml",
            "compose.container-smoke.yml",
        ):
            compose = read(relative_path)
            for line in compose.splitlines():
                stripped = line.strip()
                if stripped.startswith("image: postgres:"):
                    self.assertEqual(f"image: {POSTGRES_IMAGE}", stripped)
                if stripped.startswith("image: redis:"):
                    self.assertEqual(f"image: {REDIS_IMAGE}", stripped)

    def test_pinned_infrastructure_and_portable_codegen_compose(self) -> None:
        codegen = read("compose.container-codegen.yml")
        self.assertIn(f"image: {POSTGRES_IMAGE}", codegen)
        self.assertIn("platform: linux/amd64", codegen)
        self.assertIn("codegen_postgres_data:/var/lib/postgresql/data", codegen)
        for setting in ("POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD"):
            self.assertIn(f"{setting}: ${{{setting}:?", codegen)
        self.assertIn("interval: 2s", codegen)
        self.assertIn("timeout: 5s", codegen)
        self.assertIn("retries: 30", codegen)

    def test_smoke_compose_has_exact_images_platform_and_no_pull(self) -> None:
        compose = read("compose.container-smoke.yml")
        self.assertIn(f"image: {POSTGRES_IMAGE}", compose)
        self.assertIn(f"image: {REDIS_IMAGE}", compose)
        self.assertEqual(5, compose.count("platform: linux/amd64"))
        self.assertEqual(3, compose.count("pull_policy: never"))
        for image in ("web", "core-api", "ai-service"):
            self.assertIn(
                f"image: offertrack/{image}:${{OFFERTRACK_IMAGE_TAG:?OFFERTRACK_IMAGE_TAG is required}}",
                compose,
            )

    def test_smoke_literal_ports_and_hardening(self) -> None:
        compose = read("compose.container-smoke.yml")
        expected_ports = (
            "127.0.0.1:55433:5432",
            "127.0.0.1:56380:6379",
            "127.0.0.1:13001:13001",
            "127.0.0.1:18001:18001",
            "127.0.0.1:18081:18081",
        )
        for port in expected_ports:
            self.assertIn(port, compose)
        self.assertEqual(3, compose.count('read_only: true'))
        self.assertEqual(3, compose.count('cap_drop: ["ALL"]'))
        self.assertEqual(3, compose.count('security_opt: ["no-new-privileges:true"]'))
        self.assertEqual(3, compose.count("stop_grace_period: 15s"))
        self.assertEqual(3, compose.count("size=64m,mode=1777"))
        self.assertIn(
            "/app/apps/web/.next/cache:rw,nosuid,nodev,size=128m,mode=0750,uid=1000,gid=1000",
            compose,
        )

    def test_smoke_web_contract_omits_build_only_environment(self) -> None:
        compose = read("compose.container-smoke.yml")
        web = service_block(compose, "web")
        self.assertIn("NODE_ENV: production", web)
        self.assertIn("HOSTNAME: 0.0.0.0", web)
        self.assertIn('PORT: "13001"', web)
        self.assertNotIn("APP_ENV", web)
        self.assertNotIn("NEXT_PUBLIC_API_URL", web)
        self.assertIn("process.env.PORT + '/login'", web)
        for value in ("interval: 2s", "timeout: 5s", "retries: 30"):
            self.assertIn(value, web)

    def test_smoke_ai_and_core_configuration_is_literal_and_complete(self) -> None:
        compose = read("compose.container-smoke.yml")
        ai = service_block(compose, "ai-service")
        core = service_block(compose, "core-api")

        for expected in (
            "APP_ENV: test",
            "HOST: 0.0.0.0",
            'PORT: "18001"',
            'OPENAI_API_KEY: ""',
            "OPENAI_MODEL: gpt-5.4-mini",
            'OPENAI_TIMEOUT_SECONDS: "30"',
            "AI_SERVICE_ALLOWED_HOSTS: ai-service,localhost,127.0.0.1",
            'AI_SERVICE_DOCS_ENABLED: "false"',
        ):
            self.assertIn(expected, ai)
        self.assertIn("assert json.load", ai)
        self.assertIn("{'status': 'ok'}", ai)

        for expected in (
            "SPRING_PROFILES_ACTIVE: container-smoke",
            'PORT: "18081"',
            'SERVER_PORT: "18081"',
            "DATABASE_URL: jdbc:postgresql://postgres:5432/offertrack_container_smoke",
            "REDIS_HOST: redis",
            "REDIS_CONNECT_TIMEOUT: 2s",
            "REDIS_TIMEOUT: 2s",
            "APP_WEB_URL: http://127.0.0.1:13001",
            "CORS_ALLOWED_ORIGINS: http://127.0.0.1:13001",
            "AI_SERVICE_BASE_URL: http://ai-service:18001",
            'AI_DRAFT_CACHE_ENABLED: "false"',
            'RATE_LIMIT_FAIL_OPEN: "false"',
            "SERVER_FORWARD_HEADERS_STRATEGY: none",
            "SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE: 10s",
        ):
            self.assertIn(expected, core)

    def test_infrastructure_and_web_health_checks_are_bounded(self) -> None:
        compose = read("compose.container-smoke.yml")
        for service in ("postgres", "redis", "web"):
            block = service_block(compose, service)
            self.assertIn("interval: 2s", block)
            self.assertIn("timeout: 5s", block)
            self.assertIn("retries: 30", block)

    def test_compose_files_render_without_an_engine_or_build(self) -> None:
        docker = shutil.which("docker")
        if docker is None:
            self.skipTest("Docker Compose CLI is unavailable")

        version = subprocess.run(
            [docker, "compose", "version"],
            cwd=REPO_ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        if version.returncode != 0:
            self.skipTest("Docker Compose CLI is unavailable")

        environment = os.environ.copy()
        environment["OFFERTRACK_IMAGE_TAG"] = "contract"
        smoke = subprocess.run(
            [
                docker,
                "compose",
                "--project-name",
                "offertrack-contract-123-1",
                "--file",
                str(REPO_ROOT / "compose.container-smoke.yml"),
                "config",
                "--quiet",
            ],
            cwd=REPO_ROOT,
            env=environment,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, smoke.returncode, smoke.stdout + smoke.stderr)

        with tempfile.TemporaryDirectory() as temporary_directory:
            env_file = Path(temporary_directory) / "codegen.env"
            env_file.write_text(
                "POSTGRES_DB=offertrack_codegen\n"
                "POSTGRES_USER=offertrack_codegen\n"
                "POSTGRES_PASSWORD=container-codegen-postgres-password-not-for-production\n",
                encoding="utf-8",
            )
            codegen = subprocess.run(
                [
                    docker,
                    "compose",
                    "--project-name",
                    "offertrack-codegen-123-1",
                    "--env-file",
                    str(env_file),
                    "--file",
                    str(REPO_ROOT / "compose.container-codegen.yml"),
                    "config",
                    "--quiet",
                ],
                cwd=REPO_ROOT,
                check=False,
                capture_output=True,
                text=True,
            )
        self.assertEqual(0, codegen.returncode, codegen.stdout + codegen.stderr)


class SmokeAndE2EContractTest(unittest.TestCase):
    def test_smoke_builds_with_the_required_public_values_and_always_verifies_images(self) -> None:
        script = read("scripts/containers/run-smoke.sh")
        self.assertIn("--app-env e2e", script)
        self.assertIn("--next-public-api-url http://127.0.0.1:18081", script)
        for image in ("offertrack/web", "offertrack/core-api", "offertrack/ai-service"):
            self.assertIn(f'verify_image "{image}:$IMAGE_TAG"', script)
        self.assertIn("linux/amd64", script)
        self.assertIn("compose down --volumes --remove-orphans", script)
        self.assertIn('^[a-z0-9][a-z0-9_-]*$', script)

    def test_smoke_keeps_bounded_web_api_runtime_and_restart_checks(self) -> None:
        script = read("scripts/containers/run-smoke.sh")
        web_verifier = read("scripts/containers/verify-web-output.py")
        api_verifier = read("scripts/containers/verify-smoke-api.py")

        self.assertIn("--expected-api-url http://127.0.0.1:18081", script)
        self.assertIn("/offertrack-logo.png", web_verifier)
        self.assertIn("/_next/image", web_verifier)
        self.assertIn("Checking session...", web_verifier)
        self.assertIn("script", web_verifier)
        self.assertIn("MAX_SCRIPTS", web_verifier)
        self.assertIn("http://127.0.0.1/", api_verifier)
        self.assertIn("AI_SERVICE_INVALID_URL", api_verifier)
        self.assertIn('json.load(response) == {"status": "ok"}', script)
        self.assertIn("stop_and_start core-api", script)
        self.assertIn("stop_and_start ai-service", script)
        self.assertIn("stop_and_start web", script)
        self.assertIn("wait_for_health web", script)
        self.assertIn("http://127.0.0.1:13001/login", script)
        self.assertIn("{{json .Config.Cmd}}", script)
        self.assertIn("'[\"node\",\"server.js\"]'", script)
        self.assertIn("local deadline=$((SECONDS + 120))", script)
        self.assertIn('--max-time "$request_timeout"', script)
        self.assertNotIn("find /", script)

    def test_e2e_standalone_assets_and_process_scoped_port_are_exact(self) -> None:
        script = read("scripts/e2e/run-e2e.sh")
        self.assertIn(
            'STANDALONE_WEB_ROOT="$WEB_ROOT/.next/standalone/apps/web"', script
        )
        self.assertIn(
            'cp -R "$WEB_ROOT/public/." "$STANDALONE_WEB_ROOT/public/"', script
        )
        self.assertIn(
            'cp -R "$WEB_ROOT/.next/static/." "$STANDALONE_WEB_ROOT/.next/static/"',
            script,
        )
        self.assertIn("env -u PORT java -jar", script)
        self.assertIn("exec env PORT=13000 HOSTNAME=127.0.0.1 node server.js", script)
        self.assertNotRegex(script, r"(?m)^\s*export PORT=")


class CIContractTest(unittest.TestCase):
    def test_container_job_builds_once_scans_independently_then_smokes_and_gates(self) -> None:
        workflow = read(".github/workflows/ci.yml")
        job = workflow[workflow.index("  container-smoke:") : workflow.index("\n  e2e:")]

        self.assertIn("timeout-minutes: 40", job)
        for prerequisite in ("frontend", "backend", "ai-service"):
            self.assertIn(f"- {prerequisite}", job)
        self.assertIn(
            "docker/setup-buildx-action@bb05f3f5519dd87d3ba754cc423b652a5edd6d2c",
            job,
        )
        self.assertIn(
            'run: TMPDIR="$OFFERTRACK_CONTAINER_TEMP_ROOT" bash scripts/containers/test.sh',
            job,
        )
        self.assertEqual(
            3,
            job.count(
                "aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25"
            ),
        )
        for exact_option in (
            "version: v0.70.0",
            "scan-type: image",
            "format: json",
            "scanners: vuln",
            "vuln-type: os,library",
            "severity: HIGH,CRITICAL",
            'ignore-unfixed: "false"',
            'exit-code: "0"',
            'hide-progress: "true"',
            'list-all-pkgs: "false"',
            "timeout: 5m0s",
        ):
            self.assertEqual(3, job.count(exact_option), exact_option)
        self.assertEqual(3, job.count("continue-on-error: true"))

        build_index = job.index("id: build_images")
        web_scan_index = job.index("id: trivy_web")
        core_scan_index = job.index("id: trivy_core")
        ai_scan_index = job.index("id: trivy_ai")
        smoke_index = job.index("id: container_smoke")
        gate_index = job.index("id: trivy_gate")
        self.assertLess(build_index, web_scan_index)
        self.assertLess(web_scan_index, core_scan_index)
        self.assertLess(core_scan_index, ai_scan_index)
        self.assertLess(ai_scan_index, smoke_index)
        self.assertLess(smoke_index, gate_index)
        self.assertIn("steps.trivy_web.outcome", job[gate_index:])
        self.assertIn("steps.trivy_core.outcome", job[gate_index:])
        self.assertIn("steps.trivy_ai.outcome", job[gate_index:])

    def test_ci_scope_and_always_cleanup_are_exact(self) -> None:
        workflow = read(".github/workflows/ci.yml")
        job = workflow[workflow.index("  container-smoke:") : workflow.index("\n  e2e:")]

        self.assertIn(
            "offertrack-smoke-${{ github.run_id }}-${{ github.run_attempt }}", job
        )
        self.assertIn(
            "offertrack-codegen-${{ github.run_id }}-${{ github.run_attempt }}", job
        )
        self.assertIn(
            "offertrack-container-${{ github.run_id }}-${{ github.run_attempt }}", job
        )
        self.assertIn("^offertrack-smoke-[0-9]+-[0-9]+$", job)
        self.assertIn("^offertrack-codegen-[0-9]+-[0-9]+$", job)
        cleanup = job[job.index("Clean up exact container projects and temporary files") :]
        self.assertIn("if: always()", cleanup)
        self.assertIn("--file compose.container-smoke.yml", cleanup)
        self.assertIn("--file compose.container-codegen.yml", cleanup)
        self.assertEqual(2, cleanup.count("down --volumes --remove-orphans"))
        self.assertIn("codegen-cleanup.env", cleanup)
        self.assertIn(
            "env -u POSTGRES_DB -u POSTGRES_USER -u POSTGRES_PASSWORD docker compose",
            cleanup,
        )
        self.assertIn('rm -rf -- "$OFFERTRACK_CONTAINER_TEMP_ROOT"', cleanup)
        self.assertNotIn("docker system prune", cleanup)
        self.assertNotIn("docker volume prune", cleanup)


if __name__ == "__main__":
    unittest.main()

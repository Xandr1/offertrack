locals {
  cloud_run_names = {
    ai       = "offertrack-stg-ai"
    core_api = "offertrack-stg-core"
    migrate  = "offertrack-stg-migrate"
    web      = "offertrack-stg-web"
  }

  ai_service_host   = "${local.cloud_run_names.ai}-${var.project_number}.${var.region}.run.app"
  ai_candidate_host = "candidate---${local.ai_service_host}"
  core_service_host = "${local.cloud_run_names.core_api}-${var.project_number}.${var.region}.run.app"
  web_service_host  = "${local.cloud_run_names.web}-${var.project_number}.${var.region}.run.app"
  ai_service_url    = "https://${local.ai_service_host}"
  core_service_url  = "https://${local.core_service_host}"
  web_service_url   = "https://${local.web_service_host}"

  runtime_images = var.initial_images == null ? {
    ai       = "invalid.local/ai-service@sha256:0000000000000000000000000000000000000000000000000000000000000000"
    core_api = "invalid.local/core-api@sha256:0000000000000000000000000000000000000000000000000000000000000000"
    web      = "invalid.local/web@sha256:0000000000000000000000000000000000000000000000000000000000000000"
  } : var.initial_images

  database_url = "jdbc:postgresql://${google_sql_database_instance.postgres.private_ip_address}:5432/${google_sql_database.offertrack.name}"
  redis_tls_ca_certificates = join("\n", [
    for server_ca in google_redis_instance.staging.server_ca_certs : trimspace(server_ca.cert)
  ])

  ai_environment = {
    AI_SERVICE_ALLOWED_HOSTS         = "localhost,${local.ai_service_host},${local.ai_candidate_host}"
    AI_SERVICE_DOCS_ENABLED          = "false"
    AI_SERVICE_FETCH_TIMEOUT_SECONDS = "10"
    AI_SERVICE_MAX_JOB_TEXT_CHARS    = "18000"
    AI_SERVICE_MAX_REDIRECTS         = "5"
    AI_SERVICE_MAX_RESPONSE_BYTES    = "10000000"
    APP_ENV                          = "staging"
    OPENAI_MODEL                     = "gpt-5.4-mini"
    OPENAI_TIMEOUT_SECONDS           = "30"
  }

  ai_secret_environment = {
    AI_SERVICE_INTERNAL_API_KEY = {
      secret  = "ai_internal_key"
      version = var.secret_versions.ai_internal_key
    }
    OPENAI_API_KEY = {
      secret  = "openai_api_key"
      version = var.secret_versions.openai_api_key
    }
  }

  core_environment = {
    AI_DRAFT_CACHE_ENABLED                      = "true"
    AI_DRAFT_CACHE_TTL                          = "24h"
    AI_SERVICE_AUDIENCE                         = local.ai_service_url
    AI_SERVICE_AUTH_MODE                        = "google-id-token"
    AI_SERVICE_BASE_URL                         = local.ai_service_url
    APP_WEB_URL                                 = local.web_service_url
    AUTH_COOKIE_DOMAIN                          = ""
    AUTH_COOKIE_NAME                            = "access_token"
    AUTH_COOKIE_PATH                            = "/"
    AUTH_COOKIE_SAME_SITE                       = "None"
    AUTH_COOKIE_SECURE                          = "true"
    CORS_ALLOWED_ORIGINS                        = local.web_service_url
    DATABASE_URL                                = local.database_url
    DB_USER                                     = "offertrack_app"
    GOOGLE_CLIENT_ID                            = trimspace(var.google_oauth_client_id)
    JWT_ACCESS_TOKEN_TTL                        = "48h"
    MAIL_FROM                                   = trimspace(var.mail_from)
    OFFERTRACK_RUN_MODE                         = "server"
    RATE_LIMIT_FAIL_OPEN                        = "false"
    REDIS_CONNECT_TIMEOUT                       = "2s"
    REDIS_HOST                                  = google_redis_instance.staging.host
    REDIS_PORT                                  = tostring(google_redis_instance.staging.port)
    REDIS_TIMEOUT                               = "2s"
    REDIS_TLS_CA_CERTIFICATES                   = local.redis_tls_ca_certificates
    REDIS_TLS_ENABLED                           = "true"
    SERVER_FORWARD_HEADERS_STRATEGY             = "framework"
    SMTP_HOST                                   = trimspace(var.smtp_host)
    SMTP_PORT                                   = tostring(var.smtp_port)
    SMTP_USERNAME                               = trimspace(var.smtp_username)
    SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT = "5000"
    SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE  = "5"
    SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE       = "0"
    SPRING_FLYWAY_ENABLED                       = "false"
    SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE = "10s"
    SPRING_PROFILES_ACTIVE                      = "staging"
  }

  core_secret_environment = {
    AI_SERVICE_INTERNAL_API_KEY = {
      secret  = "ai_internal_key"
      version = var.secret_versions.ai_internal_key
    }
    DB_PASSWORD = {
      secret  = "db_app_password"
      version = var.secret_versions.db_app_password
    }
    GOOGLE_CLIENT_SECRET = {
      secret  = "google_client_secret"
      version = var.secret_versions.google_client_secret
    }
    JWT_SECRET = {
      secret  = "jwt_secret"
      version = var.secret_versions.jwt_secret
    }
    OAUTH_COOKIE_SECRET = {
      secret  = "oauth_cookie_secret"
      version = var.secret_versions.oauth_cookie_secret
    }
    RATE_LIMIT_KEY_SECRET = {
      secret  = "rate_limit_key"
      version = var.secret_versions.rate_limit_key
    }
    SMTP_PASSWORD = {
      secret  = "smtp_password"
      version = var.secret_versions.smtp_password
    }
  }

  migration_environment = {
    DATABASE_URL           = local.database_url
    DB_USER                = "offertrack_migrator"
    OFFERTRACK_RUN_MODE    = "migrate"
    SPRING_PROFILES_ACTIVE = "staging"
  }

  web_environment = {
    APP_ENV = "staging"
  }
}

resource "google_cloud_run_v2_service" "ai" {
  count = var.enable_cloud_run_runtime ? 1 : 0

  project             = var.project_id
  name                = local.cloud_run_names.ai
  location            = var.region
  deletion_protection = true
  ingress             = "INGRESS_TRAFFIC_ALL"

  template {
    service_account                  = google_service_account.staging["ai"].email
    timeout                          = "60s"
    execution_environment            = "EXECUTION_ENVIRONMENT_GEN2"
    max_instance_request_concurrency = 4

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    containers {
      name  = "ai-service"
      image = local.runtime_images.ai

      ports {
        name           = "http1"
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle          = true
        startup_cpu_boost = true
      }

      dynamic "env" {
        for_each = local.ai_environment
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = local.ai_secret_environment
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.staging[env.value.secret].secret_id
              version = env.value.version
            }
          }
        }
      }

      startup_probe {
        initial_delay_seconds = 0
        timeout_seconds       = 2
        period_seconds        = 5
        failure_threshold     = 24
        http_get {
          path = "/health"
          port = 8080
        }
      }

      liveness_probe {
        initial_delay_seconds = 0
        timeout_seconds       = 2
        period_seconds        = 30
        failure_threshold     = 3
        http_get {
          path = "/health"
          port = 8080
        }
      }

      readiness_probe {
        timeout_seconds   = 2
        period_seconds    = 10
        success_threshold = 1
        failure_threshold = 3
        http_get {
          path = "/health"
          port = 8080
        }
      }
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]
  }

  depends_on = [
    google_project_service.required["run.googleapis.com"],
    google_secret_manager_secret_iam_member.runtime_access,
  ]
}

resource "google_cloud_run_v2_service" "core" {
  count = var.enable_cloud_run_runtime ? 1 : 0

  project             = var.project_id
  name                = local.cloud_run_names.core_api
  location            = var.region
  deletion_protection = true
  ingress             = "INGRESS_TRAFFIC_ALL"

  template {
    service_account                  = google_service_account.staging["core"].email
    timeout                          = "60s"
    execution_environment            = "EXECUTION_ENVIRONMENT_GEN2"
    max_instance_request_concurrency = 20

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    vpc_access {
      egress = "PRIVATE_RANGES_ONLY"
      network_interfaces {
        network    = google_compute_network.staging.id
        subnetwork = google_compute_subnetwork.staging.id
      }
    }

    containers {
      name  = "core-api"
      image = local.runtime_images.core_api

      ports {
        name           = "http1"
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "1Gi"
        }
        cpu_idle          = true
        startup_cpu_boost = true
      }

      dynamic "env" {
        for_each = local.core_environment
        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = local.core_secret_environment
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.staging[env.value.secret].secret_id
              version = env.value.version
            }
          }
        }
      }

      startup_probe {
        initial_delay_seconds = 0
        timeout_seconds       = 2
        period_seconds        = 5
        failure_threshold     = 24
        http_get {
          path = "/actuator/health/liveness"
          port = 8080
        }
      }

      liveness_probe {
        initial_delay_seconds = 0
        timeout_seconds       = 2
        period_seconds        = 30
        failure_threshold     = 3
        http_get {
          path = "/actuator/health/liveness"
          port = 8080
        }
      }

      readiness_probe {
        timeout_seconds   = 3
        period_seconds    = 10
        success_threshold = 1
        failure_threshold = 3
        http_get {
          path = "/actuator/health/readiness"
          port = 8080
        }
      }
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]

    precondition {
      condition     = length(local.redis_tls_ca_certificates) > 0 && length(local.redis_tls_ca_certificates) <= 32768
      error_message = "Memorystore must expose a non-empty active CA set that fits in one Cloud Run environment variable."
    }
  }

  depends_on = [
    google_project_service.required["run.googleapis.com"],
    google_secret_manager_secret_iam_member.runtime_access,
  ]
}

resource "google_cloud_run_v2_job" "migrate" {
  count = var.enable_cloud_run_runtime ? 1 : 0

  project             = var.project_id
  name                = local.cloud_run_names.migrate
  location            = var.region
  deletion_protection = true

  template {
    task_count  = 1
    parallelism = 1

    template {
      service_account       = google_service_account.staging["migrator"].email
      timeout               = "600s"
      max_retries           = 0
      execution_environment = "EXECUTION_ENVIRONMENT_GEN2"

      vpc_access {
        egress = "PRIVATE_RANGES_ONLY"
        network_interfaces {
          network    = google_compute_network.staging.id
          subnetwork = google_compute_subnetwork.staging.id
        }
      }

      containers {
        name  = "migrate"
        image = local.runtime_images.core_api

        resources {
          limits = {
            cpu    = "1"
            memory = "512Mi"
          }
        }

        dynamic "env" {
          for_each = local.migration_environment
          content {
            name  = env.key
            value = env.value
          }
        }

        env {
          name = "DB_PASSWORD"
          value_source {
            secret_key_ref {
              secret  = google_secret_manager_secret.staging["db_migrator_password"].secret_id
              version = var.secret_versions.db_migrator_password
            }
          }
        }
      }
    }
  }

  lifecycle {
    ignore_changes = [template[0].template[0].containers[0].image]
  }

  depends_on = [
    google_project_service.required["run.googleapis.com"],
    google_secret_manager_secret_iam_member.runtime_access,
  ]
}

resource "google_cloud_run_v2_service" "web" {
  count = var.enable_cloud_run_runtime ? 1 : 0

  project             = var.project_id
  name                = local.cloud_run_names.web
  location            = var.region
  deletion_protection = true
  ingress             = "INGRESS_TRAFFIC_ALL"

  template {
    service_account                  = google_service_account.staging["web"].email
    timeout                          = "30s"
    execution_environment            = "EXECUTION_ENVIRONMENT_GEN2"
    max_instance_request_concurrency = 40

    scaling {
      min_instance_count = 0
      max_instance_count = 2
    }

    containers {
      name  = "web"
      image = local.runtime_images.web

      ports {
        name           = "http1"
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle          = true
        startup_cpu_boost = true
      }

      dynamic "env" {
        for_each = local.web_environment
        content {
          name  = env.key
          value = env.value
        }
      }

      startup_probe {
        initial_delay_seconds = 0
        timeout_seconds       = 2
        period_seconds        = 5
        failure_threshold     = 24
        http_get {
          path = "/login"
          port = 8080
        }
      }

      liveness_probe {
        initial_delay_seconds = 0
        timeout_seconds       = 2
        period_seconds        = 30
        failure_threshold     = 3
        http_get {
          path = "/login"
          port = 8080
        }
      }

      readiness_probe {
        timeout_seconds   = 2
        period_seconds    = 10
        success_threshold = 1
        failure_threshold = 3
        http_get {
          path = "/login"
          port = 8080
        }
      }
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }

  lifecycle {
    ignore_changes = [template[0].containers[0].image]
  }

  depends_on = [google_project_service.required["run.googleapis.com"]]
}

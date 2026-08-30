locals {
  infra_project_roles = toset([
    "roles/artifactregistry.admin",
    "roles/cloudsql.admin",
    "roles/compute.networkAdmin",
    "roles/iam.roleViewer",
    "roles/iam.serviceAccountAdmin",
    "roles/iam.workloadIdentityPoolAdmin",
    "roles/redis.admin",
    "roles/resourcemanager.projectIamAdmin",
    "roles/run.admin",
    "roles/servicenetworking.networksAdmin",
    "roles/serviceusage.serviceUsageAdmin",
  ])

  secret_access_grants = {
    ai_ai_internal_key = {
      service_account = "ai"
      secret          = "ai_internal_key"
    }
    ai_openai_api_key = {
      service_account = "ai"
      secret          = "openai_api_key"
    }
    core_ai_internal_key = {
      service_account = "core"
      secret          = "ai_internal_key"
    }
    core_db_app_password = {
      service_account = "core"
      secret          = "db_app_password"
    }
    core_dependency_health_key = {
      service_account = "core"
      secret          = "dependency_health_key"
    }
    core_google_client_secret = {
      service_account = "core"
      secret          = "google_client_secret"
    }
    core_jwt_secret = {
      service_account = "core"
      secret          = "jwt_secret"
    }
    core_oauth_cookie_secret = {
      service_account = "core"
      secret          = "oauth_cookie_secret"
    }
    core_rate_limit_key = {
      service_account = "core"
      secret          = "rate_limit_key"
    }
    core_smtp_password = {
      service_account = "core"
      secret          = "smtp_password"
    }
    deployer_dependency_health_key = {
      service_account = "deployer"
      secret          = "dependency_health_key"
    }
    migrator_db_password = {
      service_account = "migrator"
      secret          = "db_migrator_password"
    }
  }

  runtime_service_account_user_grants = {
    deployer_ai = {
      actor   = "deployer"
      runtime = "ai"
    }
    deployer_core = {
      actor   = "deployer"
      runtime = "core"
    }
    deployer_migrator = {
      actor   = "deployer"
      runtime = "migrator"
    }
    deployer_web = {
      actor   = "deployer"
      runtime = "web"
    }
    infra_ai = {
      actor   = "infra"
      runtime = "ai"
    }
    infra_core = {
      actor   = "infra"
      runtime = "core"
    }
    infra_migrator = {
      actor   = "infra"
      runtime = "migrator"
    }
    infra_web = {
      actor   = "infra"
      runtime = "web"
    }
  }
}

resource "google_project_iam_custom_role" "secret_metadata_admin" {
  project     = var.project_id
  role_id     = "offertrackStgSecretMetadataAdmin"
  title       = "OfferTrack staging secret metadata admin"
  description = "Manage staging Secret Manager containers and their IAM policies without accessing secret versions."
  stage       = "GA"

  permissions = [
    "secretmanager.locations.get",
    "secretmanager.locations.list",
    "secretmanager.secrets.create",
    "secretmanager.secrets.delete",
    "secretmanager.secrets.get",
    "secretmanager.secrets.getIamPolicy",
    "secretmanager.secrets.list",
    "secretmanager.secrets.setIamPolicy",
    "secretmanager.secrets.update",
  ]

  deletion_policy = "PREVENT"

  depends_on = [google_project_service.required["iam.googleapis.com"]]
}

resource "google_project_iam_member" "infra_project_roles" {
  for_each = local.infra_project_roles

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.staging["infra"].email}"

  depends_on = [google_project_service.required["cloudresourcemanager.googleapis.com"]]
}

resource "google_project_iam_member" "infra_secret_metadata_admin" {
  project = var.project_id
  role    = google_project_iam_custom_role.secret_metadata_admin.name
  member  = "serviceAccount:${google_service_account.staging["infra"].email}"

  depends_on = [google_project_service.required["cloudresourcemanager.googleapis.com"]]
}

resource "google_secret_manager_secret_iam_member" "runtime_access" {
  for_each = local.secret_access_grants

  project   = var.project_id
  secret_id = google_secret_manager_secret.staging[each.value.secret].secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.staging[each.value.service_account].email}"
}

resource "google_artifact_registry_repository_iam_member" "deployer_writer" {
  project    = var.project_id
  location   = google_artifact_registry_repository.offertrack.location
  repository = google_artifact_registry_repository.offertrack.repository_id
  role       = "roles/artifactregistry.writer"
  member     = "serviceAccount:${google_service_account.staging["deployer"].email}"
}

resource "google_service_account_iam_member" "runtime_service_account_user" {
  for_each = local.runtime_service_account_user_grants

  service_account_id = google_service_account.staging[each.value.runtime].name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.staging[each.value.actor].email}"
}

resource "google_cloud_run_v2_service_iam_member" "deployer_service_developer" {
  for_each = var.enable_cloud_run_runtime ? {
    ai   = google_cloud_run_v2_service.ai[0].name
    core = google_cloud_run_v2_service.core[0].name
    web  = google_cloud_run_v2_service.web[0].name
  } : {}

  project  = var.project_id
  location = var.region
  name     = each.value
  role     = "roles/run.developer"
  member   = "serviceAccount:${google_service_account.staging["deployer"].email}"
}

resource "google_cloud_run_v2_job_iam_member" "deployer_job_developer" {
  count = var.enable_cloud_run_runtime ? 1 : 0

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_job.migrate[0].name
  role     = "roles/run.developer"
  member   = "serviceAccount:${google_service_account.staging["deployer"].email}"
}

resource "google_cloud_run_v2_job_iam_member" "deployer_job_executor" {
  count = var.enable_cloud_run_runtime ? 1 : 0

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_job.migrate[0].name
  role     = "roles/run.jobsExecutor"
  member   = "serviceAccount:${google_service_account.staging["deployer"].email}"
}

resource "google_cloud_run_v2_service_iam_member" "ai_invoker" {
  for_each = var.enable_cloud_run_runtime ? toset(["core", "deployer"]) : toset([])

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.ai[0].name
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.staging[each.value].email}"
}

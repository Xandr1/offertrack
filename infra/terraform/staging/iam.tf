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
    migrator_db_password = {
      service_account = "migrator"
      secret          = "db_migrator_password"
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

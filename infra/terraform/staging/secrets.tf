locals {
  secret_ids = {
    ai_internal_key       = "offertrack-stg-ai-internal-key"
    db_app_password       = "offertrack-stg-db-app-password"
    db_migrator_password  = "offertrack-stg-db-migrator-password"
    dependency_health_key = "offertrack-stg-dependency-health-key"
    google_client_secret  = "offertrack-stg-google-client-secret"
    jwt_secret            = "offertrack-stg-jwt-secret"
    oauth_cookie_secret   = "offertrack-stg-oauth-cookie-secret"
    openai_api_key        = "offertrack-stg-openai-api-key"
    rate_limit_key        = "offertrack-stg-rate-limit-key-secret"
    smtp_password         = "offertrack-stg-smtp-password"
  }
}

resource "google_secret_manager_secret" "staging" {
  for_each = local.secret_ids

  project   = var.project_id
  secret_id = each.value
  labels    = local.common_labels

  replication {
    user_managed {
      replicas {
        location = var.region
      }
    }
  }

  depends_on = [google_project_service.required["secretmanager.googleapis.com"]]
}

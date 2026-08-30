resource "google_iam_workload_identity_pool" "github" {
  project                   = var.project_id
  workload_identity_pool_id = "offertrack-github"
  display_name              = "OfferTrack GitHub Actions"
  description               = "Federated identities for the OfferTrack GitHub repository."

  depends_on = [google_project_service.required["iam.googleapis.com"]]
}

resource "google_iam_workload_identity_pool_provider" "github" {
  project                            = var.project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github-actions"
  display_name                       = "OfferTrack GitHub Actions"
  description                        = "GitHub OIDC restricted to the immutable OfferTrack repository and owner IDs on main."

  attribute_mapping = {
    "google.subject"                = "assertion.sub"
    "attribute.repository_id"       = "assertion.repository_id"
    "attribute.repository_owner_id" = "assertion.repository_owner_id"
    "attribute.repository"          = "assertion.repository"
    "attribute.repository_owner"    = "assertion.repository_owner"
    "attribute.ref"                 = "assertion.ref"
    "attribute.ref_type"            = "assertion.ref_type"
  }

  attribute_condition = <<-EOT
    assertion.repository_id == "1252789489" &&
    assertion.repository_owner_id == "15733165" &&
    assertion.ref == "refs/heads/main" &&
    assertion.ref_type == "branch" &&
    assertion.repository == "Xandr1/offertrack" &&
    assertion.repository_owner == "Xandr1"
  EOT

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com/"
  }

  depends_on = [
    google_project_service.required["iamcredentials.googleapis.com"],
    google_project_service.required["sts.googleapis.com"],
  ]
}

resource "google_service_account_iam_member" "github_deployer_impersonation" {
  service_account_id = google_service_account.staging["deployer"].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/projects/${var.project_number}/locations/global/workloadIdentityPools/${google_iam_workload_identity_pool.github.workload_identity_pool_id}/attribute.repository_id/1252789489"

  depends_on = [google_iam_workload_identity_pool_provider.github]
}

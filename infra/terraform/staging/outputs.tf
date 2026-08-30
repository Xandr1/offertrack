output "project_id" {
  description = "The staging GCP project ID."
  value       = var.project_id
}

output "project_number" {
  description = "The staging GCP project number."
  value       = var.project_number
}

output "region" {
  description = "The staging GCP region."
  value       = var.region
}

output "zone" {
  description = "The staging GCP zone used for zonal resources."
  value       = var.zone
}

output "artifact_registry_repository" {
  description = "The fully qualified Artifact Registry repository resource name."
  value       = google_artifact_registry_repository.offertrack.name
}

output "artifact_registry_repository_url" {
  description = "The Docker repository URL prefix for staging image publication."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.offertrack.repository_id}"
}

output "vpc_name" {
  description = "The staging VPC name."
  value       = google_compute_network.staging.name
}

output "subnet_name" {
  description = "The staging regional subnet name."
  value       = google_compute_subnetwork.staging.name
}

output "cloud_sql_instance_name" {
  description = "The staging Cloud SQL instance name."
  value       = google_sql_database_instance.postgres.name
}

output "cloud_sql_private_ip" {
  description = "The private Cloud SQL endpoint used by Core and the migration job."
  value       = google_sql_database_instance.postgres.private_ip_address
  sensitive   = true
}

output "database_name" {
  description = "The application database name."
  value       = google_sql_database.offertrack.name
}

output "redis_host" {
  description = "The private TLS Memorystore endpoint used by Core."
  value       = google_redis_instance.staging.host
  sensitive   = true
}

output "redis_port" {
  description = "The TLS-enabled Memorystore endpoint port returned by GCP."
  value       = google_redis_instance.staging.port
}

output "redis_tls_enabled" {
  description = "Whether the Memorystore instance requires TLS."
  value       = google_redis_instance.staging.transit_encryption_mode == "SERVER_AUTHENTICATION"
}

output "redis_server_ca_certificates" {
  description = "Active Memorystore server CA certificates for the future runtime trust configuration."
  value       = [for server_ca in google_redis_instance.staging.server_ca_certs : server_ca.cert]
  sensitive   = true
}

output "secret_resource_names" {
  description = "Secret Manager container resource names; no secret values are exposed."
  value       = { for key, secret in google_secret_manager_secret.staging : key => secret.name }
}

output "runtime_service_account_emails" {
  description = "Runtime service account emails used by the Cloud Run resources."
  value = {
    ai       = google_service_account.staging["ai"].email
    core     = google_service_account.staging["core"].email
    migrator = google_service_account.staging["migrator"].email
    web      = google_service_account.staging["web"].email
  }
}

output "infra_service_account_email" {
  description = "The privileged Terraform service account email; GitHub cannot impersonate it in this checkpoint."
  value       = google_service_account.staging["infra"].email
}

output "deployer_service_account_email" {
  description = "The narrowly scoped staging application deployer service account email."
  value       = google_service_account.staging["deployer"].email
}

output "workload_identity_provider_resource_name" {
  description = "The GitHub Workload Identity provider resource name for future deployer authentication."
  value       = google_iam_workload_identity_pool_provider.github.name
}

output "ai_service_url" {
  description = "Canonical deterministic URL used for the private AI service and its ID-token audience."
  value       = local.ai_service_url
}

output "core_service_url" {
  description = "Canonical deterministic public Core API URL compiled into the staging Web image."
  value       = local.core_service_url
}

output "web_service_url" {
  description = "Canonical deterministic public staging Web URL used by Core CORS and cookie configuration."
  value       = local.web_service_url
}

output "cloud_run_reported_uris" {
  description = "Cloud Run API-reported service URIs for operational verification against the configured canonical URLs."
  value = {
    ai   = try(google_cloud_run_v2_service.ai[0].uri, null)
    core = try(google_cloud_run_v2_service.core[0].uri, null)
    web  = try(google_cloud_run_v2_service.web[0].uri, null)
  }
}

output "migration_job_name" {
  description = "Name of the independently deployable one-shot Flyway migration job."
  value       = try(google_cloud_run_v2_job.migrate[0].name, null)
}

output "cloud_run_resource_names" {
  description = "Non-secret Cloud Run resource identifiers used by deployment automation."
  value = {
    ai_service   = try(google_cloud_run_v2_service.ai[0].id, null)
    core_service = try(google_cloud_run_v2_service.core[0].id, null)
    migration    = try(google_cloud_run_v2_job.migrate[0].id, null)
    web_service  = try(google_cloud_run_v2_service.web[0].id, null)
  }
}

output "terraform_seed_images" {
  description = "Immutable image digests used to create the resources; later image-only revisions are intentionally ignored by Terraform."
  value       = var.initial_images
}

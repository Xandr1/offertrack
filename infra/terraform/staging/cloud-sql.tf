resource "google_sql_database_instance" "postgres" {
  project             = var.project_id
  name                = "offertrack-stg-postgres"
  region              = var.region
  database_version    = "POSTGRES_16"
  deletion_protection = true

  settings {
    tier                        = var.cloud_sql_tier
    edition                     = "ENTERPRISE"
    availability_type           = "ZONAL"
    disk_type                   = "PD_SSD"
    disk_size                   = var.cloud_sql_disk_size_gb
    disk_autoresize             = true
    disk_autoresize_limit       = var.cloud_sql_disk_autoresize_limit_gb
    deletion_protection_enabled = true

    location_preference {
      zone = var.zone
    }

    backup_configuration {
      enabled                        = true
      start_time                     = "02:00"
      location                       = var.region
      point_in_time_recovery_enabled = true
      transaction_log_retention_days = 3

      backup_retention_settings {
        retained_backups = 7
        retention_unit   = "COUNT"
      }
    }

    ip_configuration {
      ipv4_enabled       = false
      private_network    = google_compute_network.staging.id
      allocated_ip_range = google_compute_global_address.private_services.name
    }

    user_labels = local.common_labels
  }

  lifecycle {
    # Cloud SQL updates disk_size after API-driven automatic growth. Ignoring only
    # that field prevents Terraform from attempting an unsupported disk shrink.
    ignore_changes = [settings[0].disk_size]
  }

  depends_on = [
    google_project_service.required["sqladmin.googleapis.com"],
    google_service_networking_connection.private_services,
  ]
}

resource "google_sql_database" "offertrack" {
  project         = var.project_id
  name            = "offertrack"
  instance        = google_sql_database_instance.postgres.name
  deletion_policy = "PREVENT"
}

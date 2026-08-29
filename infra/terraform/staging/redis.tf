resource "google_redis_instance" "staging" {
  project                 = var.project_id
  name                    = "offertrack-stg-redis"
  display_name            = "OfferTrack staging Redis"
  region                  = var.region
  location_id             = var.zone
  tier                    = "BASIC"
  memory_size_gb          = var.redis_memory_size_gb
  redis_version           = "REDIS_7_2"
  authorized_network      = google_compute_network.staging.id
  connect_mode            = "PRIVATE_SERVICE_ACCESS"
  reserved_ip_range       = google_compute_global_address.private_services.name
  auth_enabled            = false
  transit_encryption_mode = "SERVER_AUTHENTICATION"
  deletion_protection     = false

  redis_configs = {
    "maxmemory-policy" = "noeviction"
  }

  depends_on = [
    google_project_service.required["redis.googleapis.com"],
    google_service_networking_connection.private_services,
  ]
}

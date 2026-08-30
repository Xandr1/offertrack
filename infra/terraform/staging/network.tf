resource "google_compute_network" "staging" {
  project                 = var.project_id
  name                    = "offertrack-staging-vpc"
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"

  depends_on = [google_project_service.required["compute.googleapis.com"]]
}

resource "google_compute_subnetwork" "staging" {
  project                  = var.project_id
  name                     = "offertrack-staging-subnet"
  region                   = var.region
  network                  = google_compute_network.staging.id
  ip_cidr_range            = "10.20.0.0/24"
  private_ip_google_access = true
}

resource "google_compute_global_address" "private_services" {
  project       = var.project_id
  name          = "google-managed-services-offertrack-staging-vpc"
  address       = "10.20.4.0"
  prefix_length = 22
  address_type  = "INTERNAL"
  purpose       = "VPC_PEERING"
  network       = google_compute_network.staging.id
}

resource "google_service_networking_connection" "private_services" {
  network                 = google_compute_network.staging.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_services.name]

  depends_on = [google_project_service.required["servicenetworking.googleapis.com"]]
}

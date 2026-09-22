locals {
  ai_egress_tag = "offertrack-stg-ai-egress"
  ai_non_public_ipv4_ranges = [
    "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
    "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24",
    "192.88.99.0/24", "192.168.0.0/16",
    "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24", "224.0.0.0/4",
    "240.0.0.0/4",
  ]
}

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

resource "google_compute_router" "ai_egress" {
  project = var.project_id
  name    = "offertrack-stg-ai-egress"
  region  = var.region
  network = google_compute_network.staging.id
}

resource "google_compute_router_nat" "ai_egress" {
  project                            = var.project_id
  name                               = "offertrack-stg-ai-egress"
  region                             = var.region
  router                             = google_compute_router.ai_egress.name
  type                               = "PUBLIC"
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "LIST_OF_SUBNETWORKS"
  endpoint_types                     = ["ENDPOINT_TYPE_VM"]
  min_ports_per_vm                   = 64

  subnetwork {
    name                    = google_compute_subnetwork.staging.id
    source_ip_ranges_to_nat = ["PRIMARY_IP_RANGE"]
  }
}

# Cloud Run platform DNS/metadata traffic is outside ordinary VPC firewall
# enforcement. The fetcher's metadata rejection and pinned IPs remain essential.
resource "google_compute_firewall" "ai_deny_non_public" {
  project            = var.project_id
  name               = "offertrack-stg-ai-deny-non-public"
  network            = google_compute_network.staging.id
  direction          = "EGRESS"
  priority           = 900
  target_tags        = [local.ai_egress_tag]
  destination_ranges = local.ai_non_public_ipv4_ranges
  deny {
    protocol = "all"
  }
}

resource "google_compute_firewall" "ai_allow_web" {
  project            = var.project_id
  name               = "offertrack-stg-ai-allow-web"
  network            = google_compute_network.staging.id
  direction          = "EGRESS"
  priority           = 1000
  target_tags        = [local.ai_egress_tag]
  destination_ranges = ["0.0.0.0/0"]
  allow {
    protocol = "tcp"
    ports    = ["80", "443"]
  }
}

resource "google_compute_firewall" "ai_deny_other" {
  project            = var.project_id
  name               = "offertrack-stg-ai-deny-other"
  network            = google_compute_network.staging.id
  direction          = "EGRESS"
  priority           = 1100
  target_tags        = [local.ai_egress_tag]
  destination_ranges = ["0.0.0.0/0"]
  deny {
    protocol = "all"
  }
}

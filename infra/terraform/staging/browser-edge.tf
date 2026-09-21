# One global HTTPS edge retains europe-central2. No credentials or CORS policy live here.
locals {
  browser_services = {
    core = { name = local.cloud_run_names.core_api, host = local.core_service_host, probes = ["/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness", "/actuator/health/dependencies"] }
    web  = { name = local.cloud_run_names.web, host = local.web_service_host, probes = ["/login"] }
  }
  browser_backends = var.enable_cloud_run_runtime ? {
    for entry in flatten([
      for service, config in local.browser_services : [
        for track in ["stable", "candidate"] : { key = "${service}-${track}", service = service, name = config.name, tag = track == "candidate" ? "candidate" : null }
      ]
    ]) : entry.key => entry
  } : {}
}

resource "google_compute_global_address" "browser" {
  project      = var.project_id
  name         = "offertrack-stg-browser"
  address_type = "EXTERNAL"
  ip_version   = "IPV4"
}

resource "google_compute_managed_ssl_certificate" "browser" {
  project = var.project_id
  name    = "offertrack-stg-browser"
  managed {
    domains = [local.web_service_host, local.core_service_host]
  }
  lifecycle {
    create_before_destroy = true
  }
}

resource "google_compute_ssl_policy" "browser" {
  project         = var.project_id
  name            = "offertrack-stg-browser"
  profile         = "MODERN"
  min_tls_version = "TLS_1_2"
}

resource "google_compute_region_network_endpoint_group" "browser" {
  for_each              = local.browser_backends
  project               = var.project_id
  name                  = "offertrack-stg-${each.key}"
  region                = var.region
  network_endpoint_type = "SERVERLESS"
  cloud_run {
    service = each.value.name
    tag     = each.value.tag
  }
  depends_on = [google_cloud_run_v2_service.core, google_cloud_run_v2_service.web]
}

resource "google_compute_backend_service" "browser" {
  for_each              = local.browser_backends
  project               = var.project_id
  name                  = "offertrack-stg-${each.key}"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  protocol              = "HTTP"
  enable_cdn            = false
  # Recreate the chain from edge variables, replacing caller-supplied values.
  custom_request_headers = [
    "X-Forwarded-For: {client_ip_address},{server_ip_address}",
    "X-Forwarded-Proto: https",
  ]
  backend {
    group = google_compute_region_network_endpoint_group.browser[each.key].id
  }
  log_config {
    enable      = true
    sample_rate = 1
  }
}

resource "google_compute_url_map" "browser" {
  count           = var.enable_cloud_run_runtime ? 1 : 0
  project         = var.project_id
  name            = "offertrack-stg-browser"
  default_service = google_compute_backend_service.browser["core-stable"].id
  # Unknown hosts never reach an application endpoint.
  default_route_action {
    fault_injection_policy {
      abort {
        http_status = 503
        percentage  = 100
      }
    }
  }
  header_action {
    request_headers_to_remove = ["Forwarded", "X-Forwarded-Host", "X-Forwarded-Port", "X-Forwarded-Prefix", "X-Real-IP"]
  }
  dynamic "host_rule" {
    for_each = local.browser_services
    content {
      hosts        = [host_rule.value.host]
      path_matcher = host_rule.key
    }
  }
  dynamic "path_matcher" {
    for_each = local.browser_services
    content {
      name            = path_matcher.key
      default_service = google_compute_backend_service.browser["${path_matcher.key}-stable"].id
      # This public header is a routing selector only. It conveys no authority.
      route_rules {
        priority = 10
        service  = google_compute_backend_service.browser["${path_matcher.key}-candidate"].id
        dynamic "match_rules" {
          for_each = path_matcher.value.probes
          content {
            full_path_match = match_rules.value
            header_matches {
              header_name = ":method"
              exact_match = "GET"
            }
            header_matches {
              header_name = "X-OfferTrack-Route"
              exact_match = "candidate"
            }
          }
        }
      }
      route_rules {
        priority = 20
        service  = google_compute_backend_service.browser["${path_matcher.key}-stable"].id
        dynamic "match_rules" {
          for_each = path_matcher.value.probes
          content {
            full_path_match = match_rules.value
            header_matches {
              header_name = ":method"
              exact_match = "GET"
            }
          }
        }
      }
      dynamic "route_rules" {
        for_each = var.auth_maintenance_enabled ? [true] : []
        content {
          priority = 1000
          service  = google_compute_backend_service.browser["core-stable"].id
          match_rules {
            prefix_match = "/"
          }
          route_action {
            fault_injection_policy {
              abort {
                http_status = 503
                percentage  = 100
              }
            }
          }
        }
      }
    }
  }
}

resource "google_compute_target_https_proxy" "browser" {
  count            = var.enable_cloud_run_runtime ? 1 : 0
  project          = var.project_id
  name             = "offertrack-stg-browser"
  url_map          = google_compute_url_map.browser[0].id
  ssl_certificates = [google_compute_managed_ssl_certificate.browser.id]
  ssl_policy       = google_compute_ssl_policy.browser.id
}

resource "google_compute_global_forwarding_rule" "browser" {
  count                 = var.enable_cloud_run_runtime ? 1 : 0
  project               = var.project_id
  name                  = "offertrack-stg-browser"
  ip_address            = google_compute_global_address.browser.address
  port_range            = "443"
  target                = google_compute_target_https_proxy.browser[0].id
  load_balancing_scheme = "EXTERNAL_MANAGED"
}

resource "google_logging_project_exclusion" "google_callback" {
  project     = var.project_id
  name        = "offertrack-google-callback-query"
  description = "Exclude only credential-bearing Google callback request URLs; application outcome events and ordinary requests remain logged."
  filter      = <<-FILTER
    (
      (resource.type="cloud_run_revision" AND resource.labels.service_name="${local.cloud_run_names.core_api}")
      OR
      (resource.type="http_load_balancer" AND resource.labels.url_map_name="offertrack-stg-browser")
    )
    AND httpRequest.requestUrl =~ "^https://${replace(local.core_service_host, ".", "[.]")}/login/oauth2/code/google([?].*)?$"
  FILTER
}

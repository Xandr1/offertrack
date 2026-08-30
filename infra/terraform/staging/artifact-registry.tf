resource "google_artifact_registry_repository" "offertrack" {
  project       = var.project_id
  location      = var.region
  repository_id = "offertrack"
  description   = "OfferTrack staging container images"
  format        = "DOCKER"

  docker_config {
    immutable_tags = true
  }

  depends_on = [google_project_service.required["artifactregistry.googleapis.com"]]
}

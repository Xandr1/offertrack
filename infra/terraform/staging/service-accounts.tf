locals {
  service_accounts = {
    ai = {
      account_id   = "offertrack-stg-ai"
      display_name = "OfferTrack staging AI runtime"
    }
    core = {
      account_id   = "offertrack-stg-core"
      display_name = "OfferTrack staging Core API runtime"
    }
    deployer = {
      account_id   = "offertrack-stg-deployer"
      display_name = "OfferTrack staging application deployer"
    }
    infra = {
      account_id   = "offertrack-stg-infra"
      display_name = "OfferTrack staging Terraform infrastructure"
    }
    migrator = {
      account_id   = "offertrack-stg-migrator"
      display_name = "OfferTrack staging database migrator"
    }
    web = {
      account_id   = "offertrack-stg-web"
      display_name = "OfferTrack staging Web runtime"
    }
  }
}

resource "google_service_account" "staging" {
  for_each = local.service_accounts

  project      = var.project_id
  account_id   = each.value.account_id
  display_name = each.value.display_name
  description  = "Managed by the OfferTrack staging Terraform foundation."

  depends_on = [google_project_service.required["iam.googleapis.com"]]
}

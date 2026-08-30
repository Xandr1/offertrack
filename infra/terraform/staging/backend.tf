terraform {
  backend "gcs" {
    bucket = "offertrack-staging-tfstate-2908"
    prefix = "staging"
  }
}

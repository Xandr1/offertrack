variable "project_id" {
  description = "The existing GCP staging project ID."
  type        = string
  default     = "offertrack-staging"

  validation {
    condition     = var.project_id == "offertrack-staging"
    error_message = "This Terraform root may target only offertrack-staging."
  }
}

variable "project_number" {
  description = "The existing GCP staging project number used in IAM principal identifiers."
  type        = string
  default     = "765846644391"

  validation {
    condition     = var.project_number == "765846644391"
    error_message = "This Terraform root may target only project number 765846644391."
  }
}

variable "region" {
  description = "The GCP region for staging resources."
  type        = string
  default     = "europe-central2"

  validation {
    condition     = var.region == "europe-central2"
    error_message = "Staging resources must remain in europe-central2."
  }
}

variable "zone" {
  description = "The single staging zone for zonal managed resources."
  type        = string
  default     = "europe-central2-a"

  validation {
    condition     = var.zone == "europe-central2-a"
    error_message = "Staging zonal resources must remain in europe-central2-a."
  }
}

variable "cloud_sql_tier" {
  description = "The cost-sensitive Cloud SQL machine tier to review before apply."
  type        = string
  default     = "db-g1-small"

  validation {
    condition     = length(trimspace(var.cloud_sql_tier)) > 0
    error_message = "cloud_sql_tier must not be empty."
  }
}

variable "cloud_sql_disk_size_gb" {
  description = "The initial Cloud SQL SSD capacity in GiB."
  type        = number
  default     = 10

  validation {
    condition     = var.cloud_sql_disk_size_gb >= 10 && floor(var.cloud_sql_disk_size_gb) == var.cloud_sql_disk_size_gb
    error_message = "cloud_sql_disk_size_gb must be an integer of at least 10."
  }
}

variable "cloud_sql_disk_autoresize_limit_gb" {
  description = "The maximum Cloud SQL SSD capacity in GiB after automatic growth."
  type        = number
  default     = 50

  validation {
    condition     = var.cloud_sql_disk_autoresize_limit_gb >= var.cloud_sql_disk_size_gb && floor(var.cloud_sql_disk_autoresize_limit_gb) == var.cloud_sql_disk_autoresize_limit_gb
    error_message = "cloud_sql_disk_autoresize_limit_gb must be an integer at least as large as cloud_sql_disk_size_gb."
  }
}

variable "redis_memory_size_gb" {
  description = "The cost-sensitive Memorystore capacity in GiB."
  type        = number
  default     = 1

  validation {
    condition     = var.redis_memory_size_gb >= 1 && floor(var.redis_memory_size_gb) == var.redis_memory_size_gb
    error_message = "redis_memory_size_gb must be a positive integer."
  }
}

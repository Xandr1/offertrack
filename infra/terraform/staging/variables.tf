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

variable "enable_cloud_run_runtime" {
  description = "Creates the Cloud Run services/job after foundation, secret versions, DB roles, and seed image digests are ready."
  type        = bool
  default     = false
}

variable "initial_images" {
  description = "Initial immutable Artifact Registry image digests used when Terraform first creates the Cloud Run resources. Subsequent image-only deployments are managed by the staging workflow."
  type = object({
    ai       = string
    core_api = string
    web      = string
  })
  default  = null
  nullable = true

  validation {
    condition = (
      var.initial_images == null || (
        can(regex("^europe-central2-docker\\.pkg\\.dev/offertrack-staging/offertrack/ai-service@sha256:[0-9a-f]{64}$", var.initial_images.ai)) &&
        can(regex("^europe-central2-docker\\.pkg\\.dev/offertrack-staging/offertrack/core-api@sha256:[0-9a-f]{64}$", var.initial_images.core_api)) &&
        can(regex("^europe-central2-docker\\.pkg\\.dev/offertrack-staging/offertrack/web@sha256:[0-9a-f]{64}$", var.initial_images.web))
      )
    )
    error_message = "initial_images must contain exact staging Artifact Registry image digest references for ai-service, core-api, and web."
  }

  validation {
    condition     = !var.enable_cloud_run_runtime || var.initial_images != null
    error_message = "initial_images is required when enable_cloud_run_runtime is true."
  }
}

variable "secret_versions" {
  description = "Pinned numeric Secret Manager versions used by Cloud Run. Required only when the runtime is enabled; values are version identifiers, never secret data."
  type = object({
    ai_internal_key       = string
    db_app_password       = string
    db_migrator_password  = string
    dependency_health_key = string
    google_client_secret  = string
    jwt_secret            = string
    oauth_cookie_secret   = string
    openai_api_key        = string
    rate_limit_key        = string
    smtp_password         = string
  })
  default  = null
  nullable = true

  validation {
    condition = var.secret_versions == null ? true : alltrue([
      for version in values(var.secret_versions) : can(regex("^[1-9][0-9]*$", version))
    ])
    error_message = "Every secret_versions value must be a pinned positive numeric Secret Manager version."
  }

  validation {
    condition     = !var.enable_cloud_run_runtime || var.secret_versions != null
    error_message = "secret_versions is required when enable_cloud_run_runtime is true."
  }
}

variable "google_oauth_client_id" {
  description = "Public Google OAuth client ID for the staging Core service."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = !var.enable_cloud_run_runtime || (var.google_oauth_client_id != null && length(trimspace(var.google_oauth_client_id)) >= 16 && !strcontains(lower(var.google_oauth_client_id), "change-me"))
    error_message = "google_oauth_client_id must be an explicit non-placeholder client ID."
  }
}

variable "smtp_host" {
  description = "Non-secret SMTP hostname used by staging Core."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = !var.enable_cloud_run_runtime || (var.smtp_host != null && can(regex("^(?i:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)(?:\\.(?i:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?))+$", trimspace(var.smtp_host))))
    error_message = "smtp_host must be an explicit non-loopback fully qualified hostname."
  }
}

variable "smtp_port" {
  description = "SMTP TCP port used by staging Core."
  type        = number
  default     = null
  nullable    = true

  validation {
    condition     = !var.enable_cloud_run_runtime || (var.smtp_port != null && var.smtp_port >= 1 && var.smtp_port <= 65535 && floor(var.smtp_port) == var.smtp_port)
    error_message = "smtp_port must be an integer TCP port."
  }
}

variable "smtp_username" {
  description = "Non-secret SMTP username used by staging Core."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = !var.enable_cloud_run_runtime || (var.smtp_username != null && length(trimspace(var.smtp_username)) > 0)
    error_message = "smtp_username must not be empty because the runtime references an SMTP password secret."
  }
}

variable "mail_from" {
  description = "From address used for staging transactional email."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = !var.enable_cloud_run_runtime || (var.mail_from != null && can(regex("^[^[:space:]@]+@[^[:space:]@]+$", trimspace(var.mail_from))))
    error_message = "mail_from must be an explicit email address."
  }
}

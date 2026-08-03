variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "zone_name" {
  description = "Your existing Route 53 public hosted zone, e.g. \"ark.com\". Must already exist — see modules/dns."
  type        = string
}

variable "domain_name" {
  description = "The API's public custom domain, e.g. \"api.ark.com\"."
  type        = string
}

variable "new_relic_license_key" {
  description = "New Relic Java agent license key. Optional — leave unset to skip New Relic entirely. Supply via TF_VAR_new_relic_license_key, never a committed .tfvars file. See modules/ecs's variable of the same name."
  type        = string
  default     = null
  sensitive   = true
}
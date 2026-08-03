variable "zone_name" {
  description = "The Route 53 public hosted zone that already exists for your domain (e.g. \"ark.com\"). This module looks it up rather than creating it — provisioning a new hosted zone means re-pointing domain registrar nameservers, a manual one-time step that doesn't belong in a per-environment apply."
  type        = string
}

variable "domain_name" {
  description = "The full custom domain to issue a certificate for, e.g. \"api.ark.com\"."
  type        = string
}

variable "tags" {
  type    = map(string)
  default = {}
}
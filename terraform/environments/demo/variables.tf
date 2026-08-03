variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "image_tag" {
  description = "Backend image tag to deploy (commit SHA). Passed through to the ecs module — see scripts/deploy-backend.sh, which builds/pushes the image, applies with this var to register a new task definition revision, then force-deploys the ECS service onto it (Terraform ignores task_definition drift on the service itself — see modules/ecs/main.tf)."
  type        = string
  default     = "REPLACE_WITH_COMMIT_SHA"
}

variable "new_relic_license_key" {
  description = "New Relic Java agent license key. Optional — leave unset to skip New Relic entirely. Supply via TF_VAR_new_relic_license_key, never a committed .tfvars file. See modules/ecs's variable of the same name."
  type        = string
  default     = null
  sensitive   = true
}
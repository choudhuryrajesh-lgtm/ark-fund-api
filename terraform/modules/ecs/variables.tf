variable "name" {
  description = "AWS resource name prefix, e.g. \"ark-fund-production\"."
  type        = string
}

variable "environment" {
  description = "Spring profile / log group / secret path slug, e.g. \"production\" or \"staging\"."
  type        = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

variable "target_group_arn" {
  type = string
}

variable "alb_arn_suffix" {
  description = "From the alb module — needed to build the ALBRequestCountPerTarget resource_label for autoscaling."
  type        = string
}

variable "target_group_arn_suffix" {
  type = string
}

variable "ecr_repository_url" {
  type = string
}

variable "image_tag" {
  description = "Immutable image tag to deploy (commit SHA). Terraform ignores drift on this after the first apply — see the lifecycle block on aws_ecs_service — because CI/CD (.github/workflows/ci-cd.yml) owns rolling out new task definition revisions day to day; Terraform owns the cluster/service/autoscaling shape around them."
  type        = string
  default     = "REPLACE_WITH_COMMIT_SHA"
}

variable "container_port" {
  type    = number
  default = 8083
}

variable "cpu" {
  description = "Task-level vCPU units (Fargate sizing). 512 = 0.5 vCPU staging, 1024 = 1 vCPU production — see docs/02-Capacity-Planning.md."
  type        = string
  default     = "1024"
}

variable "memory" {
  type    = string
  default = "2048"
}

variable "desired_count" {
  type    = number
  default = 6
}

variable "min_capacity" {
  description = "Autoscaling floor. 4 in production for AZ redundancy + rolling-deploy headroom (docs/06-Resiliency-Scalability.md §2)."
  type        = number
  default     = 4
}

variable "max_capacity" {
  type    = number
  default = 16
}

variable "db_url_secret_arn" {
  type = string
}

variable "db_username_secret_arn" {
  type = string
}

variable "db_password_secret_arn" {
  type = string
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "health_check_grace_period_seconds" {
  description = "How long ECS ignores ALB target-health-check failures after a task starts, so a slow-but-fine Spring Boot startup (cold JVM + New Relic agent instrumentation when enabled) isn't mistaken for a broken deployment and killed before it ever gets to serve traffic. 180s, not a tighter value: observed demo startup with the New Relic agent active took ~105-125s wall clock (75s of that just Spring context init) on 0.5 vCPU — the agent's bytecode instrumentation at premain is CPU-bound and genuinely slow at that CPU allocation, so the margin needs to be generous, not just non-zero."
  type        = number
  default     = 180
}

variable "aws_region" {
  type = string
}

variable "new_relic_license_key" {
  description = "New Relic Java agent license key. Optional — when null (the default), no New Relic secret is created, no New Relic env vars are set, and the agent (still bundled in the image, see Dockerfile) never activates. Get one free at https://newrelic.com -> account settings -> API keys -> License key. Pass via TF_VAR_new_relic_license_key or -var, never commit it to a .tfvars file that gets checked in."
  type        = string
  default     = null
  sensitive   = true
}

variable "new_relic_app_name" {
  description = "Shows up as the application name in New Relic's UI. Defaults to var.name (e.g. \"ark-fund-demo\") when not set."
  type        = string
  default     = null
}

variable "tags" {
  type    = map(string)
  default = {}
}
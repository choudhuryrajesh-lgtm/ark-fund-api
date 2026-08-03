variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  description = "Where the VPC Link's ENIs live — the same private subnets as the ALB and ECS tasks."
  type        = list(string)
}

variable "alb_security_group_id" {
  description = "So the VPC Link's own security group can be granted egress to it."
  type        = string
}

variable "alb_listener_arn" {
  description = "HTTP API private integrations target a listener ARN directly, not the load balancer itself."
  type        = string
}

variable "alb_listener_port" {
  description = "Port the ALB's listener behind alb_listener_arn actually runs on - must match modules/security-groups' alb_listener_port for the same environment (443 when alb has a certificate, 80 when it doesn't). Only used for the VPC Link's own egress rule; the integration itself already targets the right listener via alb_listener_arn regardless."
  type        = number
  default     = 443
}

variable "domain_name" {
  description = "Custom domain to map onto this API, e.g. \"api.ark.com\". Optional — when null (with certificate_arn also null), no custom domain is created and callers use the API's free auto-generated invoke URL instead (see the api_endpoint output). Used by environments/demo, which deliberately has no domain."
  type        = string
  default     = null
}

variable "certificate_arn" {
  description = "ACM certificate for the custom domain (from the dns module). Must be a REGIONAL cert in this API's own region — HTTP API custom domains don't support edge-optimized certs the way REST APIs' us-east-1-only certs do. Optional — see domain_name."
  type        = string
  default     = null
}

variable "throttle_rate_limit" {
  description = "Steady-state requests/second, per docs/06-Resiliency-Scalability.md §6 — sheds excess load here before it ever reaches an already-saturated ECS fleet."
  type        = number
  default     = 2000
}

variable "throttle_burst_limit" {
  type    = number
  default = 4000
}

variable "log_retention_days" {
  type    = number
  default = 30
}

variable "tags" {
  type    = map(string)
  default = {}
}
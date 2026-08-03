variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Subnets for the ALB's ENIs. Internal (not internet-facing — see main.tf), so these are the private app subnets, the same ones ECS tasks and the API Gateway VPC Link's ENIs live in."
  type        = list(string)
}

variable "security_group_id" {
  type = string
}

variable "certificate_arn" {
  description = "ACM certificate ARN for the HTTPS listener (from the dns module). Optional — when null, an HTTP:80 listener is created instead. Since this ALB is internal (see main.tf), plain HTTP never crosses the public internet either way; the tradeoff is only relevant for environments without a real domain/cert, e.g. environments/demo. staging/production should always pass a real cert."
  type        = string
  default     = null
}

variable "container_port" {
  type    = number
  default = 8083
}

variable "health_check_path" {
  type    = string
  default = "/actuator/health"
}

variable "tags" {
  type    = map(string)
  default = {}
}
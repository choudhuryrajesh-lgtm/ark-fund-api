variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "vpc_cidr" {
  description = "Scopes alb-sg's ingress to inside the VPC only — the ALB is internal (see modules/alb), reachable exclusively via the API Gateway VPC Link's ENIs, which live in this CIDR. Never 0.0.0.0/0 here; that would give callers a second, unthrottled way in that bypasses API Gateway entirely."
  type        = string
}

variable "alb_listener_port" {
  description = "Port the ALB's listener actually runs on — 443 when modules/alb has a certificate_arn (staging/production), 80 when it doesn't (environments/demo, see modules/alb's http/https listener count logic). Must match whichever listener alb_listener_arn points to, or the VPC Link's ENIs can reach the ALB's security group but get blocked on the wrong port — API Gateway then reports a generic 'Service Unavailable' with no indication it's a security group mismatch."
  type        = number
  default     = 443
}

variable "container_port" {
  description = "Port the API listens on inside the container. Matches server.port in application.yml (8083)."
  type        = number
  default     = 8083
}

variable "db_port" {
  type    = number
  default = 5432
}

variable "tags" {
  type    = map(string)
  default = {}
}
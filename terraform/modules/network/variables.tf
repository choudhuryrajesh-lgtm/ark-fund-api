variable "name" {
  description = "Resource name prefix, e.g. \"ark-fund-staging\"."
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR block. Matches docs/03-System-Architecture.md's 10.0.0.0/16 design."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of Availability Zones to spread subnets across. 3 in production, 2 is enough for staging. Must be >= 2 regardless of environment size: RDS requires a DB subnet group to span at least 2 AZs even when multi_az is false (it's about where a standby *could* go, not whether one currently exists) - see single_nat_gateway below for how to keep NAT cost down without dropping below that floor."
  type        = number
  default     = 3
}

variable "single_nat_gateway" {
  description = "When true, create exactly one NAT gateway (in the first public subnet) and route every private subnet's egress through it, regardless of az_count. NAT gateways are billed per-hour and are the dominant idle cost of this whole architecture; the default (false, one NAT per AZ) is the resilient/production shape, this is the cost-optimized one for short-lived environments (e.g. environments/demo) that still need >= 2 AZs of subnets for RDS."
  type        = bool
  default     = false
}

variable "tags" {
  type    = map(string)
  default = {}
}
variable "name" {
  description = "AWS resource name prefix, e.g. \"ark-fund-production\" — used for the DB instance identifier, subnet group, and tags."
  type        = string
}

variable "environment" {
  description = "Environment slug used only in the Secrets Manager path (\"ark-fund-api/<environment>/...\") — matches the path the ECS task definitions in deploy/ecs/ already reference, e.g. \"production\" or \"staging\"."
  type        = string
}

variable "subnet_ids" {
  description = "Data-tier subnet IDs (from the network module)."
  type        = list(string)
}

variable "security_group_id" {
  type = string
}

variable "instance_class" {
  description = "Sized per docs/03-System-Architecture.md §5 / docs/02-Capacity-Planning.md."
  type        = string
  default     = "db.r6g.xlarge"
}

variable "allocated_storage_gb" {
  type    = number
  default = 100
}

variable "max_allocated_storage_gb" {
  description = "Storage autoscaling ceiling — avoids a manual resize event."
  type        = number
  default     = 500
}

variable "engine_version" {
  type    = string
  default = "16"
}

variable "database_name" {
  type    = string
  default = "arkdb"
}

variable "master_username" {
  type    = string
  default = "ark"
}

variable "multi_az" {
  type    = bool
  default = true
}

variable "backup_retention_days" {
  description = "Matches docs/13-Disaster-Recovery-Failover.md §2 (max automated retention window)."
  type        = number
  default     = 35
}

variable "deletion_protection" {
  type    = bool
  default = true
}

variable "skip_final_snapshot" {
  description = "false (default) takes a final snapshot on destroy — the safe default for staging/production. Set true for genuinely throwaway environments (e.g. environments/demo) where the data has no long-term value and a fast, repeatable `terraform destroy` matters more than snapshot protection; otherwise destroy takes longer and a second destroy/apply cycle can collide on the snapshot identifier."
  type        = bool
  default     = false
}

variable "tags" {
  type    = map(string)
  default = {}
}
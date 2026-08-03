output "api_url" {
  description = "The free, auto-generated API Gateway URL — works immediately, no domain or DNS wait needed."
  value       = module.api_gateway.api_endpoint
}

output "ecs_cluster_name" {
  value = module.ecs.cluster_name
}

output "ecs_service_name" {
  value = module.ecs.service_name
}

output "ecs_task_definition_family" {
  value = module.ecs.task_definition_family
}

output "ecr_repository_url" {
  value = data.aws_ecr_repository.this.repository_url
}

output "rds_endpoint" {
  value = module.rds.endpoint
}

output "frontend_url" {
  description = "Public HTTPS URL for the React app — see scripts/deploy-frontend.sh to build and upload it here."
  value       = module.static_site.url
}

output "frontend_bucket" {
  value = module.static_site.bucket_name
}

output "frontend_distribution_id" {
  value = module.static_site.distribution_id
}
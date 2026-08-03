output "ecr_repository_url" {
  value = module.ecr.repository_url
}

output "github_actions_role_arn" {
  description = "Paste this into .github/workflows/ci-cd.yml's role-to-assume fields."
  value       = module.github_oidc.role_arn
}
output "role_arn" {
  description = "Put this into .github/workflows/ci-cd.yml's role-to-assume — it's already referenced there by ARN, this is the resource that has to exist first."
  value       = aws_iam_role.github_actions.arn
}

variable "github_repo" {
  description = "GitHub \"org/repo\" this role trusts — e.g. \"choudhuryrajesh-lgtm/ark-fund-api\". Scoped via the OIDC token's sub claim so only workflow runs from this exact repo can assume the role, not any GitHub Actions run anywhere."
  type        = string
}

variable "ecr_repository_arn" {
  description = "So the role can push images to exactly this repository, not every ECR repo in the account."
  type        = string
}

variable "task_execution_role_arns" {
  description = "The ECS task execution/task role ARNs this pipeline is allowed to iam:PassRole when registering a task definition (e.g. demo's execution + task roles). ecs:RegisterTaskDefinition itself can't be resource-scoped by AWS — this is the actual privilege boundary."
  type        = list(string)
}

variable "ecs_service_arns" {
  description = "ECS *service* ARNs (arn:aws:ecs:region:account:service/cluster/service — not cluster ARNs) the pipeline is allowed to describe/update. UpdateService/DescribeServices are scoped at the service level, not the cluster level."
  type        = list(string)
}

variable "tags" {
  type    = map(string)
  default = {}
}

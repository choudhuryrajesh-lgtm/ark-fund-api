# Resources that exist once, independent of any single environment. Apply
# this before staging or production — both reference the ECR repo created
# here via a data source lookup, not a module call, so exactly one
# Terraform state owns it (see the comment in environments/production/main.tf).

data "aws_caller_identity" "current" {}

module "ecr" {
  source          = "../../modules/ecr"
  repository_name = "ark-fund-api"
}

# GitHub Actions -> AWS OIDC federation for .github/workflows/ci-cd.yml.
# Account-wide (one OIDC provider per account), so this lives in shared/
# alongside ECR rather than per-environment.
module "github_oidc" {
  source = "../../modules/github-oidc"

  # GitHub's OIDC "sub" claim embeds immutable numeric IDs for the owner and
  # repo (repo:OWNER@OWNER_ID/REPO@REPO_ID:...), not just their names — a
  # deliberate hardening so a renamed/deleted-and-recreated repo can't
  # inherit an old trust policy. Confirmed against the actual token via a
  # temporary debug step in ci-cd.yml rather than assumed.
  github_repo        = "choudhuryrajesh-lgtm@295211991/ark-fund-api@1319656955"
  ecr_repository_arn = module.ecr.repository_arn

  # Scoped to demo today — the only environment CI/CD actually deploys to
  # (see terraform/README.md). Broaden this list if staging/production get
  # wired into the pipeline for real later.
  task_execution_role_arns = [
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/ark-fund-demo-execution-role",
    "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/ark-fund-demo-task-role",
  ]
  ecs_service_arns = [
    "arn:aws:ecs:us-east-1:${data.aws_caller_identity.current.account_id}:service/ark-fund-demo/ark-fund-demo",
  ]
}
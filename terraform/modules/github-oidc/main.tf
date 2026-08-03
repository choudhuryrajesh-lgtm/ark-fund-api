# Lets GitHub Actions assume an AWS role via short-lived OIDC tokens instead
# of long-lived access keys stored as repo secrets — the CI/CD workflow
# (.github/workflows/ci-cd.yml) already assumes this exists; this module is
# what actually creates it. One provider per AWS account (this errors if one
# already exists at this URL — safe to re-run once created, just don't
# declare it twice across environments).

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # Required by the resource, but AWS no longer actually validates GitHub's
  # OIDC token against this value — it verifies via GitHub's own published
  # signing keys instead (a change AWS rolled out account-wide once GitHub
  # rotated its intermediate CA in 2023). Any syntactically valid 40-char
  # hex string satisfies the field for this provider.
  thumbprint_list = [
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
  tags = var.tags
}

data "aws_iam_policy_document" "trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Repo-wide (any branch/PR/environment) rather than pinned to
    # refs/heads/main — the workflow itself already gates which jobs run on
    # which ref (build-image/deploy only fire on main); this condition's job
    # is keeping the role un-assumable by any *other* repo, not re-enforcing
    # branch policy that already lives in the workflow.
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:*"]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "github-actions-ark-fund-api"
  assume_role_policy = data.aws_iam_policy_document.trust.json
  tags               = var.tags
}

data "aws_iam_policy_document" "permissions" {
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"] # this specific action has no resource-level scoping in ECR
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
    ]
    resources = [var.ecr_repository_arn]
  }

  statement {
    sid       = "EcsRegisterTaskDef"
    actions   = ["ecs:RegisterTaskDefinition", "ecs:DescribeTaskDefinition"]
    resources = ["*"] # RegisterTaskDefinition can't be scoped by resource; iam:PassRole below is the real boundary
  }

  statement {
    sid       = "EcsDeployService"
    actions   = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = var.ecs_service_arns
  }

  statement {
    sid       = "PassEcsRoles"
    actions   = ["iam:PassRole"]
    resources = var.task_execution_role_arns

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_actions" {
  name   = "github-actions-ark-fund-api-deploy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.permissions.json
}

# Two roles, matching the two ARNs already referenced in
# deploy/ecs/task-definition.*.json (ark-fund-api-execution-role,
# ark-fund-api-task-role):
#
#   execution role — used by the ECS agent itself to pull the image and
#   fetch the secrets referenced in the task definition. Needs
#   secretsmanager:GetSecretValue scoped to exactly the three DB secrets
#   plus the optional New Relic license key secret, nothing broader.
#
#   task role — assumed by the application code running inside the
#   container. Empty today: this API doesn't call any other AWS service at
#   runtime. Kept as a distinct role (rather than reusing the execution
#   role) so that changes, when the app does need AWS access later, don't
#   also have to be trusted by the ECS agent's own pull/secrets path.

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.name}-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
  tags               = var.tags
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = concat(
      [
        var.db_url_secret_arn,
        var.db_username_secret_arn,
        var.db_password_secret_arn,
      ],
      # Only present when a New Relic license key was supplied — see the
      # conditional secret resource in main.tf.
      var.new_relic_license_key != null ? [aws_secretsmanager_secret.new_relic_license_key[0].arn] : []
    )
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "${var.name}-execution-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

resource "aws_iam_role" "task" {
  name               = "${var.name}-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
  tags               = var.tags
}
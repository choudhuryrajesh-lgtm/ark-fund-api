# RDS PostgreSQL, Multi-AZ, in the data-tier subnets. Credentials are
# generated here and stored in Secrets Manager under names the ECS task
# definitions already expect (deploy/ecs/task-definition.*.json) — apply
# this module, then paste the printed ARNs into ACCOUNT_ID placeholders
# there, or better, template the task definitions from these outputs in CI.

resource "aws_db_subnet_group" "this" {
  name       = "${var.name}-db-subnets"
  subnet_ids = var.subnet_ids
  tags       = merge(var.tags, { Name = "${var.name}-db-subnets" })
}

resource "random_password" "master" {
  length  = 32
  special = false # simplifies embedding in a JDBC URL without percent-encoding
}

resource "aws_db_instance" "this" {
  identifier     = var.name
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  db_name  = var.database_name
  username = var.master_username
  password = random_password.master.result

  allocated_storage     = var.allocated_storage_gb
  max_allocated_storage = var.max_allocated_storage_gb
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.security_group_id]

  multi_az                = var.multi_az
  backup_retention_period = var.backup_retention_days
  # A window when quarter-end reporting traffic is least likely to be
  # affected — see docs/02-Capacity-Planning.md §3 on the quarter-end spike.
  backup_window      = "05:00-06:00"
  maintenance_window = "sun:06:30-sun:07:30"

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.name}-final-snapshot"

  performance_insights_enabled = true

  tags = merge(var.tags, { Name = var.name })
}

# --- Credentials in Secrets Manager, named to match what the ECS task
# definitions already reference (deploy/ecs/task-definition.*.json), and to
# match the env var names application.yml actually reads
# (SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD) — the task definitions
# previously injected DB_URL/DB_USERNAME/DB_PASSWORD instead, which Spring
# silently ignores in favor of its local-dev defaults. -----------------------

resource "aws_secretsmanager_secret" "db_url" {
  name = "ark-fund-api/${var.environment}/spring-datasource-url"
  tags = var.tags
}

resource "aws_secretsmanager_secret_version" "db_url" {
  secret_id     = aws_secretsmanager_secret.db_url.id
  secret_string = "jdbc:postgresql://${aws_db_instance.this.address}:${aws_db_instance.this.port}/${var.database_name}"
}

resource "aws_secretsmanager_secret" "db_username" {
  name = "ark-fund-api/${var.environment}/spring-datasource-username"
  tags = var.tags
}

resource "aws_secretsmanager_secret_version" "db_username" {
  secret_id     = aws_secretsmanager_secret.db_username.id
  secret_string = var.master_username
}

resource "aws_secretsmanager_secret" "db_password" {
  name = "ark-fund-api/${var.environment}/spring-datasource-password"
  tags = var.tags
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.master.result
}
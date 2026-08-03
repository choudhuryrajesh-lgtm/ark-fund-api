# Three security groups, chained tightest-first, matching docs/12-AWS-Deployment.md §4:
#   API Gateway (via VPC Link) -> alb-sg:443 -> ecs-sg:8083 -> rds-sg:5432
#
# The ALB is internal (see modules/alb) — API Gateway is the only public
# entry point, reaching the ALB through a VPC Link whose ENIs live inside
# this VPC. alb-sg's ingress is therefore scoped to the VPC CIDR, never
# 0.0.0.0/0 — an internet-reachable ALB here would be a second, unthrottled
# way in that bypasses API Gateway's rate limiting entirely.
#
# The three SG resources are created bare (no inline rules) and the
# cross-referencing rules are added afterward as separate resources — alb-sg
# and ecs-sg each reference the other's ID, which would be a dependency
# cycle if declared inline on both resources at once.

resource "aws_security_group" "alb" {
  name        = "${var.name}-alb-sg"
  description = "Internal ALB - reachable only from inside the VPC, via the API Gateway VPC Link."
  vpc_id      = var.vpc_id
  tags        = merge(var.tags, { Name = "${var.name}-alb-sg" })
}

resource "aws_security_group" "ecs" {
  name        = "${var.name}-ecs-sg"
  description = "ECS Fargate tasks - reachable only from the ALB."
  vpc_id      = var.vpc_id
  tags        = merge(var.tags, { Name = "${var.name}-ecs-sg" })
}

resource "aws_security_group" "rds" {
  name        = "${var.name}-rds-sg"
  description = "RDS - reachable only from ECS tasks, no egress at all."
  vpc_id      = var.vpc_id
  # Explicit empty egress list, not omitted: AWS auto-adds an allow-all
  # outbound rule to every new security group at the API level, regardless
  # of tool. An empty (but present) egress argument is what tells Terraform
  # to manage egress as "no rules" and strip that default — RDS never
  # initiates outbound connections, so this is a real no-egress group, not
  # just an unmanaged one.
  egress = []
  tags   = merge(var.tags, { Name = "${var.name}-rds-sg" })
}

# --- alb-sg rules ----------------------------------------------------------

resource "aws_vpc_security_group_ingress_rule" "alb_from_vpc_link" {
  security_group_id = aws_security_group.alb.id
  description       = "From the API Gateway VPC Link ENIs, on whichever port the ALB listener actually runs - the only path in."
  cidr_ipv4         = var.vpc_cidr
  from_port         = var.alb_listener_port
  to_port           = var.alb_listener_port
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_ecs" {
  security_group_id            = aws_security_group.alb.id
  description                  = "Forward to ECS tasks on the app port."
  referenced_security_group_id = aws_security_group.ecs.id
  from_port                    = var.container_port
  to_port                      = var.container_port
  ip_protocol                  = "tcp"
}

# --- ecs-sg rules ------------------------------------------------------------

resource "aws_vpc_security_group_ingress_rule" "ecs_from_alb" {
  security_group_id            = aws_security_group.ecs.id
  description                  = "Only the ALB can reach the app port."
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = var.container_port
  to_port                      = var.container_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "ecs_to_rds" {
  security_group_id            = aws_security_group.ecs.id
  description                  = "Database access."
  referenced_security_group_id = aws_security_group.rds.id
  from_port                    = var.db_port
  to_port                      = var.db_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "ecs_to_internet_https" {
  security_group_id = aws_security_group.ecs.id
  description       = "Outbound HTTPS via NAT - ECR image pulls, Secrets Manager, telemetry."
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

# --- rds-sg rules ------------------------------------------------------------

resource "aws_vpc_security_group_ingress_rule" "rds_from_ecs" {
  security_group_id            = aws_security_group.rds.id
  description                  = "Only ECS tasks can reach Postgres."
  referenced_security_group_id = aws_security_group.ecs.id
  from_port                    = var.db_port
  to_port                      = var.db_port
  ip_protocol                  = "tcp"
}
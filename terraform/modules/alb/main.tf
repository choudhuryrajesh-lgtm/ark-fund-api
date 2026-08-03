# Internal ALB — not internet-facing. The only path in is
# Route53 -> API Gateway -> (VPC Link) -> this ALB -> ECS. An internet-facing
# ALB here would give callers a second, unthrottled entry point that
# bypasses API Gateway's rate limiting and usage plans entirely, defeating
# the point of putting API Gateway in front of it at all.

resource "aws_lb" "this" {
  name               = "${var.name}-alb"
  internal           = true
  load_balancer_type = "application"
  security_groups    = [var.security_group_id]
  subnets            = var.subnet_ids
  tags               = var.tags
}

resource "aws_lb_target_group" "app" {
  name        = "${var.name}-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip" # required for Fargate awsvpc networking mode

  health_check {
    path                = var.health_check_path
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 15
    timeout             = 5
    matcher             = "200"
  }

  tags = var.tags
}

resource "aws_lb_listener" "https" {
  count = var.certificate_arn != null ? 1 : 0

  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

# Used only when no certificate_arn is supplied (see variables.tf) — e.g.
# environments/demo, which deliberately has no domain and no ACM cert.
# Still never internet-reachable: this ALB is internal, and its only caller
# is the API Gateway VPC Link, which stays inside the VPC either way.
resource "aws_lb_listener" "http" {
  count = var.certificate_arn == null ? 1 : 0

  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}
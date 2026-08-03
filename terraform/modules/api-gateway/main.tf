# HTTP API (not REST API — cheaper, simpler, sufficient for a proxy-only
# integration with no per-key usage plans needed yet) fronting the internal
# ALB through a VPC Link. This is the single public entry point described
# in docs/03-System-Architecture.md — Route 53 -> here -> ALB -> ECS.

resource "aws_security_group" "vpc_link" {
  name        = "${var.name}-vpc-link-sg"
  description = "API Gateway VPC Link ENIs - outbound to the internal ALB only."
  vpc_id      = var.vpc_id
  # No inline egress block (not even `= []`) — the one actual rule below is a
  # separate aws_vpc_security_group_egress_rule resource, same as alb-sg and
  # ecs-sg in modules/security-groups. Declaring `egress = []` here as well
  # would make this resource think it owns "zero rules" and fight the
  # standalone rule resource on every plan, destroying and recreating it
  # each apply.
  tags = merge(var.tags, { Name = "${var.name}-vpc-link-sg" })
}

resource "aws_vpc_security_group_egress_rule" "vpc_link_to_alb" {
  security_group_id            = aws_security_group.vpc_link.id
  referenced_security_group_id = var.alb_security_group_id
  from_port                    = var.alb_listener_port
  to_port                      = var.alb_listener_port
  ip_protocol                  = "tcp"
}

resource "aws_apigatewayv2_vpc_link" "this" {
  name               = "${var.name}-vpc-link"
  subnet_ids         = var.private_subnet_ids
  security_group_ids = [aws_security_group.vpc_link.id]
  tags               = var.tags
}

resource "aws_apigatewayv2_api" "this" {
  name          = var.name
  protocol_type = "HTTP"
  tags          = var.tags
}

resource "aws_apigatewayv2_integration" "alb" {
  api_id             = aws_apigatewayv2_api.this.id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  connection_type    = "VPC_LINK"
  connection_id      = aws_apigatewayv2_vpc_link.this.id
  integration_uri    = var.alb_listener_arn
}

# Single catch-all route — this API is a pure reverse proxy in front of the
# Spring Boot app's own routing (/api/v1/..., /swagger-ui.html,
# /v3/api-docs, /actuator/health); path-based rules belong in the app, not
# duplicated at the gateway.
resource "aws_apigatewayv2_route" "proxy" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.alb.id}"
}

# The root path ("/") isn't covered by {proxy+} (API Gateway requires at
# least one path segment for a greedy proxy match) — actuator/health and
# the API itself are always under a path, but this catches a bare-domain
# request cleanly instead of a bare 404 with no routing at all.
resource "aws_apigatewayv2_route" "root" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /"
  target    = "integrations/${aws_apigatewayv2_integration.alb.id}"
}

resource "aws_cloudwatch_log_group" "access_logs" {
  name              = "/apigateway/${var.name}"
  retention_in_days = var.log_retention_days
  tags              = var.tags
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_rate_limit  = var.throttle_rate_limit
    throttling_burst_limit = var.throttle_burst_limit
  }

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.access_logs.arn
    format = jsonencode({
      requestId       = "$context.requestId"
      ip              = "$context.identity.sourceIp"
      requestTime     = "$context.requestTime"
      httpMethod      = "$context.httpMethod"
      routeKey        = "$context.routeKey"
      status          = "$context.status"
      integrationErr  = "$context.integrationErrorMessage"
      responseLatency = "$context.responseLatency"
    })
  }

  tags = var.tags
}

# --- Custom domain (optional) ----------------------------------------------
# Only created when domain_name is supplied. Without it, callers use the
# free auto-generated invoke URL every HTTP API gets by default — see the
# api_endpoint output. environments/demo deliberately has neither.

resource "aws_apigatewayv2_domain_name" "this" {
  count = var.domain_name != null ? 1 : 0

  domain_name = var.domain_name

  domain_name_configuration {
    certificate_arn = var.certificate_arn
    endpoint_type   = "REGIONAL"
    security_policy = "TLS_1_2"
  }

  tags = var.tags
}

resource "aws_apigatewayv2_api_mapping" "this" {
  count = var.domain_name != null ? 1 : 0

  api_id      = aws_apigatewayv2_api.this.id
  domain_name = aws_apigatewayv2_domain_name.this[0].id
  stage       = aws_apigatewayv2_stage.default.id
}
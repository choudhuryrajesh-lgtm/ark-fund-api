output "api_id" {
  value = aws_apigatewayv2_api.this.id
}

output "api_endpoint" {
  description = "The free, auto-generated invoke URL every HTTP API gets by default (e.g. https://abc123.execute-api.us-east-1.amazonaws.com) — works immediately, HTTPS already handled by AWS, no domain/cert needed. Use this directly when domain_name wasn't supplied (see environments/demo)."
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "api_endpoint_domain" {
  description = "Same as api_endpoint but host-only, no scheme/trailing slash — what a CloudFront custom origin's domain_name wants (see modules/static-site)."
  value       = trimsuffix(trimprefix(aws_apigatewayv2_stage.default.invoke_url, "https://"), "/")
}

output "domain_name_target" {
  description = "The regional API Gateway endpoint to alias from Route 53 — target_domain_name on the domain_name_configuration. Empty string when domain_name wasn't supplied; use api_endpoint instead in that case."
  value       = try(aws_apigatewayv2_domain_name.this[0].domain_name_configuration[0].target_domain_name, "")
}

output "domain_name_hosted_zone_id" {
  description = "For the Route 53 alias record's zone_id (API Gateway's own zone, not the account's hosted zone). Empty string when domain_name wasn't supplied."
  value       = try(aws_apigatewayv2_domain_name.this[0].domain_name_configuration[0].hosted_zone_id, "")
}
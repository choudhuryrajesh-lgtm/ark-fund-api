# ACM certificate for the API's custom domain, DNS-validated against an
# already-existing Route 53 public hosted zone. The final alias record
# pointing the domain at API Gateway is created in the environment root
# module (environments/{env}/main.tf), not here — it depends on the
# api-gateway module's output, which in turn depends on this module's
# certificate, so it has to come after both in the dependency chain rather
# than living inside this module.

data "aws_route53_zone" "this" {
  name         = var.zone_name
  private_zone = false
}

resource "aws_acm_certificate" "this" {
  domain_name       = var.domain_name
  validation_method = "DNS"
  tags              = var.tags

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "validation" {
  for_each = {
    for dvo in aws_acm_certificate.this.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      type   = dvo.resource_record_type
      record = dvo.resource_record_value
    }
  }

  zone_id         = data.aws_route53_zone.this.zone_id
  name            = each.value.name
  type            = each.value.type
  records         = [each.value.record]
  ttl             = 60
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "this" {
  certificate_arn         = aws_acm_certificate.this.arn
  validation_record_fqdns = [for r in aws_route53_record.validation : r.fqdn]
}
# Static frontend (Vite/React build) on S3, served through CloudFront.
# CloudFront also proxies /api/* straight to the backend's API Gateway origin
# — the same single-origin pattern frontend/nginx.conf uses locally — so the
# browser only ever talks to this one distribution and Spring Boot needs no
# CORS configuration at all. No custom domain: this uses CloudFront's own
# free *.cloudfront.net certificate, matching environments/demo's "no domain
# needed" approach for the API Gateway side.

resource "aws_s3_bucket" "site" {
  bucket = "${var.name}-frontend"
  # This environment exists to be torn down quickly (see environments/demo) —
  # without this, `terraform destroy` fails with BucketNotEmpty once the
  # deploy script has synced a build into it.
  force_destroy = true
  tags          = merge(var.tags, { Name = "${var.name}-frontend" })
}

# No public access at all — the bucket is only ever reached through
# CloudFront's Origin Access Control below, never directly.
resource "aws_s3_bucket_public_access_block" "site" {
  bucket                  = aws_s3_bucket.site.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "site" {
  name                              = "${var.name}-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

data "aws_iam_policy_document" "site" {
  statement {
    sid       = "AllowCloudFrontOAC"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.site.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.this.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "site" {
  bucket = aws_s3_bucket.site.id
  policy = data.aws_iam_policy_document.site.json
}

# --- CloudFront -------------------------------------------------------------
# Managed policy IDs below are AWS's own predefined policies (same in every
# account/region) — no need to declare custom cache/origin-request policies
# for behavior this standard.

resource "aws_cloudfront_distribution" "this" {
  enabled             = true
  default_root_object = "index.html"
  price_class         = var.price_class
  comment             = "${var.name} frontend"

  origin {
    origin_id                = "s3-site"
    domain_name              = aws_s3_bucket.site.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.site.id
  }

  origin {
    origin_id   = "api-gateway"
    domain_name = var.api_domain_name

    custom_origin_config {
      origin_protocol_policy = "https-only"
      http_port              = 80
      https_port             = 443
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = "s3-site"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = "658327ea-f89d-4fab-a63d-7e88639e58f6" # AWS managed: CachingOptimized
  }

  # Everything under /api/ goes straight to the backend, uncached — this is
  # what makes the frontend and backend look like a single origin to the
  # browser (see the module-level comment above).
  ordered_cache_behavior {
    path_pattern           = "/api/*"
    target_origin_id       = "api-gateway"
    viewer_protocol_policy = "https-only"
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" # AWS managed: CachingDisabled
    # AllViewerExceptHostHeader, not AllViewer: forwarding the viewer's Host
    # header (this distribution's own domain) straight to API Gateway makes
    # it reject the request as Forbidden — API Gateway validates Host for
    # routing/SNI and expects its own execute-api domain, which CloudFront
    # sets correctly on its own as long as Host isn't in the forwarded set.
    origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # AWS managed: AllViewerExceptHostHeader
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = var.tags
}
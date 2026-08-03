output "bucket_name" {
  description = "Where the frontend build's contents get synced — see scripts/deploy-frontend.sh."
  value       = aws_s3_bucket.site.bucket
}

output "distribution_id" {
  description = "For invalidating the cache after a new deploy."
  value       = aws_cloudfront_distribution.this.id
}

output "url" {
  description = "The site's public HTTPS URL — CloudFront's free auto-generated domain, no custom domain needed."
  value       = "https://${aws_cloudfront_distribution.this.domain_name}"
}
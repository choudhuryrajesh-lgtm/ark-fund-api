variable "name" {
  type = string
}

variable "api_domain_name" {
  description = "Host-only domain of the backend API (e.g. \"abc123.execute-api.us-east-1.amazonaws.com\", no scheme/path) — CloudFront's /api/* behavior proxies straight to this origin, so the browser only ever talks to one origin (this distribution) and Spring Boot needs no CORS config. See modules/api-gateway's api_endpoint_domain output."
  type        = string
}

variable "price_class" {
  description = "CloudFront price class. PriceClass_100 (US/Canada/Europe only) is cheapest and plenty for a demo; PriceClass_All adds edge locations worldwide at higher cost."
  type        = string
  default     = "PriceClass_100"
}

variable "tags" {
  type    = map(string)
  default = {}
}

# Remote state in S3 with DynamoDB locking. The bucket and table must exist
# before `terraform init` — they're deliberately not managed by this same
# state (a classic chicken-and-egg: the backend that stores state can't be
# the first resource created using that state). Create both once, by hand
# or via a tiny separate bootstrap config, before running anything here —
# see terraform/README.md.
terraform {
  backend "s3" {
    bucket         = "ark-fund-api-tfstate-976193264048"
    key            = "ark-fund-api/production/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "ark-fund-api-tf-locks"
    encrypt        = true
  }
}
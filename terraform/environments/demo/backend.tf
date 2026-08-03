# Same state bucket/table as staging and production, distinct key — fully
# independent state, so destroying this environment can never touch either
# of the others.
terraform {
  backend "s3" {
    bucket         = "ark-fund-api-tfstate-976193264048"
    key            = "ark-fund-api/demo/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "ark-fund-api-tf-locks"
    encrypt        = true
  }
}
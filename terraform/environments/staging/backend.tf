terraform {
  backend "s3" {
    bucket         = "ark-fund-api-tfstate-976193264048"
    key            = "ark-fund-api/staging/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "ark-fund-api-tf-locks"
    encrypt        = true
  }
}
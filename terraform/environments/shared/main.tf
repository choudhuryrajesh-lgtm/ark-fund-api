# Resources that exist once, independent of any single environment. Apply
# this before staging or production — both reference the ECR repo created
# here via a data source lookup, not a module call, so exactly one
# Terraform state owns it (see the comment in environments/production/main.tf).

module "ecr" {
  source          = "../../modules/ecr"
  repository_name = "ark-fund-api"
}
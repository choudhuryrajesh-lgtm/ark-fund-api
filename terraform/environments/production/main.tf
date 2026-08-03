locals {
  name = "ark-fund-production"
  common_tags = {
    Project     = "ark-fund-api"
    Environment = "production"
    ManagedBy   = "terraform"
  }
}

module "network" {
  source   = "../../modules/network"
  name     = local.name
  az_count = 3 # full spread across AZs — see docs/03-System-Architecture.md §3
  tags     = local.common_tags
}

module "security_groups" {
  source   = "../../modules/security-groups"
  name     = local.name
  vpc_id   = module.network.vpc_id
  vpc_cidr = module.network.vpc_cidr
  tags     = local.common_tags
}

module "dns" {
  source      = "../../modules/dns"
  zone_name   = var.zone_name
  domain_name = var.domain_name
  tags        = local.common_tags
}

module "rds" {
  source                = "../../modules/rds"
  name                  = local.name
  environment           = "production"
  subnet_ids            = module.network.data_subnet_ids
  security_group_id     = module.security_groups.rds_sg_id
  instance_class        = "db.r6g.xlarge" # docs/02-Capacity-Planning.md §7 / docs/03 §5
  multi_az              = true
  backup_retention_days = 35
  deletion_protection   = true
  tags                  = local.common_tags
}

# The ECR repository is a genuinely shared, cross-environment resource — one
# registry, images tagged by commit SHA, promoted staging -> production by
# reference (docs/05-CICD.md). It's created once in terraform/environments/
# shared, not per-environment: creating it here too would have two
# independent Terraform states fighting over the same repository name.
data "aws_ecr_repository" "this" {
  name = "ark-fund-api"
}

module "alb" {
  source            = "../../modules/alb"
  name              = local.name
  vpc_id            = module.network.vpc_id
  subnet_ids        = module.network.private_subnet_ids
  security_group_id = module.security_groups.alb_sg_id
  certificate_arn   = module.dns.certificate_arn
  tags              = local.common_tags
}

module "ecs" {
  source = "../../modules/ecs"

  name        = local.name
  environment = "production"
  aws_region  = var.aws_region

  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids
  security_group_id  = module.security_groups.ecs_sg_id

  target_group_arn        = module.alb.target_group_arn
  alb_arn_suffix          = module.alb.alb_arn_suffix
  target_group_arn_suffix = module.alb.target_group_arn_suffix

  ecr_repository_url = data.aws_ecr_repository.this.repository_url

  # Production sizing — matches deploy/ecs/task-definition.production.json
  # and the autoscaling floor/ceiling in docs/06-Resiliency-Scalability.md §2.
  cpu           = "1024"
  memory        = "2048"
  desired_count = 6
  min_capacity  = 4
  max_capacity  = 16

  db_url_secret_arn      = module.rds.db_url_secret_arn
  db_username_secret_arn = module.rds.db_username_secret_arn
  db_password_secret_arn = module.rds.db_password_secret_arn

  new_relic_license_key = var.new_relic_license_key

  tags = local.common_tags
}

module "api_gateway" {
  source = "../../modules/api-gateway"

  name   = local.name
  vpc_id = module.network.vpc_id

  private_subnet_ids    = module.network.private_subnet_ids
  alb_security_group_id = module.security_groups.alb_sg_id
  alb_listener_arn      = module.alb.listener_arn

  domain_name     = var.domain_name
  certificate_arn = module.dns.certificate_arn

  tags = local.common_tags
}

# The final hop: alias the custom domain at API Gateway's regional endpoint.
# Depends on both modules.dns (the zone) and modules.api_gateway (the
# target) — deliberately not inside either module, since it needs outputs
# from both. See modules/dns/main.tf's header comment for why.
resource "aws_route53_record" "api" {
  zone_id = module.dns.zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = module.api_gateway.domain_name_target
    zone_id                = module.api_gateway.domain_name_hosted_zone_id
    evaluate_target_health = false
  }
}
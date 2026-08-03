locals {
  name = "ark-fund-staging"
  common_tags = {
    Project     = "ark-fund-api"
    Environment = "staging"
    ManagedBy   = "terraform"
  }
}

module "network" {
  source   = "../../modules/network"
  name     = local.name
  az_count = 2 # staging doesn't need production's full 3-AZ spread
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
  environment           = "staging"
  subnet_ids            = module.network.data_subnet_ids
  security_group_id     = module.security_groups.rds_sg_id
  instance_class        = "db.t4g.medium" # not customer-facing — no need for production sizing
  multi_az              = false           # cost saving; staging tolerates a single-AZ outage
  backup_retention_days = 7
  deletion_protection   = false # staging gets rebuilt/reseeded routinely
  tags                  = local.common_tags
}

# Shared, cross-environment resource — see terraform/environments/shared.
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
  environment = "staging"
  aws_region  = var.aws_region

  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids
  security_group_id  = module.security_groups.ecs_sg_id

  target_group_arn        = module.alb.target_group_arn
  alb_arn_suffix          = module.alb.alb_arn_suffix
  target_group_arn_suffix = module.alb.target_group_arn_suffix

  ecr_repository_url = data.aws_ecr_repository.this.repository_url

  # Staging sizing — matches deploy/ecs/task-definition.staging.json. No
  # aggressive autoscaling: staging exists for pre-release verification, not
  # to absorb real traffic spikes.
  cpu           = "512"
  memory        = "1024"
  desired_count = 2
  min_capacity  = 1
  max_capacity  = 4

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

  # Staging doesn't need production's request ceiling.
  throttle_rate_limit  = 200
  throttle_burst_limit = 400

  tags = local.common_tags
}

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
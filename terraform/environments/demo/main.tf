# Minimal-cost, short-lived spin-up — built to be applied, used for at most
# a few days, then destroyed. Every knob here is chosen to minimize idle
# cost, not to represent a realistic production or even staging shape:
#
#   - 2 AZs of subnets (RDS requires a DB subnet group to span at least 2
#     AZs even with multi_az = false), but only 1 NAT gateway total via
#     single_nat_gateway - NAT is the dominant idle cost of the whole
#     architecture (~$0.045/hr each, normally 2-3 of them), so this is the
#     single biggest lever for a short-lived environment.
#   - Smallest RDS instance class, single-AZ (no standby), minimal storage/backups.
#   - Minimum viable Fargate task size and count.
#   - No custom domain, no ACM certificate, no Route 53 record — this
#     environment doesn't need a real domain at all. The ALB gets a plain
#     HTTP listener (still never internet-reachable — it's internal either
#     way, see modules/alb) and callers use API Gateway's free
#     auto-generated invoke URL (the api_url output below).
#   - No production hardening (no deletion_protection, short backup
#     retention) — intentional, since this environment's whole purpose is
#     to be torn down quickly, not protected from accidental deletion.
#
# See terraform/README.md § "Demo environment" for the cost estimate this
# was sized against and the destroy command.

locals {
  name = "ark-fund-demo"
  common_tags = {
    Project     = "ark-fund-api"
    Environment = "demo"
    ManagedBy   = "terraform"
    Lifecycle   = "short-lived" # a visual flag in the AWS console/cost explorer
  }
}

module "network" {
  source = "../../modules/network"
  name   = local.name
  # 2 AZs of subnets - RDS requires a DB subnet group to span at least 2
  # AZs even with multi_az = false - but single_nat_gateway keeps the
  # actual billed NAT gateway count at 1, the real cost lever here.
  az_count           = 2
  single_nat_gateway = true
  tags               = local.common_tags
}

module "security_groups" {
  source   = "../../modules/security-groups"
  name     = local.name
  vpc_id   = module.network.vpc_id
  vpc_cidr = module.network.vpc_cidr
  # This environment's ALB has no certificate (see module "alb" below), so it
  # listens on plain HTTP:80, not the module's 443 default — the VPC Link ->
  # ALB security group rule has to match or API Gateway can reach the ALB's
  # security group but gets blocked on the wrong port.
  alb_listener_port = 80
  tags              = local.common_tags
}

module "rds" {
  source            = "../../modules/rds"
  name              = local.name
  environment       = "demo"
  subnet_ids        = module.network.data_subnet_ids
  security_group_id = module.security_groups.rds_sg_id

  instance_class           = "db.t4g.micro" # smallest current-gen Postgres-capable class
  allocated_storage_gb     = 20             # RDS's minimum for Postgres — no headroom needed for a demo
  max_allocated_storage_gb = 30
  multi_az                 = false
  backup_retention_days    = 1     # torn down before a longer window would ever matter
  deletion_protection      = false # must be destroyable without a manual override step
  skip_final_snapshot      = true  # no long-term data value here — keeps `destroy` fast and repeatable
  tags                     = local.common_tags
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
  # No certificate_arn — no domain in this environment, so the module
  # creates a plain HTTP:80 listener instead. Still never internet-facing;
  # see the internal-ALB rationale in modules/alb/main.tf.
  tags = local.common_tags
}

module "ecs" {
  source = "../../modules/ecs"

  name        = local.name
  environment = "demo"
  aws_region  = var.aws_region

  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids
  security_group_id  = module.security_groups.ecs_sg_id

  target_group_arn        = module.alb.target_group_arn
  alb_arn_suffix          = module.alb.alb_arn_suffix
  target_group_arn_suffix = module.alb.target_group_arn_suffix

  ecr_repository_url = data.aws_ecr_repository.this.repository_url
  image_tag          = var.image_tag

  # 512 CPU units / 1024MB, not Fargate's absolute floor (256/512) — a JVM
  # running Spring Boot plus the New Relic agent's own memory overhead is
  # genuinely tight at the minimum size; still a single task, this
  # environment exists to prove the deployment works, not carry real traffic.
  cpu           = "512"
  memory        = "1024"
  desired_count = 1
  min_capacity  = 1
  max_capacity  = 2

  db_url_secret_arn      = module.rds.db_url_secret_arn
  db_username_secret_arn = module.rds.db_username_secret_arn
  db_password_secret_arn = module.rds.db_password_secret_arn

  new_relic_license_key = var.new_relic_license_key

  log_retention_days = 3 # matches the environment's own short lifespan

  tags = local.common_tags
}

module "api_gateway" {
  source = "../../modules/api-gateway"

  name   = local.name
  vpc_id = module.network.vpc_id

  private_subnet_ids    = module.network.private_subnet_ids
  alb_security_group_id = module.security_groups.alb_sg_id
  alb_listener_arn      = module.alb.listener_arn
  alb_listener_port     = 80 # matches module "security_groups" above — see that comment

  # No domain_name/certificate_arn — this environment uses the API's free
  # auto-generated invoke URL instead (see the api_url output). No Route 53
  # record needed either, so there isn't one below.

  # Tight limits — this is a demo walkthrough, not production traffic.
  throttle_rate_limit  = 50
  throttle_burst_limit = 100

  log_retention_days = 3

  tags = local.common_tags
}

module "static_site" {
  source = "../../modules/static-site"

  name            = local.name
  api_domain_name = module.api_gateway.api_endpoint_domain
  tags            = local.common_tags
}
# AWS Deployment

Supporting files: [`deploy/ecs/task-definition.production.json`](../deploy/ecs/task-definition.production.json),
[`deploy/ecs/task-definition.staging.json`](../deploy/ecs/task-definition.staging.json),
[`deploy/ecs/appspec.yaml`](../deploy/ecs/appspec.yaml).

## 1. Environments

| Environment | AWS account | Purpose | Data | Status |
|---|---|---|---|---|
| dev | Shared dev account | Individual engineer testing; `docker compose up` locally is the default, this is for integration testing against real AWS services | Synthetic | Local Compose serves this role today |
| **demo** | Candidate-owned account | Minimal-cost live environment so the submission can be evaluated without installing anything | Seeded demo data | **Applied and running** |
| staging | `ark-staging` | Pre-release verification | Anonymized copy of production shape, not real client data | Written, not applied |
| production | `ark-production` | Live traffic | Real client data | Written, not applied |

Account-per-environment (not a shared account with namespaced resources) so an IAM
misconfiguration or a runaway cost in staging can never touch production, and blast radius
of a compromised credential is bounded to one environment.

**The live `demo` environment:**

| | URL |
|---|---|
| API (Swagger UI) | https://524p1owhlc.execute-api.us-east-1.amazonaws.com/swagger-ui/index.html |
| Frontend | https://d5rx4a862iikr.cloudfront.net/ |

`demo` deliberately skips the Route 53 / ACM custom domain that `staging` and `production`
use, taking API Gateway's free auto-generated URL instead — a registered domain is a
manual, chargeable, human step that has nothing to do with whether the infrastructure code
is correct.

## 2. Infrastructure as Code

Real Terraform, in [`terraform/`](../terraform) — `terraform validate` and `terraform fmt
-check` clean across every environment, and **`demo` and `shared` are applied against a
live AWS account**, not just validated. `staging` and `production` are written but not
applied (see §1). Full runbook: [`terraform/README.md`](../terraform/README.md).

```
terraform/
├── modules/
│   ├── network/          # VPC, subnets, NAT, IGW, route tables
│   ├── security-groups/  # alb-sg -> ecs-sg -> rds-sg, chained least-privilege
│   ├── rds/               # instance, Multi-AZ, subnet group, Secrets Manager credentials
│   ├── ecr/               # container registry
│   ├── alb/               # internal load balancer, target group, HTTPS listener
│   ├── ecs/               # cluster, task definition, service, IAM roles, autoscaling
│   ├── api-gateway/       # HTTP API + VPC Link into the internal ALB, custom domain
│   ├── dns/               # ACM certificate, DNS-validated against an existing Route 53 zone
│   ├── static-site/       # S3 + CloudFront for the React UI, /api/* behavior -> API Gateway
│   └── github-oidc/       # IAM OIDC provider + deploy role for GitHub Actions (no static keys)
└── environments/
    ├── shared/             # ECR repo + OIDC role — applied once, account-wide
    ├── demo/               # minimal-cost live environment (APPLIED)
    ├── staging/            # full stack, staging sizing
    └── production/         # full stack, production sizing
```

Not yet built on top of this: production blue/green via CodeDeploy (both environments
currently get a plain rolling ECS deployment — see `terraform/README.md` for exactly what
that follow-up needs), WAF in front of API Gateway, and the cross-region DR replica from
[13-Disaster-Recovery-Failover.md](13-Disaster-Recovery-Failover.md). Observability
(CloudWatch alarms, New Relic, Splunk forwarding) is also not yet in this Terraform —
[10-Monitoring-Observability.md](10-Monitoring-Observability.md) describes the target
design.

**Why Terraform over CDK/CloudFormation:** state-file-based plan/apply gives an explicit
diff review step before any infrastructure change, which matters for a system touching
real client financial data — a CDK deploy's "what will actually change" is less legible in
review than a Terraform plan.

## 3. ECS configuration

Task definitions differ between staging (0.5 vCPU / 1GB, single-purpose verification) and
production (1 vCPU / 2GB, sized per [02-Capacity-Planning.md](02-Capacity-Planning.md)).
Both:

- Pull the image from ECR by immutable tag (commit SHA) only — the repository is configured
  with `IMAGE_TAG_MUTABILITY: IMMUTABLE` (see [terraform/modules/ecr](../terraform/modules/ecr)),
  so a floating `:latest` tag doesn't exist at all: re-pushing it on every build would be
  rejected once the repo enforces immutability. CI resolves the tag it just pushed and passes
  it straight through to the render-task-definition step.
- Inject DB credentials from Secrets Manager via the `secrets` block, not plaintext
  `environment` entries.
- Define a container health check independent of the ALB's — ECS can restart an unhealthy
  task even if it's still technically reachable.

## 4. Networking

Full VPC/subnet layout in [03-System-Architecture.md](03-System-Architecture.md) §3.
Security groups:

| Security group | Inbound | Outbound |
|---|---|---|
| `alb-sg` | 443 from the VPC CIDR only — the ALB is internal (never `0.0.0.0/0`; see below) | 8083 to `ecs-sg` |
| `ecs-sg` | 8083 from `alb-sg` only | 5432 to `rds-sg`, 443 to internet (via NAT, for ECR pulls and telemetry) |
| `rds-sg` | 5432 from `ecs-sg` only | none |

**The ALB is internal, not internet-facing.** API Gateway is the sole public entry point;
it reaches the ALB through a VPC Link, whose ENIs are provisioned inside this VPC. An
internet-facing ALB here would give callers a second, unthrottled path in that bypasses API
Gateway's rate limiting and usage plans — the entire reason to front ECS with API Gateway
is that there's exactly one public door, not two.

No security group permits inbound from `0.0.0.0/0` except the ALB's public listener —
every other hop is locked to its specific caller.

## 5. Deployment runbook

**Routine deploy** (handled by CI/CD, [05-CICD.md](05-CICD.md)): merge to `main` → staging
auto-deploys → smoke test → manual approval → production blue/green deploy → bake → full
cutover.

**Manual rollback** (if needed outside the auto-rollback window):
```
aws deploy stop-deployment --deployment-id <id> --auto-rollback-enabled
```

**Emergency manual deploy** (CI/CD pipeline unavailable). The checked-in task definition
carries a `REPLACE_WITH_COMMIT_SHA` placeholder — since the ECR repo is immutable-tagged,
there's no `:latest` to fall back on, so substitute the exact tag of a known-good image
(check ECR directly if unsure which commit last deployed cleanly):
```
sed -i '' 's/REPLACE_WITH_COMMIT_SHA/<known-good-commit-sha>/' deploy/ecs/task-definition.production.json
aws ecs register-task-definition --cli-input-json file://deploy/ecs/task-definition.production.json
aws ecs update-service --cluster ark-fund-production --service ark-fund-api-production \
  --task-definition ark-fund-api-production --force-new-deployment
```
Documented as a break-glass procedure, not the normal path — it bypasses the smoke test
and approval gate, so it requires incident-commander sign-off per the on-call process in
[10-Monitoring-Observability.md](10-Monitoring-Observability.md) §5.

## 6. Cost shape (order of magnitude, not a quote)

| Resource | Steady state | Peak |
|---|---|---|
| ECS Fargate (6–16 tasks × 1 vCPU/2GB) | ~$220/mo | up to ~$580/mo if sustained at max scale |
| RDS primary (`db.r6g.xlarge`) + 1 replica | ~$700/mo | flat (not autoscaled) |
| ALB + API Gateway + NAT + data transfer | ~$150/mo | scales with request volume |

Sized off [02-Capacity-Planning.md](02-Capacity-Planning.md)'s numbers — the point of
deriving RPS/throughput from Ark's actual published metrics rather than guessing is that
this cost estimate is traceable back to a real assumption, not a round number.

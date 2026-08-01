# AWS Deployment

Supporting files: [`deploy/ecs/task-definition.production.json`](../deploy/ecs/task-definition.production.json),
[`deploy/ecs/task-definition.staging.json`](../deploy/ecs/task-definition.staging.json),
[`deploy/ecs/appspec.yaml`](../deploy/ecs/appspec.yaml).

## 1. Environments

| Environment | AWS account | Purpose | Data |
|---|---|---|---|
| dev | Shared dev account | Individual engineer testing; `docker compose up` locally is the default, this is for integration testing against real AWS services | Synthetic |
| staging | `ark-staging` | Pre-release verification, demo environment | Anonymized copy of production shape, not real client data |
| production | `ark-production` | Live traffic | Real client data |

Account-per-environment (not a shared account with namespaced resources) so an IAM
misconfiguration or a runaway cost in staging can never touch production, and blast radius
of a compromised credential is bounded to one environment.

## 2. Infrastructure as Code

Terraform, structured as:

```
infra/
├── modules/
│   ├── network/        # VPC, subnets, NAT, IGW, route tables
│   ├── ecs-service/     # cluster, service, task def template, autoscaling policy
│   ├── rds/             # instance, Multi-AZ, parameter group, subnet group
│   ├── alb/              # load balancer, target group, listener rules
│   └── observability/   # CloudWatch alarms, New Relic integration, Splunk forwarder
├── environments/
│   ├── staging/         # module invocations + tfvars for staging sizing
│   └── production/      # module invocations + tfvars for production sizing
└── backend.tf           # remote state in S3 + DynamoDB lock table
```

This is an outline, not a built module tree — the repository ships the ECS task
definitions and CI/CD pipeline that are the actual deploy-time artifacts; the Terraform
module structure above is the recommended next step for anyone standing up the AWS
account from scratch, scoped out of this take-home the same way a full Kubernetes
manifest set would be.

**Why Terraform over CDK/CloudFormation:** state-file-based plan/apply gives an explicit
diff review step before any infrastructure change, which matters for a system touching
real client financial data — a CDK deploy's "what will actually change" is less legible in
review than a Terraform plan.

## 3. ECS configuration

Task definitions differ between staging (0.5 vCPU / 1GB, single-purpose verification) and
production (1 vCPU / 2GB, sized per [02-Capacity-Planning.md](02-Capacity-Planning.md)).
Both:

- Pull the image from ECR by immutable tag (commit SHA), never `:latest` in the actual
  running task definition — `:latest` is pushed for convenience/debugging only.
- Inject DB credentials from Secrets Manager via the `secrets` block, not plaintext
  `environment` entries.
- Define a container health check independent of the ALB's — ECS can restart an unhealthy
  task even if it's still technically reachable.

## 4. Networking

Full VPC/subnet layout in [03-System-Architecture.md](03-System-Architecture.md) §3.
Security groups:

| Security group | Inbound | Outbound |
|---|---|---|
| `alb-sg` | 443 from `0.0.0.0/0` (API Gateway VPC link or public, depending on API Gateway integration type) | 8083 to `ecs-sg` |
| `ecs-sg` | 8083 from `alb-sg` only | 5432 to `rds-sg`, 443 to internet (via NAT, for ECR pulls and telemetry) |
| `rds-sg` | 5432 from `ecs-sg` only | none |

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

**Emergency manual deploy** (CI/CD pipeline unavailable):
```
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

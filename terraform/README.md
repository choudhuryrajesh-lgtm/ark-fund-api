# Terraform — Ark Fund API infrastructure
11
Real, `terraform validate`-clean HCL for the architecture in
[`docs/03-System-Architecture.md`](../docs/03-System-Architecture.md):

```
Route 53 -> API Gateway -> (VPC Link) -> internal ALB -> ECS Fargate -> RDS Postgres
```

Nothing here has been `apply`'d — this builds the configuration and confirms it's
internally consistent (`init`, `validate`, `fmt` all pass), not that it's been run against
a real AWS account. That's the next step, on your own credentials.

## Layout

```
terraform/
├── modules/            9 reusable modules — network, security-groups, rds, ecr,
│                        alb, ecs, api-gateway, dns
└── environments/
    ├── shared/          ECR repository — applied once, independent of the others
    ├── demo/             minimal-cost, short-lived spin-up (§ below)
    ├── staging/          full stack, staging sizing
    └── production/       full stack, production sizing
```

**Why `shared/` is separate:** the ECR repository is one registry shared across both
environments (images tagged by commit SHA, promoted staging → production by reference —
see [`docs/05-CICD.md`](../docs/05-CICD.md)). If `staging` and `production` each tried to
create their own copy of it via `module "ecr"`, two independent Terraform states would
fight over the same repository name. `shared/` creates it once; `staging`/`production`
each look it up with a `data "aws_ecr_repository"` read instead.

## Prerequisites

1. An AWS account with credentials configured (`aws configure`, or an assumed role —
   anything the AWS provider can pick up).
2. **`staging`/`production` only:** a domain already registered and hosted in Route 53 (a
   public hosted zone). The `dns` module looks this up by name — it does not create one,
   since pointing a registrar's nameservers at a new zone is a manual, one-time step
   outside any environment's lifecycle. **`demo` needs none of this** — see § "Demo
   environment" below; it uses API Gateway's free auto-generated URL instead.
3. An S3 bucket and a DynamoDB table for Terraform remote state, created once, by hand or
   via a small separate bootstrap config — not by the same state they'd be backing (a
   classic chicken-and-egg). Something like:
   ```bash
   aws s3api create-bucket --bucket your-tf-state-bucket --region us-east-1
   aws s3api put-bucket-versioning --bucket your-tf-state-bucket \
     --versioning-configuration Status=Enabled
   aws dynamodb create-table --table-name your-tf-lock-table \
     --attribute-definitions AttributeName=LockID,AttributeType=S \
     --key-schema AttributeName=LockID,KeyType=HASH \
     --billing-mode PAY_PER_REQUEST
   ```
   Then replace `REPLACE_WITH_YOUR_TF_STATE_BUCKET` / `REPLACE_WITH_YOUR_TF_LOCK_TABLE` in
   each `backend.tf` (`shared/`, `demo/`, `staging/`, `production/`) with your real names —
   this one's still needed for every environment, `demo` included.

## Apply order

```bash
# 1. Shared resources first — the ECR repo staging/production both reference.
cd terraform/environments/shared
terraform init
terraform apply

# 2. Each environment is then fully self-contained.
cd ../staging
cp terraform.tfvars.example terraform.tfvars   # fill in your domain/zone
terraform init
terraform plan     # review before applying anything that costs money
terraform apply

cd ../production
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan
terraform apply
```

First `apply` in each environment builds real infrastructure — VPC, NAT gateways (billed
per hour), RDS, ALB, ECS, API Gateway, DNS. Expect it to take 15–20 minutes, mostly RDS
provisioning. Review every `plan` before confirming `apply`; nothing here should be
applied blind.

## Demo environment — minimal cost, short-lived

`environments/demo/` is a fourth, fully independent environment sized for a spin-up you'll
tear down within a few days — a live walkthrough, an interview demo, a one-off check —
not a persistent staging/production shape. Every knob is chosen to minimize idle cost:

| | staging/production | demo |
|---|---|---|
| AZs / NAT gateways | 2–3 / 2–3 | 2 subnets (RDS requires ≥2 AZs even single-AZ) / **1 NAT gateway** via `single_nat_gateway = true` — the single biggest cost lever; see below |
| RDS | `db.r6g.xlarge` or `db.t4g.medium`, Multi-AZ (prod) | `db.t4g.micro`, single-AZ, 20GB, 1-day backups |
| Fargate task | 0.5–1 vCPU, 2–6 tasks | **0.25 vCPU / 512MB, 1 task** (Fargate's minimum) |
| `deletion_protection` | `true` (production) | `false` — must be destroyable without a manual override |
| Custom domain / ACM cert / Route 53 record | Yes | **None** — no domain needed at all (see below) |

**No domain required.** `demo` skips the `dns` module entirely and doesn't pass a
`certificate_arn` to `alb`/`api-gateway`. API Gateway's HTTP API gets a free,
auto-generated HTTPS URL the moment it's created — something like
`https://abc123xyz.execute-api.us-east-1.amazonaws.com` — with TLS already handled by AWS.
The internal ALB falls back to a plain HTTP:80 listener when no certificate is supplied
(see `modules/alb`); it's never internet-reachable either way, since its only caller is
the API Gateway VPC Link inside the VPC. `staging`/`production` still use real domains
via the `dns` module — this is a `demo`-specific simplification, not a change to how the
other environments work.

**Cost estimate for 6 days (144 hours), at on-demand us-east-1 rates — order of magnitude,
not a quote:**

| Line item | Rate | 6 days |
|---|---|---|
| NAT Gateway ×1 | ~$0.045/hr | ~$6.50 |
| Fargate (0.25 vCPU / 0.5GB) | ~$0.0123/hr | ~$1.77 |
| RDS `db.t4g.micro`, single-AZ | ~$0.016/hr + ~20GB storage | ~$2.75 |
| ALB + API Gateway | ~$0.0225/hr (ALB) + per-request (API GW, negligible at demo volume) | ~$3.25 |
| **Total** | | **~$14–15** |

Compare that to running the *default* `staging` sizing for the same 6 days — 2 NAT gateways
alone would already roughly double the NAT line above, before the larger RDS instance and
extra Fargate tasks are even counted. The `az_count = 1` change is what actually makes this
"minimal cost," more than any single service-level downsize.

```bash
cd terraform/environments/demo
# No terraform.tfvars needed — every variable here has a sensible default.
terraform init
terraform plan
terraform apply

# Once apply finishes, the api_url output is the URL to hit — no DNS wait,
# works immediately:
terraform output api_url

# ... use it ...

# Tear down well within your window — don't let it run past what you need:
terraform destroy
```

`terraform destroy` removes everything this environment created, including the RDS
instance — `deletion_protection` is `false` and `skip_final_snapshot` is `true` here
specifically so destroy isn't blocked and doesn't sit taking a final snapshot you don't
need (staging/production keep both protections on; this environment deliberately doesn't).
No domain to worry about losing here — there isn't one.

## New Relic APM (optional, per-environment)

The `ecs` module takes an optional `new_relic_license_key` variable (default unset). Supply
it via `TF_VAR_new_relic_license_key` (never a committed `.tfvars`) and `apply` creates a
Secrets Manager secret for it, scopes the execution role's IAM policy to read just that one
secret, and wires `JAVA_TOOL_OPTIONS=-javaagent:/app/newrelic/newrelic.jar` plus
`NEW_RELIC_APP_NAME`/`NEW_RELIC_LOG` into the container. Leave it unset and none of that
exists — the agent stays completely dormant.

The New Relic Java agent itself is fetched in its own `Dockerfile` stage, gated behind a
build arg (`INCLUDE_NEW_RELIC`, default `false`) rather than always-on:

- **Local `docker compose up --build`** — arg stays at its default, so the build never
  downloads the ~38MB agent zip at all. A plain local eval has zero dependency on New
  Relic's CDN being reachable.
- **`scripts/deploy-backend.sh`** and **`.github/workflows/ci-cd.yml`** — both explicitly
  pass `--build-arg INCLUDE_NEW_RELIC=true`, since those are the only paths that produce
  images actually destined for an environment where the agent might be activated.

Bundling is unconditional on those two paths (whether or not that particular environment
has a license key set) so there's one image-build recipe per path, not a "monitored" and
"unmonitored" variant to keep in sync — activation is what's actually conditional, gated by
`JAVA_TOOL_OPTIONS` only being set when a key is configured.

If you enable this for `demo`, note the task needed bumping from Fargate's minimum
(256 CPU/512MB) to 512 CPU/1024MB, and `health_check_grace_period_seconds` /
`startPeriod` on the container's own health check both went to 180s — New Relic's
bytecode instrumentation at JVM startup is CPU-bound and measurably slow (~105-125s wall
clock observed) at the smaller size; both defaults in `modules/ecs` already reflect this.

## Getting the app running once infrastructure exists

Terraform builds the cluster, service, task definition, and everything around them — but
the task definition's `image_tag` defaults to the placeholder `REPLACE_WITH_COMMIT_SHA`
(see `variables.tf` in the `ecs` module), and the `aws_ecs_service` resource deliberately
ignores drift on `task_definition` and `desired_count` after the first apply. That's not
an oversight: `.github/workflows/ci-cd.yml` owns rolling out new task definition revisions
day to day (build → push to ECR → render task definition → deploy). Terraform owns the
shape around it — cluster, networking, autoscaling, IAM — not which exact revision is
running at any given moment. Point the CI/CD pipeline's `ECR_REPOSITORY` and cluster/service
names at what this Terraform created, and pushes to `main` take it from there.

## What's deliberately not built yet

- **Production blue/green via CodeDeploy.** Both environments currently get a plain
  rolling `ECS`-controller deployment. `deploy/ecs/appspec.yaml` and the CodeDeploy
  strategy described in [`docs/05-CICD.md`](../docs/05-CICD.md) §3 (bake period, listener
  cutover, auto-rollback) are a real follow-up on top of the `ecs` module — a second
  target group, a test listener rule on the ALB, and `aws_codedeploy_app` /
  `aws_codedeploy_deployment_group` resources — not yet wired into Terraform.
- **WAF** in front of API Gateway. Not yet added; the throttling in the `api-gateway`
  module (steady-state + burst limits) is the only load-shedding in place today.
- **Cross-region DR** (`docs/13-Disaster-Recovery-Failover.md`). This Terraform builds one
  region. The warm-standby cross-region replica described there is a separate, larger
  piece of work.

## Verifying this actually works before you spend money

```bash
cd terraform/environments/<shared|staging|production>
terraform init -backend=false   # skips remote state, just downloads providers
terraform validate
terraform fmt -check -recursive
```

All three environments pass `validate` and `fmt -check` as of the last time this was run.
That confirms the HCL is syntactically correct and every module's inputs/outputs actually
line up — it does **not** confirm the plan is what you want, or that account-level details
(service quotas, existing resource name collisions, IAM permissions) won't surface once you
run a real `plan` against your account. Read the `plan` output carefully the first time.

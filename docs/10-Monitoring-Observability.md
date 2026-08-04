# Monitoring & Observability

Combines the "monitoring" and "observability / New Relic / Splunk / reporting tool" asks —
they're one concern in practice: knowing whether the system is healthy, and being able to
answer why when it isn't.

**Implementation status:** New Relic APM — metrics, error tracking, distributed tracing,
and application logs forwarded in trace context — is actually built and running in the
live `demo` environment, not just designed; see §1a for how. CloudWatch Logs and the
`/actuator/health` checks behind ECS and the ALB are live too. **Splunk** forwarding,
PagerDuty paging, the business-reporting dashboard, and synthetic monitoring remain
design-only, the same "not yet built" status as the rest of this document unless called
out otherwise.

## 1. The three pillars, and where each lives

| Pillar | Tool | Purpose |
|---|---|---|
| Metrics | New Relic APM + CloudWatch | SLO tracking, autoscaling triggers, alerting |
| Logs | Splunk (via CloudWatch Logs → Splunk forwarder, or Firehose) | Structured JSON logs, correlation-ID tracing across a request, audit-adjacent search |
| Traces | New Relic distributed tracing | Request path from API Gateway → ALB → ECS → RDS, latency breakdown per hop |

```mermaid
flowchart LR
    ECS[ECS Fargate tasks] -->|Micrometer + New Relic agent| NR[New Relic APM]
    ECS -->|JSON stdout| CWL[CloudWatch Logs]
    CWL -->|subscription filter| SPL[Splunk]
    ALB -->|access logs| S3[S3 access log bucket]
    RDS[(RDS)] -->|Performance Insights| NR
    NR --> AL[Alerting: PagerDuty/Slack]
    SPL --> AL
```

## 1a. How the New Relic agent actually gets there

- **Fetched at build time, activated at deploy time.** The Docker image has its own build
  stage (`newrelic-agent` in the `Dockerfile`) that downloads the New Relic Java agent and
  copies it to `/app/newrelic`. It's gated on a build arg, `INCLUDE_NEW_RELIC`, which
  defaults to **false**: CI and `terraform/scripts/deploy-backend.sh` pass
  `--build-arg INCLUDE_NEW_RELIC=true`, while a local `docker compose up --build` doesn't,
  and gets an empty directory instead. That default is deliberate — evaluating this project
  offline shouldn't depend on reaching New Relic's CDN for ~38MB producing a file nothing
  local would load anyway. Even when present, the agent is inert unless
  `JAVA_TOOL_OPTIONS=-javaagent:/app/newrelic/newrelic.jar` is set, which only happens in
  environments that opt in (below) — so activation is a two-key decision: the agent has to
  be in the image *and* the environment has to ask for it.
- **Per-environment opt-in, not global.** `terraform/modules/ecs` takes an optional
  `new_relic_license_key` variable (default `null`). When set, it creates a Secrets Manager
  secret (`ark-fund-api/{environment}/new-relic-license-key`), grants the task's execution
  role `secretsmanager:GetSecretValue` scoped to just that one secret (same least-privilege
  pattern as the three DB credentials), and adds `NEW_RELIC_LICENSE_KEY` (from the secret),
  `NEW_RELIC_APP_NAME`, `NEW_RELIC_LOG=stdout`, and the `JAVA_TOOL_OPTIONS` line above to
  the container definition. Leave the variable unset and none of this exists — no secret,
  no IAM grant, no env vars, agent stays dormant.
- **Agent logs land in the same place app logs do.** `NEW_RELIC_LOG=stdout` routes the
  agent's own connection/init logging through the same `awslogs` driver as the application
  (`/ecs/{environment}` log group) — one CloudWatch log group to check, not two.
- **Application logs are forwarded into New Relic, in trace context.**
  `NEW_RELIC_APPLICATION_LOGGING_ENABLED` and
  `NEW_RELIC_APPLICATION_LOGGING_FORWARDING_ENABLED` send the app's own log lines to New
  Relic's Logs UI decorated with `trace.id`/`span.id`, so a slow or failed transaction can
  be opened as a trace with its log lines already attached — no correlating by timestamp
  across two tools. This is agent-side forwarding and does not replace the CloudWatch
  stream, which stays as the vendor-independent copy (and, per §1, the intended path to
  Splunk).
- **Config is entirely environment variables, no `newrelic.yml` edits.** The agent reads
  `NEW_RELIC_LICENSE_KEY`/`NEW_RELIC_APP_NAME` directly; nothing environment-specific is
  baked into the image.

## 2. Key metrics and SLOs

Directly tied to the targets set in [02-Capacity-Planning.md](02-Capacity-Planning.md):

| Metric | SLO | Alert threshold |
|---|---|---|
| Report endpoint p95 latency | < 600ms | Page if > 800ms for 5 consecutive minutes |
| CRUD endpoint p95 latency | < 300ms | Page if > 500ms for 5 consecutive minutes |
| 5xx error rate | < 0.1% | Page if > 1% over any 5-minute window |
| Availability | 99.9% monthly | Page on 2 consecutive failed `/actuator/health` checks |
| ECS task count vs. desired | — | Alert if running < desired for > 2 minutes (deploy stuck or capacity issue) |
| RDS CPU / connections | < 80% sustained | Alert at 80%, page at 95% |
| RDS replica lag | < 5s | Alert if > 15s (affects report freshness, §5 of [08-Database-Design.md](08-Database-Design.md)) |
| ECR image scan | — | Block deploy on critical/high CVE (see [09-Security.md](09-Security.md)) |

## 3. Logging

- **Structured JSON logs**, one line per event, shipped to Splunk via a CloudWatch Logs
  subscription filter — human-readable dashboards in New Relic for APM, full-text/field
  search in Splunk for incident investigation.
- **Correlation ID** generated at API Gateway (or accepted from an inbound
  `X-Correlation-Id` header) and propagated through every log line for a request, so a
  single failed report call can be traced end to end across the ALB access log, app log,
  and any downstream DB slow-query log.
- **What's logged:** request method/path/status/latency, tenant ID (`clientId`), and — for
  writes — the entity type and ID mutated. **What's never logged:** transaction amounts or
  investor PII in plaintext log lines; those live in the audit trail
  ([13-Disaster-Recovery-Failover.md](13-Disaster-Recovery-Failover.md)), which has
  stricter access control than general application logs.

## 4. Dashboards

| Dashboard | Audience | Contents |
|---|---|---|
| Platform health (New Relic) | Engineering / on-call | Latency percentiles, error rate, throughput, ECS task count, DB CPU/connections |
| Business reporting (QuickSight or Metabase, fed from the read replica) | Product / Ops leadership | Transactions posted per day, active clients, committed capital tracked, growth against the 450-manager / 70,000-LP baseline |
| Security/audit (Splunk) | Security / Compliance | Auth failures, cross-tenant access attempts (should be zero — any hit is an incident), admin-role actions |

The **business reporting tool** runs against the read replica, not the primary — it's
analytical, not operational, and shouldn't compete with production query load (same
routing principle as report endpoints, [08-Database-Design.md](08-Database-Design.md) §5).

## 5. Alerting and on-call

| Severity | Example | Response |
|---|---|---|
| P1 | 5xx rate > 1%, DB primary unreachable, availability SLO breach | Page on-call immediately, incident channel opened |
| P2 | Latency SLO breach sustained 5+ min, replica lag > 15s | Page on-call, no incident channel unless it escalates |
| P3 | Elevated (not breaching) latency, single task restart | Slack notification, reviewed next business day |

Alert routing: New Relic/CloudWatch alarms → PagerDuty → on-call engineer, with a Slack
mirror for visibility. Runbook links are attached to each alert definition so the
on-call engineer's first step is never "figure out what this means."

## 6. Synthetic monitoring

A scheduled synthetic check (New Relic Synthetics or CloudWatch Synthetics) runs the
README's own example flow every few minutes against production — create-adjacent-safe
reads (`GET /reports/portfolio` against a known seeded client) rather than writes, so it
verifies the full path (Route 53 → API Gateway → ALB → ECS → RDS) without polluting data.

## 7. What good looks like, 90 days after launch

- Every alert has fired at least once in a controlled game-day exercise (see
  [13-Disaster-Recovery-Failover.md](13-Disaster-Recovery-Failover.md) §5), so the first
  time it fires for real isn't also the first time anyone has seen it.
- p95 report latency dashboard shows the quarter-end spike predicted in
  [02-Capacity-Planning.md](02-Capacity-Planning.md) §3, confirming (or correcting) the
  assumption it was based on.
- Zero cross-tenant access attempts logged — the one metric where "boringly zero" is the
  entire point.
# Resiliency & Scalability

## 1. Failure modes and mitigations

| Failure | Mitigation | Detail |
|---|---|---|
| Single ECS task crashes | ALB health check fails it out of rotation; ECS replaces it | Target group deregistration + task replacement, typically < 60s |
| Whole AZ becomes unavailable | Tasks and RDS standby are spread across 3 AZs | See [03-System-Architecture.md](03-System-Architecture.md) §3 |
| RDS primary fails | Multi-AZ automatic failover to standby | RPO ≈ 0 (synchronous replication), RTO ≈ 60–120s — detail in [13-Disaster-Recovery-Failover.md](13-Disaster-Recovery-Failover.md) |
| Traffic spike (quarter-end statement release) | Target-tracking autoscaling on `RequestCountPerTarget` | Policy sized in [02-Capacity-Planning.md](02-Capacity-Planning.md) §7 and [03](03-System-Architecture.md) §4 |
| Downstream dependency slow (RDS under load) | Connection pool timeout + circuit breaker (Resilience4j) rather than unbounded thread pile-up | §3 below |
| Retried client request after timeout | Idempotency key on `POST /transactions` | Phase 1 item, [04-Implementation-Plan.md](04-Implementation-Plan.md) |
| Bad deploy | Blue/green with auto-rollback on alarm | [05-CICD.md](05-CICD.md) §3 |
| Region-wide AWS event | Cross-region DR (warm standby) | [13-Disaster-Recovery-Failover.md](13-Disaster-Recovery-Failover.md) |

## 2. Autoscaling policy

Derived from [02-Capacity-Planning.md](02-Capacity-Planning.md):

```yaml
# ECS Service Auto Scaling — target tracking
MinCapacity: 4
MaxCapacity: 16
TargetTrackingPolicy:
  PredefinedMetric: ALBRequestCountPerTarget
  TargetValue: 120          # requests/min/target, keeps p95 latency inside SLO
  ScaleOutCooldown: 60s      # react fast to the quarter-end spike
  ScaleInCooldown: 300s      # avoid flapping once the spike subsides
```

A **scheduled scaling action** is also worth adding once real usage confirms the
quarter-end pattern predicted in [02](02-Capacity-Planning.md) §3 — pre-warming capacity
ahead of a known statement-release window is cheaper and safer than relying purely on
reactive autoscaling for a predictable spike.

## 3. Resilience patterns at the application layer

| Pattern | Where | Purpose |
|---|---|---|
| Connection pool bounds (HikariCP) | DB access | Prevents one slow query from exhausting all app threads |
| Statement timeout | DB queries | Reporting aggregations get a bounded max execution time so a pathological query can't hold a connection indefinitely |
| Circuit breaker (Resilience4j) | Any future outbound call (e.g., a future bank integration or notification service) | Fails fast instead of queuing requests behind a dead dependency; not yet needed for the current DB-only dependency graph but the library is a one-line addition when phase 2/3 introduces outbound calls |
| Bulkhead | ECS task-level (separate service if a heavy reporting workload is added) | Isolates report-generation load from CRUD latency if/when reports grow expensive enough to need it — not needed at today's aggregation-query cost |
| Graceful degradation | Reporting endpoints | A report query timeout returns a 503 with `Retry-After`, not a hung connection — client sees a clear signal rather than an opaque failure |

## 4. Database scalability

| Concern | Approach |
|---|---|
| Read scaling | Route report/list traffic to RDS read replica(s); write traffic (CRUD posts) stays on primary — matches the 90/10 read/write split in [02](02-Capacity-Planning.md) §5 |
| Write scaling | Single-writer Postgres primary is sufficient at the projected ~19,000 transactions/day (§2 of [02](02-Capacity-Planning.md)) — no sharding needed at this volume |
| Table growth | `transactions` reaches ~25M rows in 5 years (§2 of [02](02-Capacity-Planning.md)); existing indexes (`fund_id, transaction_date`, `investor_id, transaction_date`, `client_id, transaction_date`) keep reporting queries index-only. Range partitioning by `transaction_date` (yearly) is the next lever if a single partition starts dominating cache — proposed as a trigger-based decision, not built pre-emptively |
| Hot tenant isolation | A single very large client (e.g., a top-10 fund administrator) generating disproportionate load is still served by the same shared schema — the tenant-scoped indexes keep that client's queries cheap regardless of total table size, since Postgres uses the composite index to touch only that tenant's rows |

## 5. Concurrency correctness

- **Optimistic locking** (`@Version` on `Transaction`) prevents two concurrent `PUT`
  requests from silently overwriting each other's correction — the second writer gets a
  409 and must retry against the current state. Phase 1 item.
- **Negative balances remain allowed by design** (per the take-home README) — enforcing a
  non-negative constraint correctly under concurrent writes would require row-level
  locking on every transaction insert against a fund, which trades write throughput for a
  business rule the business hasn't confirmed it wants. Documented as an open question in
  [01-PRD.md](01-PRD.md) §10 rather than silently implemented.

## 6. Load shedding

At sustained load beyond the 16-task ceiling (i.e., autoscaling has maxed out and the ALB
queue is still growing), API Gateway's throttling (steady-state + burst limits) sheds
excess requests with a 429 before they reach an already-saturated ECS fleet — protecting
report-endpoint latency for requests that do get through, rather than degrading everyone
equally.
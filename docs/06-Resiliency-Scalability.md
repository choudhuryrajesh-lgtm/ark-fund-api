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
| Circuit breaker (Resilience4j) | `ReportingService`'s three report methods (fund/investor/portfolio) | **Built, not aspirational** — see §3a below. RDS is this API's only real external dependency, and reporting is its most DB-intensive read path; if RDS starts failing repeatedly, the breaker fails fast instead of every request piling onto an already-exhausted connection pool |
| Bulkhead | ECS task-level (separate service if a heavy reporting workload is added) | Isolates report-generation load from CRUD latency if/when reports grow expensive enough to need it — not needed at today's aggregation-query cost |
| Graceful degradation | Reporting endpoints | A report query timeout returns a 503 with `Retry-After`, not a hung connection — client sees a clear signal rather than an opaque failure |

### 3a. Circuit breaker configuration, explained

From `application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      database:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        ignore-exceptions:
          - com.ark.fundapi.exception.ResourceNotFoundException
          - com.ark.fundapi.exception.BusinessRuleException
```

A circuit breaker moves through three states:

- **CLOSED** — normal operation, every call goes through.
- **OPEN** — calls are rejected immediately (routed to the fallback) without attempting
  the real method at all — the actual "fail fast" behavior.
- **HALF_OPEN** — a probe state: let a few calls through to test whether the dependency
  recovered before deciding to fully close again or snap back open.

What each setting controls:

| Setting | Meaning |
|---|---|
| `sliding-window-type: COUNT_BASED` | Judges health over the last N *calls*, not a time window — more predictable than `TIME_BASED` for a low/bursty-traffic API, where a quiet period could otherwise look artificially healthy |
| `sliding-window-size: 10` | That N — only the most recent 10 calls are considered; older ones roll off |
| `minimum-number-of-calls: 5` | Guards against judging on too small a sample — even 2 failures out of 2 calls (100%) won't trip the breaker until at least 5 calls have happened |
| `failure-rate-threshold: 50` | Once ≥5 calls are in the window, ≥50% failing flips CLOSED → OPEN |
| `wait-duration-in-open-state: 10s` | How long the breaker stays OPEN — rejecting everything immediately — before it's willing to test again |
| `automatic-transition-from-open-to-half-open-enabled: true` | The breaker moves itself OPEN → HALF_OPEN after that 10s, without needing an incoming request to trigger it |
| `permitted-number-of-calls-in-half-open-state: 3` | Only 3 real calls are let through as a probe; enough successes closes the breaker again, enough failures sends it back to OPEN for another 10s |
| `ignore-exceptions` | A separate axis: *what counts as a failure* at all. Without this, a 404 for a mistyped client/fund/investor UUID counts identically to RDS actually being down — a burst of ordinary client typos would incorrectly trip the breaker for everyone. `ResourceNotFoundException`/`BusinessRuleException` are expected, already-handled outcomes (mapped to their own HTTP statuses by `ApiExceptionHandler`), so they're excluded from the failure-rate calculation entirely |

**A subtlety worth knowing**: `ignore-exceptions` only stops an exception from counting
toward the failure rate — it does *not* stop Resilience4j's fallback method from being
invoked. The fallback method itself has to explicitly re-throw expected exceptions
(`rethrowIfExpected(...)` in `ReportingService`) or a plain 404 would incorrectly surface
as a 503. This was caught by testing against a running instance, not by unit tests alone
— worth remembering as a general lesson: a green test suite doesn't catch every
integration-level surprise, especially with AOP-based cross-cutting concerns like this.


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
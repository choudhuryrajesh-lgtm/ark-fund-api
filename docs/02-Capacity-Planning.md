# Capacity Planning — RPS, Latency, Throughput

Ark publishes four numbers that matter here: **450+ fund managers**, **10+ fund
administrators**, **70,000+ LP users**, **$150B+ committed capital**. Nothing else about
real traffic is public, so every number below is derived from those four inputs plus a
stated assumption. The point of this document is that the assumptions are visible and
arguable, not that the numbers are precise — they exist to size ECS task counts, RDS
instance class, and autoscaling policy in [03-System-Architecture.md](03-System-Architecture.md).

## 1. Population assumptions

| Quantity | Assumption | Reasoning |
|---|---|---|
| Fund managers (tenants) | 450 | Given |
| Fund administrators | 10 | Given |
| Avg funds per manager | 3 | Emerging GPs run 1 fund; larger firms run fund families. 3 is a conservative blended average. |
| **Total funds** | **≈ 1,500** | 450 × 3 |
| LP users | 70,000 | Given |
| Avg funds per LP | 1.5 | Most LPs concentrate in one manager relationship; some diversify across 2–3. |
| **Investor–fund relationships** | **≈ 105,000** | 70,000 × 1.5 |
| Committed capital | $150B | Given |
| **Avg commitment per relationship** | **≈ $1.4M** | $150B / 105,000 — sanity-checks against a mix of institutional LPs (tens of millions) and smaller LPs (low hundreds of thousands) |

## 2. Transaction volume

Fund accounting posts in batches, not continuously: management fees post monthly,
distributions and capital calls post quarterly-ish, expenses are ad hoc.

| Assumption | Value |
|---|---|
| Avg transactions posted per investor–fund relationship per month | 4 (mgmt fee, occasional call/distribution, occasional expense allocation) |
| **Transactions/month (steady state)** | 105,000 × 4 ≈ **420,000** |
| **Transactions/day** | 420,000 / 22 business days ≈ **19,000/day** |
| **Ledger size after 5 years** | 420,000 × 60 months ≈ **25M rows** (before archival) |

This is the number that justifies index design and partitioning strategy in
[08-Database-Design.md](08-Database-Design.md) — 25M rows is trivial for a well-indexed
Postgres table, but it rules out unindexed full scans for reports, which the current
schema already avoids (grouped aggregation queries, `idx_transactions_{fund,investor,client}_date`).

## 3. Concurrency model

Two very different user populations hit this API:

| Population | Count | Peak concurrency assumption | Peak concurrent users |
|---|---|---|---|
| Back-office (fund admin + GP staff) | 450 managers × ~5 staff + 10 admins × ~20 staff | ~40% active during business hours peak | ≈ (2,250 + 200) × 0.4 ≈ **980** |
| LP portal (future) | 70,000 | 2% baseline concurrent, spiking to 8% right after a quarterly statement email blast | Baseline ≈ **1,400**, spike ≈ **5,600** |

**Worst case peak concurrency ≈ 980 + 5,600 ≈ 6,600 concurrent sessions**, occurring in a
narrow window right after quarter-end statements are released — a predictable, schedulable
spike rather than a random one.

## 4. Requests per second

Assume an average think-time of 8–10 seconds between requests per active session (page
navigation, report fetch, list scroll — this is a back-office tool, not a chat app).

```
RPS ≈ concurrent_sessions / avg_think_time_seconds
```

| Scenario | Concurrency | Think time | RPS |
|---|---|---|---|
| Steady state (business hours, no spike) | ~2,400 | 10s | **~240 RPS** |
| Quarter-end statement spike | ~6,600 | 8s | **~825 RPS** |
| Design target (2× spike headroom) | — | — | **~1,650 RPS** |

**Design capacity target: 2,000 RPS**, rounded up from the 2× headroom figure, used as the
autoscaling ceiling in [03-System-Architecture.md](03-System-Architecture.md) and
[06-Resiliency-Scalability.md](06-Resiliency-Scalability.md).

## 5. Read/write ratio

Back-office fund accounting is read-heavy: staff pull reports and review positions far
more often than they post transactions.

| Traffic type | Share | Basis |
|---|---|---|
| Reads (list/get/report endpoints) | **90%** | Reporting endpoints (fund/investor/portfolio) plus list/get CRUD dominate normal usage |
| Writes (create/update/delete) | **10%** | Transaction posting is batch-oriented, not per-request-heavy |

This ratio drives the read replica decision in [08-Database-Design.md](08-Database-Design.md) —
routing report reads to a replica removes ~90% of query volume from the primary.

## 6. Latency SLOs

| Endpoint class | p50 | p95 | p99 | Why |
|---|---|---|---|---|
| Simple CRUD (get/create/update client, fund, investor, transaction) | 50ms | 300ms | 800ms | Single-row lookups/writes, indexed by PK or tenant-scoped unique key |
| List endpoints (paginated) | 80ms | 350ms | 900ms | Indexed, paginated, bounded page size |
| Reports (fund/investor/portfolio) | 150ms | 600ms | 1,500ms | Grouped aggregation queries; heavier but still single-pass per README's design (one grouped query per report, not a per-row loop) |

**Availability SLO: 99.9% monthly** (≈43 minutes downtime budget) — appropriate for a B2B
back-office system where a short outage is recoverable, but not a system where minutes of
downtime are invisible. See [06-Resiliency-Scalability.md](06-Resiliency-Scalability.md)
for how this is achieved (multi-AZ ECS + RDS) and
[10-Monitoring-Observability.md](10-Monitoring-Observability.md) for how it's measured.

## 7. Throughput → infrastructure sizing (headline numbers, detail in 03)

| Input | Value |
|---|---|
| Design RPS target | 2,000 |
| Estimated RPS per Fargate task (1 vCPU / 2GB, this workload) | ~150, conservative for a Spring Boot CRUD+aggregation API at the latency SLOs above |
| **Tasks needed at peak** | 2,000 / 150 ≈ **14 tasks** |
| **Tasks at steady state** | 240 / 150 ≈ **2 tasks**, floor raised to **4** for AZ redundancy and rolling-deploy headroom |
| DB primary sizing | Sized for the 10% write share + steady reporting; `db.r6g.xlarge` baseline, detailed in [08](08-Database-Design.md) |
| DB read replica(s) | Sized for the 90% read share, absorbing report traffic |

Full ECS/ALB/autoscaling configuration derived from these numbers is in
[03-System-Architecture.md](03-System-Architecture.md) §4 and
[06-Resiliency-Scalability.md](06-Resiliency-Scalability.md) §2.

## 8. Growth headroom

Ark's stated trajectory (98% retention, active new-logo growth) means capacity planning
for *today's* 450 managers and re-deriving these numbers every 6–12 months is the right
cadence — not over-provisioning for an assumed 5-year future today. The autoscaling design
in §7 handles 3–4× organic growth without a re-architecture; beyond that, the read-replica
count and Fargate task ceiling are the two knobs to revisit first.
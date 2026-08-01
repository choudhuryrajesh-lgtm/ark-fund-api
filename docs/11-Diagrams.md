# Diagram Index

All diagrams are written as Mermaid so they render directly in GitHub and stay in version
control as text, not as images that drift from the system they describe. This page is a
map of what's where, plus two diagrams that don't belong inside any single doc.

| Diagram | Type | Location |
|---|---|---|
| Context diagram (users → edge → compute → data) | flowchart | [03-System-Architecture.md](03-System-Architecture.md) §1 |
| Network / VPC layout | flowchart | [03-System-Architecture.md](03-System-Architecture.md) §3 |
| Request path, end to end | sequence | [03-System-Architecture.md](03-System-Architecture.md) §6 |
| Entity relationship diagram | erDiagram | [08-Database-Design.md](08-Database-Design.md) §1 |
| Auth flow | sequence | [09-Security.md](09-Security.md) §1 |
| Observability data flow | flowchart | [10-Monitoring-Observability.md](10-Monitoring-Observability.md) §1 |
| CI/CD pipeline | flowchart | [05-CICD.md](05-CICD.md) §1 |
| Phased roadmap | gantt | [04-Implementation-Plan.md](04-Implementation-Plan.md) §"Sequencing rationale" |

## Tenancy model (today vs. phase 3)

```mermaid
flowchart TB
    subgraph Today["Today — flat tenancy"]
        C1[Client] --> F1[Fund]
        C1 --> I1[Investor]
        F1 --> T1[Transaction]
        I1 --> T1
    end

    subgraph Phase3["Phase 3 — two-tier org model"]
        A[Fund Administrator<br/>org_type=FUND_ADMINISTRATOR] -->|manages| G1[Fund Manager<br/>org_type=FUND_MANAGER]
        A -->|manages| G2[Fund Manager]
        G1 --> F2[Fund]
        G2 --> F3[Fund]
        F2 --> T2[Transaction]
        F3 --> T3[Transaction]
    end
```

`Client` becomes `Organization` with a `type` discriminator and an optional
`managed_by_org_id` — existing rows default to `FUND_MANAGER` with no parent, so this is a
non-breaking evolution of the current schema (detail in
[04-Implementation-Plan.md](04-Implementation-Plan.md) phase 3).

## Transaction lifecycle

```mermaid
stateDiagram-v2
    [*] --> Posted: POST /transactions\n(amount > 0, date >= fund inception)
    Posted --> Corrected: PUT /transactions/{id}\n(type, amount, date, notes only)
    Corrected --> Corrected: further correction
    Posted --> Deleted: DELETE /transactions/{id}
    Corrected --> Deleted: DELETE /transactions/{id}
    Deleted --> [*]

    note right of Corrected
        fund_id and investor_id are immutable.
        Re-pointing a transaction would silently
        rewrite two parties' reported history —
        the correct fix is a reversing entry
        plus a new one, matching what an
        auditor expects to see.
    end note
```

## Reporting aggregation shape

```mermaid
flowchart LR
    subgraph Input
        T[transactions table<br/>~25M rows @ 5yr]
    end
    T -->|GROUP BY fund_id, type<br/>WHERE transaction_date <= asOfDate| A[Totals by type]
    T -->|GROUP BY fund_id, investor_id<br/>WHERE transaction_date <= asOfDate| B[Per-investor positions]
    A --> R[Fund report response]
    B --> R
```

One grouped query per breakdown, not a per-party loop — the mechanism that keeps report
latency inside the SLO in [02-Capacity-Planning.md](02-Capacity-Planning.md) §6 regardless
of how many transactions a fund accumulates.
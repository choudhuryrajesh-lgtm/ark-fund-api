# Product Requirements Document — Ark Fund API

| | |
|---|---|
| **Product** | Ark Fund Accounting & LP Reporting API |
| **Author** | Rajesh Choudhury (candidate submission) |
| **Status** | MVP delivered, phase 2 proposed |
| **Audience** | Engineering, Product, Fund Operations |

---

## 1. Problem statement

Private fund managers (GPs) and the fund administrators who service them run their back
office on spreadsheets, email, and point solutions that take months to stand up and can't
adapt to a fund's actual workflow. Ark replaces that with a cloud platform that deploys in
weeks: clients sign up, add their funds and investors, and immediately get a system of
record for capital movement and LP reporting.

This document scopes the **core ledger and reporting API** — the system of record that
everything else in Ark (portal, statements, capital call workflow) is built on top of.

## 2. Goals

| Goal | Metric |
|---|---|
| Clients can self-serve fund/investor setup without IT involvement | Time from signup to first transaction booked |
| Every dollar moved is attributable to a fund, an investor, and a type | Zero unattributed transactions |
| Fund and investor positions are always answerable on demand | Report p95 latency (see [02-Capacity-Planning](02-Capacity-Planning.md)) |
| One client's data is never visible to another | Zero cross-tenant leaks (see [09-Security](09-Security.md)) |
| The platform is auditable after the fact | Every mutation reconstructable (see [13-Disaster-Recovery-Failover](13-Disaster-Recovery-Failover.md)) |

## 3. Non-goals (this iteration)

- LP-facing portal UI (API-only; portal is a separate front-end consuming this API)
- Double-entry general ledger / chart of accounts (ArkGL proper — see
  [04-Implementation-Plan](04-Implementation-Plan.md) phase 3)
- Capital call workflow (notices, due dates, partial funding, waterfalls)
- Bank integration / cash reconciliation
- Authentication provider integration (design is specified in
  [09-Security.md](09-Security.md); wiring a real IdP is phase 2)

## 4. Personas

| Persona | Who | Primary need |
|---|---|---|
| **Fund Administrator ops user** | Back-office staff at one of Ark's 10+ admin firms, servicing many GP clients | Book transactions accurately and fast across many clients; never mix up a client's data |
| **Fund Manager (GP) staff** | Controller/accountant at one of 450+ managing firms | See their fund's balance and investor positions at any point in time, including historical restatements |
| **Limited Partner (LP)** | One of 70,000+ investors | See their own position and statements — not modeled as an API consumer in this iteration, but the data shape must support it later |

## 5. User stories

**Client & fund setup**
- As a fund admin, I can create a client record for a new GP relationship.
- As an ops user, I can create a fund under a client with a name and inception date.
- As an ops user, I can create an investor under a client so they can be linked to funds via transactions.

**Transactions**
- As an ops user, I can record a transaction (contribution, interest income, distribution,
  general expense, or management fee) against a fund for an investor, on a specific date,
  for a specific amount.
- As an ops user, I can correct a transaction's type, amount, date, or notes — but not
  re-point it to a different fund or investor (that requires a reversing entry, matching
  how an auditor expects to see a correction).
- As an ops user, I cannot book a transaction dated before the fund's inception date.
- As an ops user, I cannot delete a fund or investor that has transaction history.

**Reporting**
- As a GP controller, I can pull a fund report showing balance, totals by transaction type,
  and each investor's position in that fund.
- As a GP controller, I can pull the same report as of a past date, to reproduce what was
  reported before a back-dated correction landed.
- As an LP (via a future portal), I can see my total position across every fund I
  participate in.
- As a fund admin, I can pull a portfolio rollup across every fund a client manages.

## 6. Functional requirements

Full detail in [07-API-Documentation.md](07-API-Documentation.md). Summary:

| Capability | Operations |
|---|---|
| Clients | Create, list, read, update, delete |
| Funds | Create, list, read, update, delete (blocked if transactions exist) |
| Investors | Create, list, read, update, delete (blocked if transactions exist) |
| Transactions | Create, list (filterable by fund/investor), read, update (type/amount/date/notes only), delete |
| Reports | Fund report, investor report, portfolio report — all support `asOfDate` |

## 7. Non-functional requirements

| Category | Requirement | Detail |
|---|---|---|
| Performance | p95 report latency < 600ms, p95 CRUD latency < 300ms at projected peak load | [02-Capacity-Planning](02-Capacity-Planning.md) |
| Availability | 99.9% monthly | [06-Resiliency-Scalability](06-Resiliency-Scalability.md) |
| Data integrity | Money is exact (`BigDecimal`/`NUMERIC(19,2)`); amount always positive, direction from type | Enforced in schema + domain layer |
| Multi-tenancy | Total isolation between clients; cross-tenant access returns 404, not 403 | [09-Security.md](09-Security.md) |
| Auditability | Every write attributable to who/what/when/previous-value | [13-Disaster-Recovery-Failover](13-Disaster-Recovery-Failover.md) |
| Scalability | Support 450+ managers, 70,000+ LPs, $150B+ committed capital without re-architecture | [02](02-Capacity-Planning.md), [03](03-System-Architecture.md) |

## 8. Business rules (already enforced)

1. Amounts are stored positive; credit/debit direction is derived from `TransactionType`.
2. A transaction cannot predate its fund's `inception_date`.
3. Funds and investors with transaction history cannot be hard-deleted (409).
4. A transaction's fund/investor cannot be changed after creation — only a correction to
   type, amount, date, or notes is allowed.
5. Fund names are unique per client (not globally); investor emails are unique per client.
6. A transaction's `client_id`, `fund_id`, and `investor_id` must all resolve to the same
   tenant — a fund and investor from different clients can never be linked.

## 9. Success metrics (post-launch, platform-level)

These are Ark's actual stated business outcomes this API is in service of:

- $150B+ in committed capital tracked without a reconciliation break
- 450+ fund managers onboarded without a bespoke integration per client
- 70,000+ LP users able to self-serve their statements (once the portal ships)
- 98% client retention — directly threatened by ledger correctness or downtime incidents

## 10. Open questions for the business

Carried over from the take-home README, still valid at platform scale:

- Should negative fund balances be blocked, or is running negative between a distribution
  and a capital call legitimate? (Currently allowed — see
  [06-Resiliency-Scalability.md](06-Resiliency-Scalability.md) for the row-locking
  implication of changing this.)
- Is a formal subscription/commitment step required before an investor can transact, or
  does participation continue to derive purely from the ledger? (See
  [04-Implementation-Plan.md](04-Implementation-Plan.md) phase 2 — commitments are
  proposed as an additive layer, not a replacement.)

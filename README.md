# Ark Fund API

A REST API for investment management and reporting. Clients (tenants) manage funds and
investors; investors interact with funds through transactions, and the API reports positions
at the fund, investor, and portfolio level.

Built with Java 21, Spring Boot 3.3, PostgreSQL, and Flyway.

This repository is the working implementation of the take-home brief. The `docs/` folder
extends it into what the same problem looks like at Ark's actual scale — 450+ fund
managers, 70,000+ LP users, $150B+ in committed capital — covering the product, capacity,
architecture, and operational documentation a production launch would need on top of this
code.

## Documentation

| Doc | Covers |
|---|---|
| [01 · Product Requirements](docs/01-PRD.md) | Problem, personas, user stories, functional/non-functional requirements |
| [02 · Capacity Planning](docs/02-Capacity-Planning.md) | RPS, latency, throughput — derived from Ark's published business metrics |
| [03 · System Architecture](docs/03-System-Architecture.md) | AWS HLD: Route 53 → API Gateway → ALB → ECS Fargate → RDS |
| [04 · Implementation Plan](docs/04-Implementation-Plan.md) | Phased roadmap from this MVP to commitments, LP portal, org hierarchy, full GL |
| [05 · CI/CD](docs/05-CICD.md) | Pipeline design; pipeline itself at [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) |
| [06 · Resiliency & Scalability](docs/06-Resiliency-Scalability.md) | Failure modes, autoscaling policy, resilience patterns |
| [07 · API Documentation](docs/07-API-Documentation.md) | Endpoint reference, examples, error format, versioning |
| [08 · Database Design](docs/08-Database-Design.md) | Schema, ERD, indexing, partitioning, migration strategy |
| [09 · Security](docs/09-Security.md) | AuthN/AuthZ design, tenant isolation, OWASP mapping, compliance posture |
| [10 · Monitoring & Observability](docs/10-Monitoring-Observability.md) | New Relic, Splunk, dashboards, alerting, on-call |
| [11 · Diagrams](docs/11-Diagrams.md) | Index of every diagram plus tenancy/lifecycle diagrams that don't belong in one doc |
| [12 · AWS Deployment](docs/12-AWS-Deployment.md) | Environments, IaC outline, ECS configuration, deployment runbook |
| [13 · Disaster Recovery & Failover](docs/13-Disaster-Recovery-Failover.md) | Multi-AZ failover, backups, cross-region DR, game-day testing |

The live application below is the actual Phase 0 deliverable ([04-Implementation-Plan](docs/04-Implementation-Plan.md));
everything above documents how it extends into a production Ark deployment.

---

## Running it

One command, from a clean checkout. No local Java or Maven required — the build happens
inside Docker.

```bash
./run.sh
```

This builds the image, starts PostgreSQL and the API, waits until the API is actually
responding (not just "container started"), then prints the Swagger UI URL and a couple of
ready-to-run `curl` examples against the pre-seeded demo data.

Equivalent, if you'd rather run it directly:

```bash
docker compose up --build
```

That starts PostgreSQL, applies the schema migrations, loads demo data, and serves the API on
**http://localhost:8083**.

If port 8083 is already in use:

```bash
API_PORT=8090 docker compose up --build
# or: API_PORT=8090 ./run.sh
```

**Verify it's up:**

```bash
curl http://localhost:8083/actuator/health
```

**Explore the API interactively:** http://localhost:8083/swagger-ui.html

**Demo UI (optional, bonus):** http://localhost:3000 — a minimal React app exercising the
API end to end (create clients/funds/investors, record transactions, view reports). Not
part of the graded submission — the brief explicitly says a front end isn't required.
Starts automatically with `./run.sh` / `docker compose up --build`; see
[`frontend/README.md`](frontend/README.md) for details.

**Shut down** (add `-v` to also drop the database volume):

```bash
docker compose down
```

### Running the tests

Tests run against in-memory H2, so no Docker daemon is needed:

```bash
mvn verify
```

---

## Demo data

The application seeds one client with two funds, three investors, and thirteen transactions so
the reporting endpoints return meaningful numbers immediately.

| Entity | ID |
|---|---|
| Client — Meridian Capital Partners | `11111111-1111-1111-1111-111111111111` |
| Fund — Meridian Growth Fund I | `22222222-2222-2222-2222-222222222201` |
| Fund — Meridian Income Fund | `22222222-2222-2222-2222-222222222202` |
| Investor — Alice Nakamura (in both funds) | `33333333-3333-3333-3333-333333333301` |
| Investor — Brookfield Trust | `33333333-3333-3333-3333-333333333302` |
| Investor — Carlos Mendes | `33333333-3333-3333-3333-333333333303` |

To start with no demo business data instead, set `spring.flyway.target=2` (which skips the
seed migration but still applies the schema and the `transaction_types` reference data).

### Try it

```bash
CLIENT=11111111-1111-1111-1111-111111111111
FUND=22222222-2222-2222-2222-222222222201
INVESTOR=33333333-3333-3333-3333-333333333301

# Fund report — balance, totals by type, per-investor positions
curl "http://localhost:8083/api/v1/clients/$CLIENT/reports/funds/$FUND"

# The same fund as of a past date (before H2 fees and distributions were booked)
curl "http://localhost:8083/api/v1/clients/$CLIENT/reports/funds/$FUND?asOfDate=2024-06-30"

# Investor report — position across every fund they participate in
curl "http://localhost:8083/api/v1/clients/$CLIENT/reports/investors/$INVESTOR"

# Portfolio rollup across all of the client's funds
curl "http://localhost:8083/api/v1/clients/$CLIENT/reports/portfolio"

# Record a transaction
curl -X POST "http://localhost:8083/api/v1/clients/$CLIENT/transactions" \
  -H "Content-Type: application/json" \
  -d "{\"fundId\":\"$FUND\",\"investorId\":\"$INVESTOR\",\"type\":\"CONTRIBUTION\",\"amount\":25000.00,\"transactionDate\":\"2024-12-01\"}"
```

---

## Domain model

```
Client (tenant)
  ├── Fund       (many)
  ├── Investor   (many)
  └── Transaction (many) ──> references one Fund and one Investor
```

A fund has many investors and an investor may contribute to many funds. That many-to-many
relationship is expressed **through the transaction ledger** rather than a join table: the
association carries a date, an amount, and a type, none of which a plain join table can hold.
Deriving participation from transactions keeps one source of truth — an investor is in a fund
because there is money behind it.

### Transaction types

| Type | Effect |
|---|---|
| `CONTRIBUTION` | Credit |
| `INTEREST_INCOME` | Credit |
| `DISTRIBUTION` | Debit |
| `GENERAL_EXPENSE` | Debit |
| `MANAGEMENT_FEE` | Debit |

Amounts are always stored **positive**; direction comes from the type. Storing signed amounts
would make the sign and the type two sources of truth that can disagree. The rule lives in one
place (`TransactionType.applySign`).

Transaction types are governed reference data (the `transaction_types` table,
foreign-keyed from `transactions.type`), not a fixed enum — the business can add a new
type (a new fee category, a new kind of distribution) by inserting a row and classifying
it as a credit or a debit, with no code deploy. `GET /api/v1/transaction-types` lists the
types available for posting. See [08-Database-Design.md](docs/08-Database-Design.md) §2
for the full rationale, including why this is deliberately *not* a client-facing
self-service action.

---

## API

All endpoints are under `/api/v1` and scoped to a client.

| Method | Path | Description |
|---|---|---|
| `POST` `GET` | `/clients` | Create / list clients |
| `GET` `PUT` `DELETE` | `/clients/{clientId}` | Read / update / delete a client |
| `POST` `GET` | `/clients/{clientId}/funds` | Create / list funds |
| `GET` `PUT` `DELETE` | `/clients/{clientId}/funds/{fundId}` | Read / update / delete a fund |
| `POST` `GET` | `/clients/{clientId}/investors` | Create / list investors |
| `GET` `PUT` `DELETE` | `/clients/{clientId}/investors/{investorId}` | Read / update / delete an investor |
| `POST` `GET` | `/clients/{clientId}/transactions` | Record / list transactions (filter by `fundId` or `investorId`) |
| `GET` `PUT` `DELETE` | `/clients/{clientId}/transactions/{transactionId}` | Read / correct / delete a transaction |
| `GET` | `/clients/{clientId}/reports/funds/{fundId}` | Fund report |
| `GET` | `/clients/{clientId}/reports/investors/{investorId}` | Investor report |
| `GET` | `/clients/{clientId}/reports/portfolio` | Portfolio rollup |

List endpoints are paginated (`?page=0&size=20&sort=name,asc`). Every report accepts an
optional `?asOfDate=YYYY-MM-DD`.

Errors use [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807) `problem+json`:

```json
{
  "type": "https://api.ark.com/problems/business-rule-violation",
  "title": "Business rule violation",
  "status": 409,
  "detail": "Transaction date 2023-01-01 is before the fund's inception date 2024-02-01",
  "instance": "/api/v1/clients/.../transactions",
  "timestamp": "2026-07-29T22:03:42Z"
}
```

`400` for validation failures (with a per-field `errors` map), `404` for missing resources,
`409` for business-rule violations.

---

## Design decisions

**Tenancy is explicit in the URL and enforced on every lookup.** Funds, investors, and
transactions are nested under `/clients/{clientId}`, and every repository lookup is
`findByIdAndClientId` rather than `findById`. Requesting another client's resource returns
`404`, not `403` — a `403` would confirm the resource exists, which is itself a leak. The most
important case this closes: a transaction can never link a fund and an investor belonging to
different clients, because both are resolved through tenant-scoped loaders.

In production the client ID would come from an authenticated principal (JWT claim) rather than
the path. The path-based approach keeps the take-home runnable without an auth server while
making the isolation boundary visible and testable.

**Money is `BigDecimal`, never `double`.** Binary floating point cannot represent decimal
currency exactly, and the drift compounds across a ledger. Amounts are pinned to two decimal
places on write, and the database enforces `amount > 0`.

**Aggregation happens in the database.** Reports use grouped queries returning one row per
transaction type, not a full ledger load summed in Java. A fund with a million transactions
still returns a handful of rows. The per-investor and per-fund breakdowns use a single grouped
query each rather than a per-party loop, avoiding the classic reporting N+1.

**Reports carry their `asOfDate`.** Fund accounting gets restated — back-dated transactions
arrive after a period has been reported on. A balance without an effective date is ambiguous,
and reports get screenshotted and emailed, so the date travels with the number.

**Flyway owns the schema; Hibernate is set to `validate`.** Startup fails fast if the entities
and the migrated schema have drifted, rather than `ddl-auto: update` silently altering tables.

**Transactions can't be re-pointed at a different fund or investor.** `PUT` on a transaction
allows correcting type, amount, date, and notes — but not the parties. Re-pointing an existing
ledger entry silently rewrites two parties' reported history; the correct treatment is a
reversing entry plus a new one, which is what an auditor expects to see.

**Funds and investors with transactions cannot be deleted.** A fund with history is a
financial record, not a typo. Deleting it would orphan investor history, so it is refused with
a `409`.

### Things I decided *not* to do, and why

**Negative fund balances are allowed.** Real funds legitimately run negative between a
distribution and a capital call, so blocking it would encode a rule the business may not want.
Enforcing it correctly would also need row-level locking to be safe under concurrent writes.
This is the first question I'd take back to the business.

**No explicit subscription/commitment entity.** Real fund administration usually has a
subscription step where an investor commits to a fund before transacting. The brief describes
investors interacting with funds *through transactions*, so participation is derived from the
ledger. Adding commitments later is additive, not a rewrite.

**No authentication.** Out of scope for the brief. The tenancy boundary is built and tested,
so wiring it to a real principal is a small change (resolve `clientId` from the token instead
of the path).

---

## What I'd add for production

- **Authentication and authorisation** — OAuth2/JWT, with `clientId` resolved from the token
  and a `HandlerInterceptor` or method-level security enforcing it, replacing the path variable.
- **Append-only audit trail** — `created_at`/`updated_at` cover the basics, but financial
  records need "who changed what, when, and what was the previous value". A separate audit
  table (or Hibernate Envers) rather than in-place mutation.
- **Optimistic locking** (`@Version`) on transactions to prevent lost updates under concurrent
  edits.
- **Idempotency keys** on transaction creation, so a retried request after a network timeout
  cannot double-book a contribution.
- **Testcontainers** in CI to run the integration suite against real PostgreSQL rather than
  H2, catching dialect-specific behaviour the compatibility mode hides.
- **Observability** — Micrometer metrics, structured JSON logs with correlation IDs, and
  tracing across the request path.
- **Soft delete / lifecycle status** on funds (`ACTIVE`/`CLOSED`) instead of hard delete, so
  closing a fund preserves history.
- **Rate limiting and request-size caps** at the gateway.
- **Report caching** — portfolio rollups are read-heavy and change only when transactions are
  written, making them a natural fit for a cache invalidated on write.

---

## Project layout

```
src/main/java/com/ark/fundapi/
├── domain/       JPA entities, incl. TransactionType (reference data owning the credit/debit rule)
├── repository/   Spring Data repositories + reporting projections
├── service/      Business logic, validation, tenant enforcement
├── web/          REST controllers, DTOs, RFC 7807 exception handling
├── exception/    Domain exceptions
└── config/       OpenAPI configuration

src/main/resources/db/migration/
├── V1__initial_schema.sql
├── V2__transaction_types.sql
└── V3__demo_seed_data.sql

docs/                          Production documentation — see the index above
├── 01-PRD.md ... 13-Disaster-Recovery-Failover.md

deploy/ecs/                    ECS task definitions + CodeDeploy appspec (docs/12)
.github/workflows/ci-cd.yml    Build/test/deploy pipeline (docs/05)
```

DTOs are kept separate from entities deliberately: the wire contract and the persistence model
change for different reasons, and serialising entities directly risks lazy-loading surprises
and exposes columns never meant to be public.

## Test coverage

14 tests covering the reporting arithmetic and the tenant boundary — the two places where a
defect would be both silent and financially meaningful.

- Fund report aggregation, including that per-investor positions sum back to the fund balance
- Investor reports spanning multiple funds
- `asOfDate` filtering
- Funds with no transactions still appearing in the portfolio report
- Cross-tenant transaction creation rejected
- Reading another client's fund returns `404`
- Transaction dated before fund inception rejected
- Non-positive amounts rejected
- Deletion blocked for funds with transactions
- Duplicate fund names within a client rejected
- Credit/debit direction for every transaction type

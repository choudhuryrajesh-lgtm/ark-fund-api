# Ark Fund API

A multi-tenant REST API for fund accounting and investor reporting — the system of record
for capital moving between investors and the funds they hold.

Fund managers register their funds and investors, then record the transactions that move
money between them: contributions and interest income credit a fund; distributions,
general expenses and management fees debit it. From that ledger the API answers the two
questions the business actually asks — *what is this fund worth?* and *what is this
investor's position?* — as of today, or as of any date in the past.

**Java 21 · Spring Boot 3.3 · PostgreSQL · Flyway · Docker · Terraform · GitHub Actions · New Relic**

---

## Three ways to try it

All three exercise the same code. Option 2 is the brief's "single command from a clean
checkout"; option 1 needs nothing installed at all, if you'd rather just look first.

| | Option | Requires | Start here |
|---|---|---|---|
| **1** | **Live in AWS** | Nothing to install | [Swagger UI](https://524p1owhlc.execute-api.us-east-1.amazonaws.com/swagger-ui/index.html) · [Demo UI](https://d5rx4a862iikr.cloudfront.net/) |
| **2** | **Locally, one command** | Docker only | [`./run.sh`](#option-2--locally-in-one-command) or [`docker compose up --build`](#option-2--locally-in-one-command) |
| **3** | **Just the tests** | Maven, no Docker | [`mvn verify`](#option-3--run-the-tests) |

---

### Option 1 — Live in AWS (nothing to install)

The application is deployed and running. Both links are live right now:

| | URL |
|---|---|
| **API — Swagger UI** | **https://524p1owhlc.execute-api.us-east-1.amazonaws.com/swagger-ui/index.html** |
| **Demo UI** | **https://d5rx4a862iikr.cloudfront.net/** |

Or straight from a terminal:

```bash
API=https://524p1owhlc.execute-api.us-east-1.amazonaws.com

curl "$API/actuator/health"
curl "$API/api/v1/clients"
curl "$API/api/v1/clients/11111111-1111-1111-1111-111111111111/reports/portfolio"
```

This is a real deployment, not a static demo: **API Gateway → VPC Link → internal ALB →
ECS Fargate → RDS PostgreSQL (Multi-AZ)**, with the React UI on S3 + CloudFront. All of
it is provisioned by the Terraform in [`terraform/`](terraform/), and every push to `main`
redeploys it through [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) — build
→ test → image → deploy → **post-deploy smoke tests that must pass**. Details in
[12-AWS-Deployment.md](docs/12-AWS-Deployment.md) and [05-CICD.md](docs/05-CICD.md).

> It's a shared demo environment with seeded data, so feel free to create and delete
> records — you may see leftovers from someone else's poking around.

**Monitoring is live too.** The New Relic Java agent is attached at JVM start, so every
request you send to that API produces a real trace — latency percentiles, error rate, JVM
and GC health, and slow SQL down to the statement — with application logs forwarded
alongside and tagged with their trace ID. Container logs also go to CloudWatch
independently, and `/actuator/health` backs both the ECS and ALB health checks, so a task
that stops answering is replaced automatically. The agent is opt-in at build time and its
licence key comes from Secrets Manager, so nothing here runs or leaks locally — full
design in [10-Monitoring-Observability.md](docs/10-Monitoring-Observability.md).

---

### Option 2 — Locally, in one command

The brief asks for a single command from a clean checkout. This is it. **Docker is the
only prerequisite** — no local Java, no Maven, no PostgreSQL install; the build happens
inside the image.

Use **either** of these:

```bash
./run.sh                     # runs detached, waits until the API actually answers, prints the URLs
docker compose up --build    # runs in the foreground, logs stream to your terminal, Ctrl-C stops it
```

**They produce exactly the same stack** — `run.sh`'s first and only action on the
containers is literally `docker compose up --build -d`. Same image, same ports, same
migrations, same seed data. Nothing about the running system differs.

What `run.sh` adds is only what happens *around* that command. Running detached frees your
terminal — useful, since the `curl` examples below need one — but it also means you lose
the startup log as a readiness signal, and the API isn't serving for a few seconds after
the command returns. So the script polls `/actuator/health` until the API genuinely
answers, then prints the URLs and the examples; if it never comes up, it dumps the
container logs and tells you what to try.

Plain Compose in the foreground is in no way a lesser path — you watch Postgres, Flyway,
and Spring Boot start up, and the startup banner tells you when it's ready. Use whichever
you prefer.

Either way:

| | URL |
|---|---|
| **API — Swagger UI** | http://localhost:8083/swagger-ui/index.html |
| **Demo UI** | http://localhost:3000 |
| Health check | http://localhost:8083/actuator/health |

If those ports are taken (works with both commands):

```bash
API_PORT=8090 UI_PORT=3001 ./run.sh
API_PORT=8090 UI_PORT=3001 docker compose up --build
```

Shut it down when you're done (`-v` also drops the database volume):

```bash
docker compose down
```

---

### Option 3 — Run the tests

No Docker daemon needed — the unit and integration suites run against in-memory H2:

```bash
mvn verify
```

Three tiers, deliberately separated so the command above keeps its no-Docker guarantee:

| Tier | Command | Runs against |
|---|---|---|
| **Unit / integration** — 16 tests | `mvn verify` | In-memory H2 |
| **Component** — 5 Cucumber scenarios | `docker compose up -d db`<br>`mvn verify -Pcomponent-tests` | Real PostgreSQL, real HTTP layer |
| **Smoke** — the same 5 scenarios, black-box | `mvn verify -Psmoke-tests -Dsmoke.base.url=<url>` | A live, already-deployed instance |

The same Gherkin features serve the last two tiers: once against a real dependency, once
as a post-deploy gate CI runs against `demo` after every release — so a deploy is verified
by behaviour, not just a health check. **SonarCloud** takes JaCoCo coverage on every push
(advisory, not yet a merge gate).

Detail: [Test coverage](#test-coverage).

---

## How this maps to the brief

| The brief asks for | Where it is |
|---|---|
| Java | Java 21, Spring Boot 3.3 |
| Clients add **funds** and **investors** | Full CRUD under `/api/v1/clients/{clientId}/…` |
| A fund has **many investors**; an investor joins **many funds** | Many-to-many, expressed through the transaction ledger — [rationale](#domain-model) |
| A transaction has a **date**, an **amount**, a **type**, applied to a **fund**, executed by an **investor** | `transactions` table + `POST /clients/{id}/transactions` |
| The **five transaction types**, each a credit or a debit | [Transaction types](#transaction-types) — credit/debit is reference data, not hardcoded |
| **Typical CRUD operations** | Create / read / update / delete on clients, funds, investors, transactions — with financial-integrity limits on update and delete, [explained here](#design-decisions) |
| **Basic reporting for funds and investors** | Three reports: fund, investor, and a portfolio rollup — each with optional `asOfDate` |
| **Simple instructions to run it** | [Option 2](#option-2--locally-in-one-command) |
| **A single command** | Either `./run.sh` or `docker compose up --build` — your choice, same stack |
| **Everything the business would expect at the end of a dev cycle** | Tests at three tiers, CI/CD, IaC, a live deployment, and [13 engineering documents](#engineering-documentation) |
| Front end **not** required | Understood — the API stands on its own. One is [included anyway](#about-the-demo-ui), to make validation quicker |

---

## Engineering documentation

The brief asks for "everything the business would expect at the conclusion of the
development cycle." That is not just working code, so these 13 documents are part of the
deliverable rather than an appendix to it — the product, capacity, architecture, security
and operational thinking behind the API, sized for Ark's actual published scale (450+ fund
managers, 70,000+ LP users, $150B+ in committed capital).

| Doc | Covers |
|---|---|
| [01 · Product Requirements](docs/01-PRD.md) | Problem, personas, user stories, functional/non-functional requirements |
| [02 · Capacity Planning](docs/02-Capacity-Planning.md) | RPS, latency, throughput — derived from Ark's published business metrics |
| [03 · System Architecture](docs/03-System-Architecture.md) | AWS HLD: Route 53 → API Gateway → ALB → ECS Fargate → RDS |
| [04 · Implementation Plan](docs/04-Implementation-Plan.md) | Phased roadmap from this MVP to commitments, LP portal, org hierarchy, full GL |
| [05 · CI/CD](docs/05-CICD.md) | Pipeline design; the pipeline itself is [`.github/workflows/ci-cd.yml`](.github/workflows/ci-cd.yml) |
| [06 · Resiliency & Scalability](docs/06-Resiliency-Scalability.md) | Failure modes, autoscaling policy, circuit breakers |
| [07 · API Documentation](docs/07-API-Documentation.md) | Endpoint reference, examples, error format, versioning |
| [08 · Database Design](docs/08-Database-Design.md) | Schema, ERD, indexing, partitioning, migration strategy |
| [09 · Security](docs/09-Security.md) | AuthN/AuthZ design, tenant isolation, OWASP mapping, compliance posture |
| [10 · Monitoring & Observability](docs/10-Monitoring-Observability.md) | New Relic, Splunk, dashboards, alerting, on-call |
| [11 · Diagrams](docs/11-Diagrams.md) | Index of every diagram, plus tenancy/lifecycle diagrams that don't belong to one doc |
| [12 · AWS Deployment](docs/12-AWS-Deployment.md) | Environments, IaC, ECS configuration, deployment runbook |
| [13 · Disaster Recovery & Failover](docs/13-Disaster-Recovery-Failover.md) | Multi-AZ failover, backups, cross-region DR, game-day testing |

Infrastructure runbook: [`terraform/README.md`](terraform/README.md).

---

## About the demo UI

The brief doesn't require a front end; I built one deliberately, for two reasons.

It makes the API quick to validate: posting a contribution and watching the fund balance,
the investor's position, and the portfolio rollup all move together demonstrates the
ledger is coherent — faster than assembling the equivalent curl calls. And as a full-stack
engineer, it lets me show that side of my work rather than assert it.

A single-page React app covering the full flow — client selection, funds, investors,
transactions, and all three reports — with the API's RFC 7807 errors surfaced as inline
field messages rather than a generic failure. Plain React and `fetch`, no state library or
component kit, deployed by the same Terraform as the API (S3 + CloudFront). The API
remains the deliverable and stands on its own; every screen maps to an endpoint you can
hit directly in Swagger. See [`frontend/README.md`](frontend/README.md).

---

## Demo data

Seeded on startup: one client, two funds, three investors, thirteen transactions — so the
reporting endpoints return meaningful numbers the moment the app is up.

| Entity | ID |
|---|---|
| Client — Meridian Capital Partners | `11111111-1111-1111-1111-111111111111` |
| Fund — Meridian Growth Fund I | `22222222-2222-2222-2222-222222222201` |
| Fund — Meridian Income Fund | `22222222-2222-2222-2222-222222222202` |
| Investor — Alice Nakamura (in both funds) | `33333333-3333-3333-3333-333333333301` |
| Investor — Brookfield Trust | `33333333-3333-3333-3333-333333333302` |
| Investor — Carlos Mendes | `33333333-3333-3333-3333-333333333303` |

To start with an empty database instead, set `spring.flyway.target=2` — that skips the
seed migration while still applying the schema and the `transaction_types` reference data.

### Try it

```bash
API=http://localhost:8083          # or the AWS URL from Option 1
CLIENT=11111111-1111-1111-1111-111111111111
FUND=22222222-2222-2222-2222-222222222201
INVESTOR=33333333-3333-3333-3333-333333333301

# Fund report — balance, totals by type, per-investor positions
curl "$API/api/v1/clients/$CLIENT/reports/funds/$FUND"

# The same fund as of a past date, before the second-half fees and distributions were booked
curl "$API/api/v1/clients/$CLIENT/reports/funds/$FUND?asOfDate=2024-06-30"

# Investor report — their position across every fund they participate in
curl "$API/api/v1/clients/$CLIENT/reports/investors/$INVESTOR"

# Portfolio rollup across all of the client's funds
curl "$API/api/v1/clients/$CLIENT/reports/portfolio"

# Record a transaction, then re-run the fund report to see it land
curl -X POST "$API/api/v1/clients/$CLIENT/transactions" \
  -H "Content-Type: application/json" \
  -d "{\"fundId\":\"$FUND\",\"investorId\":\"$INVESTOR\",\"type\":\"CONTRIBUTION\",\"amount\":25000.00,\"transactionDate\":\"2024-12-01\"}"
```

---

## Domain model

```
Client (tenant)
  ├── Fund        (many)
  ├── Investor    (many)
  └── Transaction (many) ──> references one Fund and one Investor
```

A fund has many investors and an investor may contribute to many funds. That many-to-many
relationship is expressed **through the transaction ledger** rather than a join table: the
association carries a date, an amount, and a type — none of which a plain join table can
hold. Deriving participation from transactions keeps one source of truth: an investor is
in a fund because there is money behind it, not because a second table says so.

### Transaction types

| Type | Effect |
|---|---|
| `CONTRIBUTION` | Credit |
| `INTEREST_INCOME` | Credit |
| `DISTRIBUTION` | Debit |
| `GENERAL_EXPENSE` | Debit |
| `MANAGEMENT_FEE` | Debit |

Amounts are always stored **positive**; direction comes from the type. Signed amounts
would make the sign and the type two sources of truth that can disagree. Direction is held
in exactly one place — `transaction_types.direction`, read through `TransactionType` — so
both the per-transaction signed amount (`applySign`) and the report-level credit/debit
split (`isCredit`) derive from the same column rather than restating the rule.

The types are **governed reference data** — a `transaction_types` table, foreign-keyed
from `transactions.type` — not a fixed enum. The business can add a new fee category or a
new kind of distribution by inserting a row and classifying it as a credit or a debit,
with no code deploy. `GET /api/v1/transaction-types` lists what's available for posting.
[08-Database-Design.md §2](docs/08-Database-Design.md) covers the full rationale, including
why this is deliberately *not* a client-facing self-service action.

---

## API

Everything is under `/api/v1` and scoped to a client.

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
| `GET` | `/transaction-types` | Postable transaction types and their credit/debit direction |

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

`400` for validation failures (with a per-field `errors` map), `404` for missing
resources, `409` for business-rule violations. Full reference with request/response
examples: [07-API-Documentation.md](docs/07-API-Documentation.md).

---

## Design decisions

| Decision | Why |
|---|---|
| **Tenancy enforced on every lookup** — every repository call is `findByIdAndClientId`, never `findById` | A transaction can never link a fund and an investor from different clients. Cross-tenant reads return `404`, not `403` — a `403` confirms the resource exists, which is itself a leak |
| **Money is `BigDecimal`, never `double`** | Binary floating point can't represent decimal currency exactly, and drift compounds across a ledger. Pinned to 2dp on write; the DB enforces `amount > 0` |
| **Aggregation happens in SQL** | Grouped queries return one row per type, not a full ledger summed in Java. A fund with a million transactions still returns a handful of rows, and per-party breakdowns avoid the reporting N+1 |
| **Reports carry their `asOfDate`** | Back-dated transactions restate history. A balance without an effective date is ambiguous — and reports get screenshotted and emailed |
| **Flyway owns the schema; Hibernate is `validate`** | Startup fails fast on drift, instead of `ddl-auto: update` silently altering tables under a running system |
| **Transactions can't be re-pointed to another fund or investor** | `PUT` corrects type, amount, date, notes — not the parties. Re-pointing rewrites two parties' reported history; the correct treatment is a reversing entry, which is what an auditor expects |
| **Funds and investors with transactions can't be deleted** | A fund with history is a financial record, not a typo. Refused with `409` |
| **Circuit breaker on reporting endpoints** | RDS is the one real external dependency; Resilience4j stops a database stall becoming thread-pool exhaustion that takes writes down with reads ([06](docs/06-Resiliency-Scalability.md)) |

In production `clientId` would come from a JWT claim rather than the path. The path-based
approach keeps this runnable without an auth server, while making the isolation boundary
visible and testable.

### Deliberately not done

| Not done | Why |
|---|---|
| **Negative fund balances aren't blocked** | Real funds run negative between a distribution and a capital call, so blocking it would encode a rule the business may not want — and would need row-level locking to be safe under concurrent writes. First question I'd take back to the business |
| **No subscription/commitment entity** | The brief describes investors interacting with funds *through transactions*, so participation is derived from the ledger. Adding commitments later is additive, not a rewrite |
| **No authentication** | Out of scope for the brief. The tenancy boundary is built and tested, so wiring it to a real principal is a small change ([09-Security.md](docs/09-Security.md)) |

---

## What I'd add for production

- **Authentication and authorisation** — OAuth2/JWT, with `clientId` resolved from the
  token and method-level security enforcing it, replacing the path variable.
- **Append-only audit trail** — `created_at`/`updated_at` cover the basics, but financial
  records need "who changed what, when, and what was the previous value". A separate audit
  table (or Hibernate Envers) rather than in-place mutation.
- **Optimistic locking** (`@Version`) on transactions, to prevent lost updates under
  concurrent edits.
- **Idempotency keys** on transaction creation, so a retried request after a network
  timeout cannot double-book a contribution.
- **Testcontainers** in CI for the integration suite, catching dialect-specific behaviour
  H2's compatibility mode hides. (The component tier already covers this against real
  PostgreSQL; Testcontainers would make it the default rather than opt-in.)
- **Structured JSON logs with correlation IDs** propagated end to end, plus tracing across
  the request path ([10-Monitoring-Observability.md](docs/10-Monitoring-Observability.md)).
- **Soft delete / lifecycle status** on funds (`ACTIVE`/`CLOSED`) instead of hard delete,
  so closing a fund preserves history.
- **Rate limiting and request-size caps** at the gateway.
- **Report caching** — portfolio rollups are read-heavy and change only when transactions
  are written, making them a natural fit for a cache invalidated on write.

The sequencing for all of this is in [04-Implementation-Plan.md](docs/04-Implementation-Plan.md).

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

src/test/
├── java/…/cucumber/    Component + smoke step definitions (same features, two runners)
└── resources/features/ Gherkin scenarios

frontend/                      Optional React demo UI (not part of the deliverable)
docs/                          01 … 13 — see the index above
terraform/                     Modules + demo/staging/production environments
deploy/ecs/                    ECS task definitions + CodeDeploy appspec
.github/workflows/ci-cd.yml    Build → test → image → deploy → smoke gate
```

DTOs are kept separate from entities deliberately: the wire contract and the persistence
model change for different reasons, and serialising entities directly risks lazy-loading
surprises and exposes columns never meant to be public.

---

## Test coverage

The 16 unit/integration tests target the two places a defect would be both silent and
financially meaningful: the reporting arithmetic (aggregation, `asOfDate` filtering,
per-investor positions summing back to the fund balance) and the tenant boundary
(cross-tenant writes rejected, another client's fund returning `404`). The 5 Cucumber
scenarios re-verify that ground end to end — client lifecycle, a fund report's aggregation
SQL, an inception-date violation — first against real PostgreSQL, then unchanged as the
post-deploy gate against the live `demo` environment.

SonarCloud reads JaCoCo coverage on every push and PR, advisory alongside the OWASP and
SpotBugs steps rather than a merge gate.

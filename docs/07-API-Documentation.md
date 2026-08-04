# API Documentation

**Interactive reference:** `/swagger-ui/index.html` (raw OpenAPI spec at `/v3/api-docs`),
served by `springdoc-openapi` — always the source of truth for exact request/response
schemas. This document is the human-readable companion: conventions, examples, and the
"why," not a duplicate of every field.

Live now, no setup required:
**https://524p1owhlc.execute-api.us-east-1.amazonaws.com/swagger-ui/index.html** —
or locally at `http://localhost:8083/swagger-ui/index.html` after `./run.sh`.

## 1. Conventions

| Convention | Detail |
|---|---|
| Base path | `/api/v1`, versioned in the URL — a `v2` can be introduced additively without breaking existing integrations |
| Tenancy | Every non-root resource is nested under `/clients/{clientId}/...`; in production `clientId` is resolved from the auth token, not trusted from the path directly (see [09-Security.md](09-Security.md)) |
| Pagination | `?page=0&size=20&sort=name,asc` on all list endpoints |
| Content type | `application/json` request/response; errors are `application/problem+json` |
| Idempotency | `POST /transactions` accepts an `Idempotency-Key` header (phase 1, see [04-Implementation-Plan.md](04-Implementation-Plan.md)) |

## 2. Endpoints

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
| `GET` | `/transaction-types` | List transaction types available for posting (not client-scoped — see §3a) |
| `GET` | `/actuator/health` | Liveness/readiness probe used by the ALB target group |

## 3. Example: recording a transaction

```
POST /api/v1/clients/{clientId}/transactions
Content-Type: application/json

{
  "fundId": "22222222-2222-2222-2222-222222222201",
  "investorId": "33333333-3333-3333-3333-333333333301",
  "type": "CONTRIBUTION",
  "amount": 25000.00,
  "transactionDate": "2024-12-01",
  "notes": "Q4 capital call"
}
```

**201 Created**

```json
{
  "id": "44444444-4444-4444-4444-444444444401",
  "fundId": "22222222-2222-2222-2222-222222222201",
  "investorId": "33333333-3333-3333-3333-333333333301",
  "type": "CONTRIBUTION",
  "amount": 25000.00,
  "transactionDate": "2024-12-01",
  "notes": "Q4 capital call",
  "createdAt": "2026-08-01T14:02:11Z",
  "updatedAt": "2026-08-01T14:02:11Z"
}
```

### 3a. Transaction types are not a fixed enum

`type` on a transaction is a **code** (`"CONTRIBUTION"`, `"MANAGEMENT_FEE"`, ...)
validated against the `transaction_types` table (see
[08-Database-Design.md](08-Database-Design.md) §2), not a closed set baked into the
OpenAPI schema. The business can add a new type — a new fee category, a new kind of
distribution — by adding a row to that table, with no API redeploy.

```
GET /api/v1/transaction-types
```

```json
[
  { "code": "CONTRIBUTION",    "direction": "CREDIT", "description": "Capital contributed by an investor into a fund" },
  { "code": "INTEREST_INCOME", "direction": "CREDIT", "description": "Interest income earned by a fund" },
  { "code": "DISTRIBUTION",    "direction": "DEBIT",  "description": "Capital or income distributed from a fund to an investor" },
  { "code": "GENERAL_EXPENSE", "direction": "DEBIT",  "description": "A general expense charged to a fund" },
  { "code": "MANAGEMENT_FEE",  "direction": "DEBIT",  "description": "A management fee charged to a fund" }
]
```

Posting a transaction with a code that isn't in this list (or one that's been retired)
returns `404` — the same "reference to something that doesn't exist" semantics as any
other unresolvable ID in this API, not a special-cased error shape.

## 4. Example: fund report

```
GET /api/v1/clients/{clientId}/reports/funds/{fundId}?asOfDate=2024-06-30
```

```json
{
  "fundId": "22222222-2222-2222-2222-222222222201",
  "asOfDate": "2024-06-30",
  "balance": 475000.00,
  "totalsByType": [
    { "type": "CONTRIBUTION", "total": 500000.00 },
    { "type": "MANAGEMENT_FEE", "total": 25000.00 }
  ],
  "investorPositions": [
    { "investorId": "33333333-3333-3333-3333-333333333301", "position": 300000.00 },
    { "investorId": "33333333-3333-3333-3333-333333333302", "position": 175000.00 }
  ]
}
```

Per-investor positions sum back to the fund balance — enforced by a test in the suite, not
just true by convention.

## 5. Error format (RFC 7807)

```json
{
  "type": "https://api.ark.com/problems/business-rule-violation",
  "title": "Business rule violation",
  "status": 409,
  "detail": "Transaction date 2023-01-01 is before the fund's inception date 2024-02-01",
  "instance": "/api/v1/clients/.../transactions",
  "timestamp": "2026-08-01T14:02:11Z"
}
```

| Status | Meaning | Example |
|---|---|---|
| `400` | Validation failure | Missing required field, malformed date — includes a per-field `errors` map |
| `404` | Resource not found (including cross-tenant access, or an unknown/retired transaction type code) | Requesting another client's fund; posting `type: "CAPITAL_CALL"` before that code exists in `transaction_types` |
| `409` | Business rule violation | Transaction predates fund inception; deleting a fund with history |

## 6. Filtering and query parameters

| Endpoint | Parameter | Effect |
|---|---|---|
| `GET /transactions` | `fundId` | Restrict to one fund |
| `GET /transactions` | `investorId` | Restrict to one investor |
| `GET /reports/funds/{fundId}` | `asOfDate` | Compute balance/positions as of a past date, not "now" |
| `GET /reports/investors/{investorId}` | `asOfDate` | Same, across every fund the investor participates in |
| `GET /reports/portfolio` | `asOfDate` | Same, across every fund the client owns |

## 7. Versioning strategy

- The URL path (`/api/v1`) carries the major version. A breaking change (removed field,
  changed semantics) ships as `/api/v2` running alongside `/v1` until clients migrate —
  never a silent breaking change to `/v1`.
- Additive changes (new optional field, new endpoint) ship into the existing version — this
  is how [04-Implementation-Plan.md](04-Implementation-Plan.md)'s phase 2/3 features
  (commitments, org hierarchy) are designed to land without a version bump.
- Deprecation policy: a `Sunset` header and changelog entry at least one full release cycle
  before an old version is retired.
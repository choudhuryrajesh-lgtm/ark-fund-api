# SPEC — Ark Fund Management API

Status: v1.0 — written before implementation. This is the source of truth. If the code and this
document ever disagree, that's a bug — either the code is wrong, or this spec is stale and must be
updated first, then the code.

## 1. Problem

Ark is a SaaS platform for investment management and reporting. A client (the business using
Ark) can register funds and investors through the portal. Investors interact with funds by
executing transactions. The system needs a backend API — CRUD for the core entities, plus basic
reporting for funds and investors.

## 2. Out of scope (explicitly, so it doesn't creep in)

No frontend. No authentication/authorization (would exist in a real product, but the assignment
says "front end is not required" and doesn't ask for auth — noted as a known gap in the README,
not silently skipped). No multi-currency support. No pagination beyond a sane default (noted as a
future improvement).

## 3. Domain model

```
Client 1 ──< Fund
Client 1 ──< Investor
Fund   1 ──< Transaction >── 1 Investor
```

- **Client** — the business entity that owns funds and investors. `id, name, createdAt`.
- **Fund** — belongs to exactly one client. `id, name, clientId, createdAt`.
- **Investor** — belongs to exactly one client. `id, name, email, clientId, createdAt`.
- **Transaction** — the only thing that links a Fund and an Investor. An investor executes a
  transaction, on a date, for an amount, against a fund. `id, investorId, fundId, date, amount,
  type, createdAt`.

There is no separate "Fund ↔ Investor membership" table. "Fund has multiple investors" and
"investor contributes to multiple funds" are both derived from the transaction history — this
keeps the model normalized and avoids a second source of truth for something a query already
answers.

### 3.1 Transaction types (fixed business rule)

| Type | Effect |
|---|---|
| CONTRIBUTION | Credit |
| INTEREST_INCOME | Credit |
| DISTRIBUTION | Debit |
| GENERAL_EXPENSE | Debit |
| MANAGEMENT_FEE | Debit |

`amount` is always stored positive. The credit/debit sign is derived from `type`, never taken
from user input — a caller cannot submit a negative amount to fake a debit. This is a deliberate
integrity rule: the API is the single place that decides what a transaction type means.

### 3.2 Data integrity rule

A transaction's `investorId` and `fundId` must belong to the *same* client. Creating a
transaction across two different clients' data is rejected (400) — this is the one business rule
in the spec that isn't explicitly stated in the assignment but follows directly from "clients add
funds and investors via their portal": funds and investors are scoped to a client, so a
transaction crossing that boundary is invalid data, not a valid edge case.

## 4. API contract

Base path: `/api/v1`

### Clients
- `POST /clients` — create. `{name}` → 201 + created client.
- `GET /clients` — list.
- `GET /clients/{id}` — get one. 404 if missing.
- `PUT /clients/{id}` — update. 404 if missing.
- `DELETE /clients/{id}` — delete. 404 if missing.

### Funds
- `POST /funds` — create. `{name, clientId}` → 201. 404 if clientId doesn't exist.
- `GET /funds`, `GET /funds/{id}`, `PUT /funds/{id}`, `DELETE /funds/{id}` — standard CRUD, same
  404 semantics as above.
- `GET /funds/{id}/report` — **reporting endpoint**, see §5.

### Investors
- `POST /investors` — create. `{name, email, clientId}` → 201.
- `GET /investors`, `GET /investors/{id}`, `PUT /investors/{id}`, `DELETE /investors/{id}`.
- `GET /investors/{id}/report` — **reporting endpoint**, see §5.

### Transactions
- `POST /transactions` — create. `{investorId, fundId, date, amount, type}` → 201.
  - 400 if `amount <= 0`.
  - 404 if investorId or fundId doesn't exist.
  - 400 if investor and fund belong to different clients (§3.2).
- `GET /transactions?fundId=&investorId=` — list, optionally filtered.
- `GET /transactions/{id}`, `PUT /transactions/{id}`, `DELETE /transactions/{id}`.
  - Noted in README as a real-world caveat: production ledgers usually treat transactions as
    append-only and use reversing entries instead of edit/delete, to preserve an audit trail.
    Implemented here as full CRUD anyway because the assignment asks for "typical CRUD
    operations" across the board — the tradeoff is called out explicitly rather than silently
    picked.

## 5. Reporting requirements

### 5.1 Fund report — `GET /funds/{id}/report`
```json
{
  "fundId": 1,
  "fundName": "Growth Fund I",
  "balance": 125000.00,
  "totalsByType": {
    "CONTRIBUTION": 150000.00,
    "INTEREST_INCOME": 2000.00,
    "DISTRIBUTION": 20000.00,
    "GENERAL_EXPENSE": 5000.00,
    "MANAGEMENT_FEE": 2000.00
  },
  "distinctInvestorCount": 4,
  "transactionCount": 37
}
```
`balance` = sum(credits) − sum(debits) across all of the fund's transactions.

### 5.2 Investor report — `GET /investors/{id}/report`
```json
{
  "investorId": 3,
  "investorName": "Jane Doe",
  "netPosition": 45000.00,
  "byFund": [
    { "fundId": 1, "fundName": "Growth Fund I", "netPosition": 30000.00 },
    { "fundId": 2, "fundName": "Income Fund II", "netPosition": 15000.00 }
  ]
}
```
`netPosition` = sum(credits) − sum(debits) for that investor, overall and per fund.

## 6. Acceptance criteria (used as the test checklist)

1. Creating a fund with a non-existent `clientId` returns 404, not 500.
2. Creating a transaction with `amount = 0` or negative returns 400.
3. Creating a transaction where investor and fund belong to different clients returns 400.
4. A fund report's `balance` reflects credits minus debits exactly — verified with a
   contribution + a management fee in the same fund.
5. An investor report's `netPosition` is correct per fund and matches the sum across funds
   for the overall figure.
6. Deleting a client does not silently orphan funds/investors/transactions — cascade behavior
   is explicit and documented, not accidental.
7. The whole application starts with a single command and requires no manual DB setup
   (H2 in-memory, schema auto-created on boot).

## 7. Non-functional requirements

Bean validation on all inputs. A global exception handler so 404s/400s return structured JSON,
not a stack trace. Layered architecture (controller → service → repository) so business rules
live in one place, not scattered across controllers. Unit tests for the business logic that
actually matters (transaction sign rules, report math), not tests for getters and setters.

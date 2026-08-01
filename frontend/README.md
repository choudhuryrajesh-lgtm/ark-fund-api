# Ark Fund API — Demo UI

A minimal React app that exercises the Ark Fund API: create a client, add funds and
investors, record transactions, and view the three report types. It exists to make the
API easy to poke at visually — the take-home brief explicitly says a front end is not
required, so this is a bonus, kept out of the graded API's source tree.

No state management library, no router, no component kit — plain React, `fetch`, and
`useState`/`useEffect`. The point is to showcase the API, not the frontend.

## Running it

**As part of the full stack** (recommended — this is what `../run.sh` and
`docker compose up --build` from the repo root already do):

```bash
cd ..
./run.sh
```

The UI is served at **http://localhost:3000**, built and served by nginx, which also
reverse-proxies `/api/*` to the `api` container — so the browser never needs CORS
configured on the backend.

**Standalone, against a locally-running API** (for UI iteration without rebuilding the
Docker image each time):

```bash
npm install
npm run dev
```

This starts Vite's dev server on **http://localhost:5173**, proxying `/api` to
`http://localhost:8083` (see `vite.config.js`) — so the API needs to already be running
(`docker compose up --build` from the repo root, or your own local run).

## What it does

| Screen | Backed by |
|---|---|
| Client picker (select or create) | `GET/POST /api/v1/clients` |
| Funds tab | `GET/POST /api/v1/clients/{id}/funds` |
| Investors tab | `GET/POST /api/v1/clients/{id}/investors` |
| Transactions tab | `GET/POST /api/v1/clients/{id}/transactions`, `GET /api/v1/transaction-types` |
| Reports tab (portfolio / by fund / by investor) | `GET /api/v1/clients/{id}/reports/*` |

Errors from the API (RFC 7807 `problem+json`) are surfaced directly in the UI rather than
swallowed — a validation failure shows the actual field error, a business-rule violation
shows the actual message.

## What's intentionally not here

CRUD update/delete for funds/investors/transactions, auth, routing, pagination beyond a
single page of 100 rows — this is a showcase of the read/write flow end to end, not a
second implementation of the API's full surface.
#!/usr/bin/env bash
# One-command run: builds and starts the stack, waits for it to actually be
# ready, then prints exactly what to hit next. `docker compose up --build`
# alone already satisfies "run it in one command" — this just removes the
# guesswork of "is it up yet?" for someone testing the API for the first time.
set -euo pipefail

PORT="${API_PORT:-8083}"
MAX_WAIT_SECONDS=90
POLL_INTERVAL_SECONDS=2

echo "==> Building and starting Ark Fund API (docker compose up --build)..."
docker compose up --build -d

echo "==> Waiting for the API to become healthy on port ${PORT}..."
elapsed=0
while [ "${elapsed}" -lt "${MAX_WAIT_SECONDS}" ]; do
    if curl -sf "http://localhost:${PORT}/actuator/health" >/dev/null 2>&1; then
        echo
        echo "Ark Fund API is up."
        echo
        echo "  Swagger UI:   http://localhost:${PORT}/swagger-ui.html"
        echo "  Health check: http://localhost:${PORT}/actuator/health"
        echo
        echo "  Try it (demo data is pre-seeded):"
        echo "    curl http://localhost:${PORT}/api/v1/clients"
        echo "    curl \"http://localhost:${PORT}/api/v1/clients/11111111-1111-1111-1111-111111111111/reports/portfolio\""
        echo
        echo "  Shut down:  docker compose down"
        echo "  Full reset: docker compose down -v   (also drops the seeded database)"
        exit 0
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
    elapsed=$((elapsed + POLL_INTERVAL_SECONDS))
done

echo
echo "API did not become healthy within ${MAX_WAIT_SECONDS}s." >&2
echo "Check the logs: docker compose logs api" >&2
exit 1
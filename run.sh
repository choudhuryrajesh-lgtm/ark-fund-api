#!/usr/bin/env bash
# One-command run: builds and starts the stack, waits for it to actually be
# ready, then prints exactly what to hit next. `docker compose up --build`
# alone already satisfies "run it in one command" — this just removes the
# guesswork of "is it up yet?" for someone testing the API for the first time.
set -euo pipefail

PORT="${API_PORT:-8083}"
UI_PORT="${UI_PORT:-3000}"
MAX_WAIT_SECONDS=90
POLL_INTERVAL_SECONDS=2

echo "==> Building and starting Ark Fund API (docker compose up --build)..."
docker compose up --build -d

echo "==> Waiting for the API to become healthy on port ${PORT}..."
elapsed=0
api_up=false
while [ "${elapsed}" -lt "${MAX_WAIT_SECONDS}" ]; do
    if curl -sf "http://localhost:${PORT}/actuator/health" >/dev/null 2>&1; then
        api_up=true
        break
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
    elapsed=$((elapsed + POLL_INTERVAL_SECONDS))
done

if [ "${api_up}" != "true" ]; then
    echo
    echo "API did not become healthy within ${MAX_WAIT_SECONDS}s." >&2
    echo "Most likely cause: a race on the very first cold start of the" >&2
    echo "database container (docker-compose.yml has a restart policy for" >&2
    echo "exactly this — one more run usually clears it):" >&2
    echo >&2
    echo "    ./run.sh" >&2
    echo >&2
    echo "Last 50 lines of the API container's log, for context:" >&2
    echo "----------------------------------------------------------------" >&2
    docker compose logs --tail 50 api >&2 || true
    echo "----------------------------------------------------------------" >&2
    exit 1
fi

echo "==> Waiting for the demo UI to become reachable on port ${UI_PORT}..."
elapsed=0
ui_up=false
while [ "${elapsed}" -lt "${MAX_WAIT_SECONDS}" ]; do
    if curl -sf "http://localhost:${UI_PORT}/" >/dev/null 2>&1; then
        ui_up=true
        break
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
    elapsed=$((elapsed + POLL_INTERVAL_SECONDS))
done

echo
echo "Ark Fund API is up."
echo
echo "  Swagger UI:   http://localhost:${PORT}/swagger-ui.html"
echo "  Health check: http://localhost:${PORT}/actuator/health"
if [ "${ui_up}" = "true" ]; then
    echo "  Demo UI:      http://localhost:${UI_PORT}"
else
    echo "  Demo UI:      not reachable yet on port ${UI_PORT} — check: docker compose logs frontend"
fi
echo
echo "  Try it (demo data is pre-seeded):"
echo "    curl http://localhost:${PORT}/api/v1/clients"
echo "    curl \"http://localhost:${PORT}/api/v1/clients/11111111-1111-1111-1111-111111111111/reports/portfolio\""
echo
echo "  Shut down:  docker compose down"
echo "  Full reset: docker compose down -v   (also drops the seeded database)"
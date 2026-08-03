#!/usr/bin/env bash
# Lists everything a given environment has actually created — two views:
#   1. terraform state list  — what Terraform's state file tracks, resource by resource.
#   2. AWS Resource Groups Tagging API — every real AWS resource carrying this
#      environment's tags, straight from the account. Useful cross-check after a
#      partial/failed apply, since state can briefly lag or a resource can exist
#      without ever having been recorded (e.g. a run that died before state was written).
#
# Usage: ./list-resources.sh <environment>   e.g. ./list-resources.sh demo

set -euo pipefail

ENV="${1:?Usage: $0 <environment>  (shared|demo|staging|production)}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$SCRIPT_DIR/../environments/$ENV"

if [ ! -d "$ENV_DIR" ]; then
  echo "No such environment: $ENV_DIR" >&2
  exit 1
fi

echo "=== Terraform state: $ENV ==="
(cd "$ENV_DIR" && terraform state list) || echo "(no state / not initialized)"

echo
echo "=== AWS resources tagged Project=ark-fund-api, Environment=$ENV ==="
aws resourcegroupstaggingapi get-resources \
  --tag-filters "Key=Project,Values=ark-fund-api" "Key=Environment,Values=$ENV" \
  --query 'ResourceTagMappingList[].ResourceARN' \
  --output table
#!/usr/bin/env bash
# Builds the React app and syncs it to the demo environment's S3 bucket,
# then invalidates CloudFront so the new build is served immediately instead
# of waiting out the cache TTL. Run this after `terraform apply` in
# environments/demo has created the static-site module (bucket + distribution).
#
# Usage: ./deploy-frontend.sh [environment]   (defaults to demo)

set -euo pipefail

ENV="${1:-demo}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$SCRIPT_DIR/../environments/$ENV"
FRONTEND_DIR="$SCRIPT_DIR/../../frontend"

BUCKET=$(cd "$ENV_DIR" && terraform output -raw frontend_bucket)
DISTRIBUTION_ID=$(cd "$ENV_DIR" && terraform output -raw frontend_distribution_id)
URL=$(cd "$ENV_DIR" && terraform output -raw frontend_url)

echo "=== Building frontend ==="
(cd "$FRONTEND_DIR" && npm install && npm run build)

echo "=== Syncing dist/ to s3://$BUCKET ==="
aws s3 sync "$FRONTEND_DIR/dist" "s3://$BUCKET" --delete

echo "=== Invalidating CloudFront cache ($DISTRIBUTION_ID) ==="
aws cloudfront create-invalidation --distribution-id "$DISTRIBUTION_ID" --paths "/*"

echo
echo "Deployed: $URL"
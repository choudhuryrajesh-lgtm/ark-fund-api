#!/usr/bin/env bash
# Run this whenever backend code changes and you want them live: builds and
# pushes a new image, registers a new ECS task definition revision for it via
# `terraform apply -var image_tag=...`, then force-deploys the ECS service
# onto that revision.
#
# Two steps, not one, because aws_ecs_service.app deliberately ignores
# task_definition drift after the first apply (see modules/ecs/main.tf) — day
# to day that's .github/workflows/ci-cd.yml's job, but for a manual push like
# this, `terraform apply` alone registers the new revision without ever
# rolling the service onto it, so the update-service call below is required.
#
# Usage: ./deploy-backend.sh [environment]   (defaults to demo)

set -euo pipefail

ENV="${1:-demo}"
REGION="${AWS_REGION:-us-east-1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$SCRIPT_DIR/../environments/$ENV"
REPO_ROOT="$SCRIPT_DIR/../.."

# Git SHA alone isn't unique enough — ECR's IMAGE_TAG_MUTABILITY is IMMUTABLE
# (see modules/ecr), so re-running this without a new commit (e.g. iterating
# on a fix) would collide with the previous push. The timestamp guarantees a
# fresh tag every run while the SHA still ties it back to a commit.
IMAGE_TAG="$(git -C "$REPO_ROOT" rev-parse --short HEAD)-$(date +%s)"
ECR_REPO_URL="$(cd "$ENV_DIR" && terraform output -raw ecr_repository_url)"

# Explicit --platform, not a bare `docker build`: the ecs module's task
# definition requests ARM64 (Graviton — cheaper, and native on Apple
# Silicon). Forcing this here means the image comes out correct even when
# run on an Intel Mac or a Linux x86 CI runner, not just by coincidence of
# whatever machine happens to run this script.
echo "=== Building backend image (linux/arm64): $ECR_REPO_URL:$IMAGE_TAG ==="
# INCLUDE_NEW_RELIC=true here, not the Dockerfile's default: this script is
# only ever used for real AWS deploys, which is where the agent is actually
# useful. Local `docker compose up --build` deliberately stays on the
# Dockerfile's off-by-default so a plain local eval never depends on
# downloading the agent.
docker build --platform linux/arm64 --build-arg INCLUDE_NEW_RELIC=true -t "$ECR_REPO_URL:$IMAGE_TAG" "$REPO_ROOT"

echo "=== Pushing to ECR ==="
docker push "$ECR_REPO_URL:$IMAGE_TAG"

echo "=== Registering new task definition revision (terraform apply -var image_tag=$IMAGE_TAG) ==="
(cd "$ENV_DIR" && terraform apply -var="image_tag=$IMAGE_TAG")

CLUSTER=$(cd "$ENV_DIR" && terraform output -raw ecs_cluster_name)
SERVICE=$(cd "$ENV_DIR" && terraform output -raw ecs_service_name)
TASK_DEF_FAMILY=$(cd "$ENV_DIR" && terraform output -raw ecs_task_definition_family)
TASK_DEF_ARN=$(aws ecs describe-task-definition --task-definition "$TASK_DEF_FAMILY" --region "$REGION" --query 'taskDefinition.taskDefinitionArn' --output text)

# The service's desired count lives outside Terraform's control after the
# first apply (same ignore_changes as task_definition) — if a prior failed
# deployment's circuit breaker ever zeroed it out, a plain --force-new-deployment
# would silently redeploy zero tasks. Read it back and only raise it, never
# lower it, so this never fights a deliberate scale-up done by hand.
CURRENT_DESIRED=$(aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICE" --region "$REGION" --query 'services[0].desiredCount' --output text)
DESIRED_COUNT=$([ "$CURRENT_DESIRED" -lt 1 ] && echo 1 || echo "$CURRENT_DESIRED")

echo "=== Forcing ECS service onto the new revision ($TASK_DEF_ARN), desired count $DESIRED_COUNT ==="
aws ecs update-service \
  --cluster "$CLUSTER" \
  --service "$SERVICE" \
  --task-definition "$TASK_DEF_ARN" \
  --desired-count "$DESIRED_COUNT" \
  --force-new-deployment \
  --region "$REGION" >/dev/null

echo
echo "Deployed $IMAGE_TAG. Watch rollout with:"
echo "  watch -n 10 'aws ecs describe-services --cluster $CLUSTER --services $SERVICE --region $REGION --query \"services[0].deployments\"'"
#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if command -v docker >/dev/null 2>&1; then
  docker compose -f deploy/docker-compose.yml up -d mysql redis minio >/dev/null || true
fi

echo "Dependency containers are starting. Use ./scripts/codespaces-start.sh to run the app."

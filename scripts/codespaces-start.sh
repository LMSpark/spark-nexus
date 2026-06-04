#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p logs

start_service() {
  local name="$1"
  local command="$2"
  local log_file="$3"
  local pid_file="logs/${name}.pid"

  if [[ -f "$pid_file" ]]; then
    local existing_pid
    existing_pid="$(cat "$pid_file")"
    if [[ -n "$existing_pid" ]] && kill -0 "$existing_pid" 2>/dev/null; then
      echo "$name is already running with PID $existing_pid."
      return
    fi
  fi

  bash -lc "$command" > "$log_file" 2>&1 &
  echo "$!" > "$pid_file"
  echo "Started $name with PID $(cat "$pid_file")."
}

echo "Starting MySQL, Redis, and MinIO..."
docker compose -f deploy/docker-compose.yml up -d mysql redis minio

echo "Waiting for MySQL health check..."
for _ in {1..60}; do
  status="$(docker inspect -f '{{.State.Health.Status}}' spark-nexus-property-mysql 2>/dev/null || true)"
  if [[ "$status" == "healthy" ]]; then
    break
  fi
  sleep 2
done

status="$(docker inspect -f '{{.State.Health.Status}}' spark-nexus-property-mysql 2>/dev/null || true)"
if [[ "$status" != "healthy" ]]; then
  echo "MySQL did not become healthy. Check logs with:"
  echo "docker logs spark-nexus-property-mysql"
  exit 1
fi

echo "Starting backend on :8580..."
start_service "backend" "cd '$ROOT_DIR/backend' && mvn spring-boot:run" "logs/backend.codespaces.log"

echo "Starting admin frontend on :5683..."
start_service "web-admin" "cd '$ROOT_DIR/web-admin' && npm run dev" "logs/web-admin.codespaces.log"

echo "Starting owner H5 frontend on :5684..."
start_service "owner-miniapp" "cd '$ROOT_DIR/owner-miniapp' && npm run dev:h5" "logs/owner-miniapp.codespaces.log"

echo
echo "All services are starting."
echo "Backend API: http://localhost:8580/api/health"
echo "Admin:       http://localhost:5683"
echo "Owner H5:    http://localhost:5684"
echo
echo "Logs:"
echo "tail -f logs/backend.codespaces.log"
echo "tail -f logs/web-admin.codespaces.log"
echo "tail -f logs/owner-miniapp.codespaces.log"

#!/usr/bin/env bash
set -euo pipefail

check_url() {
  local name="$1"
  local url="$2"
  if curl -fsS "$url" >/dev/null; then
    echo "OK   $name $url"
  else
    echo "FAIL $name $url"
    return 1
  fi
}

check_url "Backend" "http://localhost:8580/api/health"
check_url "Admin proxy" "http://localhost:5683/api/health"
check_url "Owner proxy" "http://localhost:5684/api/health"

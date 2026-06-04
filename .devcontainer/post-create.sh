#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "Installing frontend dependencies..."
npm --prefix web-admin install
npm --prefix owner-miniapp install

echo "Preloading Maven dependencies..."
mvn -f backend/pom.xml -DskipTests dependency:go-offline

echo
echo "Codespaces setup complete."
echo "Run ./scripts/codespaces-start.sh to start MySQL, backend, admin, and owner H5."

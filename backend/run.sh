#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/.." && pwd)"

if [ -f "${repo_root}/.env.local" ]; then
  set -a
  . "${repo_root}/.env.local"
  set +a
fi

cd "$(dirname "$0")"

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn is required to run the Spring Boot backend." >&2
  echo "Install Maven and Java 21+, then rerun ./backend/run.sh" >&2
  exit 1
fi

port="${PORT:-8080}"

if [ -z "${PORT:-}" ] && command -v lsof >/dev/null 2>&1; then
  while lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; do
    port=$((port + 1))
  done
fi

export PORT="$port"
echo "Starting backend on http://127.0.0.1:${PORT}"

exec mvn spring-boot:run "$@"

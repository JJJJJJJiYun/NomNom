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
  echo "mvn is required to run backend tests." >&2
  echo "Install Maven and Java 21+, then rerun ./backend/test.sh" >&2
  exit 1
fi

exec mvn test "$@"

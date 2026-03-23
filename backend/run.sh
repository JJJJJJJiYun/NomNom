#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac -d out $(find src -name '*.java' | sort)
exec java -cp out com.nomnom.backend.NomNomServer "$@"

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

pass() {
  printf '[OK] %s\n' "$1"
}

warn() {
  printf '[WARN] %s\n' "$1"
}

fail() {
  printf '[FAIL] %s\n' "$1"
}

if command -v java >/dev/null 2>&1; then
  pass "Java available: $(java -version 2>&1 | head -n 1)"
else
  fail "Java not found in PATH"
fi

if command -v mvn >/dev/null 2>&1; then
  pass "Maven available: $(mvn -version 2>/dev/null | head -n 1)"
else
  fail "Maven not found in PATH"
fi

if DEVELOPER_DIR=/Library/Developer/CommandLineTools swift --version >/tmp/nomnom-swift-version.txt 2>/dev/null; then
  pass "Swift CLI available: $(head -n 1 /tmp/nomnom-swift-version.txt)"
else
  fail "Swift CLI unavailable via Command Line Tools"
fi

if DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer xcodebuild -list -project NomNom.xcodeproj >/tmp/nomnom-xcode-check.txt 2>&1; then
  pass "Full Xcode is ready for iOS builds"
else
  if grep -q "You have not agreed to the Xcode license agreements" /tmp/nomnom-xcode-check.txt; then
    warn "Xcode license not accepted. Run: sudo xcodebuild -license && sudo xcodebuild -runFirstLaunch"
  else
    warn "Xcode check failed: $(head -n 1 /tmp/nomnom-xcode-check.txt)"
  fi
fi

if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:8080 -sTCP:LISTEN >/tmp/nomnom-port-8080.txt 2>/dev/null; then
  warn "Port 8080 is already in use. backend/run.sh will auto-pick the next free port."
else
  pass "Port 8080 is available"
fi

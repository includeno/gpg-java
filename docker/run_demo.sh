#!/usr/bin/env bash
set -euo pipefail
MODE="${1:-normal}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mvn -B -DskipTests clean package

if [[ "$MODE" == "broken" || "$MODE" == "broken-stream" || "$MODE" == "broken_stream" ]]; then
  set +e
  mvn -B -DskipTests exec:java \
    -Dexec.mainClass=nl.base.crypto.gpg.demo.PdfDecryptDemo \
    -Dexec.args=broken
  status=$?
  set -e
  exit $status
else
  mvn -B -DskipTests exec:java \
    -Dexec.mainClass=nl.base.crypto.gpg.demo.PdfDecryptDemo \
    -Dexec.args=$MODE
fi

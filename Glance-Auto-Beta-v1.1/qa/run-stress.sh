#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
SRC="$ROOT/app/src/main/java/com/appllp7/glanceauto"
QA="$ROOT/qa"
javac -encoding UTF-8 -d "$OUT" \
  "$SRC/AutoPolicy.java" "$SRC/WakeReason.java" "$SRC/BootPolicy.java" \
  "$QA/AutoPolicyStress.java" "$QA/WakeReasonStress.java" "$QA/BootDecisionStress.java"
for test in AutoPolicyStress WakeReasonStress BootDecisionStress; do
  java -cp "$OUT" "com.appllp7.glanceauto.$test"
done

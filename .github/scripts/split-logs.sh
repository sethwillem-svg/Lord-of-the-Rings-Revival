#!/usr/bin/env bash
# Split a game log into one file per add-on system (specs/addon/debug-logging.md).
# Usage: split-logs.sh <latest.log> <out dir>
set -u
log="${1:-addon/run/gametest/logs/latest.log}"
out="${2:-logs}"
mkdir -p "$out"
summary="$out/summary.md"

if [ ! -f "$log" ]; then
  echo "## No game log" > "$summary"
  echo "\`$log\` was not written: the server never started (see compile.log / gametest-console.log)." >> "$summary"
  exit 0
fi

# every system that logged, from "[name]" or "[DBG name]" prefixes on lotr_revival lines
systems=$(grep -oE '\[(DBG )?[a-z_]+\] ' "$log" | sed -E 's/\[(DBG )?([a-z_]+)\] /\2/' | sort -u)

{
  echo "## Add-on logs"
  echo
  echo "| System | Lines | Debug | Warnings | Errors |"
  echo "| --- | ---: | ---: | ---: | ---: |"
} > "$summary"

for s in $systems; do
  grep -E "\[(DBG )?$s\] " "$log" > "$out/$s.log" || true
  lines=$(wc -l < "$out/$s.log")
  dbg=$(grep -c "\[DBG $s\] " "$out/$s.log" || true)
  warn=$(grep -c '/WARN\]' "$out/$s.log" || true)
  err=$(grep -c '/ERROR\]' "$out/$s.log" || true)
  echo "| $s | $lines | $dbg | $warn | $err |" >> "$summary"
done

# warnings, errors and stack traces from anything (ours, Middle-earth, NeoForge)
grep -nE '/(WARN|ERROR|FATAL)\]|^\s+at |^Caused by|Exception' "$log" > "$out/errors.log" || true

{
  echo
  echo "### Game tests"
  echo
  if grep -qE '\[test\] (PASS|FAIL)' "$log"; then
    grep -E '\[test\] (PASS|FAIL)' "$log" | sed -E 's/.*\[test\] /- /'
  fi
  # NeoForge/vanilla game test runner lines
  grep -E 'tests? (passed|failed)|required tests failed|All [0-9]+ required tests passed|::' "$log" | sed -E 's/^.*\]: /- /' | head -100
  echo
  echo "Errors and warnings: $(wc -l < "$out/errors.log") lines in errors.log"
} >> "$summary"

echo "split $(echo "$systems" | wc -w) systems into $out"

#!/bin/bash
# Generates memory/backlog.md from TODO(BACKLOG-*) comments in source code.
# Convention: // TODO(BACKLOG-HIGH): description [file context]
# Severities: HIGH, MEDIUM, LOW

set -euo pipefail
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"

OUTPUT="memory/backlog.md"
SRC_DIRS="composeApp/src"

# Collect TODOs grouped by severity
collect() {
  local severity="$1"
  grep -rn "TODO(BACKLOG-${severity}):" $SRC_DIRS --include='*.kt' --include='*.swift' --include='*.sq' --include='*.xml' 2>/dev/null | \
    sed 's|^'"$SRC_DIRS"'/||' | \
    while IFS= read -r line; do
      file=$(echo "$line" | cut -d: -f1)
      lineno=$(echo "$line" | cut -d: -f2)
      desc=$(echo "$line" | sed "s|.*TODO(BACKLOG-${severity}): *||")
      echo "| ${severity} | ${desc} | \`${file}:${lineno}\` |"
    done
}

high=$(collect HIGH)
medium=$(collect MEDIUM)
low=$(collect LOW)

total=0
[[ -n "$high" ]] && total=$((total + $(echo "$high" | wc -l)))
[[ -n "$medium" ]] && total=$((total + $(echo "$medium" | wc -l)))
[[ -n "$low" ]] && total=$((total + $(echo "$low" | wc -l)))

cat > "$OUTPUT" << 'HEADER'
# Cross-Epic Deferred Backlog

Auto-generated from `TODO(BACKLOG-*)` comments in source code.
Run `./scripts/generate-backlog.sh` to regenerate.

---

HEADER

echo "**${total} open items**" >> "$OUTPUT"
echo "" >> "$OUTPUT"

if [[ -n "$high" ]]; then
  cat >> "$OUTPUT" << 'EOF'
## HIGH

| Severity | Item | Location |
|----------|------|----------|
EOF
  echo "$high" >> "$OUTPUT"
  echo "" >> "$OUTPUT"
fi

if [[ -n "$medium" ]]; then
  cat >> "$OUTPUT" << 'EOF'
## MEDIUM

| Severity | Item | Location |
|----------|------|----------|
EOF
  echo "$medium" >> "$OUTPUT"
  echo "" >> "$OUTPUT"
fi

if [[ -n "$low" ]]; then
  cat >> "$OUTPUT" << 'EOF'
## LOW

| Severity | Item | Location |
|----------|------|----------|
EOF
  echo "$low" >> "$OUTPUT"
  echo "" >> "$OUTPUT"
fi

echo "---" >> "$OUTPUT"
echo "" >> "$OUTPUT"
echo "_Last generated: $(date '+%Y-%m-%d %H:%M')_" >> "$OUTPUT"

echo "✅ Generated $OUTPUT with $total items"

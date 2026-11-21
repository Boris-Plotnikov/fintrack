#!/bin/sh
set -e
LIST="${1:-$FORBIDDEN_WORDS_FILE}"
if [ -z "$LIST" ] || [ ! -f "$LIST" ]; then
  echo "Usage: scripts/check-forbidden.sh <words-file>  (or set FORBIDDEN_WORDS_FILE)" >&2
  exit 2
fi
FOUND=0
while IFS= read -r word; do
  [ -z "$word" ] && continue
  if git grep -n -i -w -F -- "$word" -- . ':!scripts/check-forbidden.sh' >/dev/null 2>&1; then
    echo "Forbidden word found: $word"
    git grep -n -i -w -F -- "$word" -- . ':!scripts/check-forbidden.sh' | head -5
    FOUND=1
  fi
done < "$LIST"
exit $FOUND

#!/usr/bin/env bash
# Runs the official RealWorld Hurl suite against a locally running server.
#
# This is the project-level definition of done, not part of `sbt test`. It fails today, and will keep
# failing until every capability has been applied -- only the `users` endpoints exist so far.
#
#   sbt run &                       # in another shell
#   scripts/api-conformance.sh
set -euo pipefail

HOST="${REALWORLD_API_HOST:-http://localhost:8080}"
SUITE_URL="https://raw.githubusercontent.com/realworld-apps/realworld/main/specs/api/hurl"
SUITE_DIR="${TMPDIR:-/tmp}/realworld-hurl"
FILES=(
  auth profiles articles comments favorites feed tags pagination
  errors_auth errors_authorization errors_articles errors_comments errors_profiles
)

command -v hurl >/dev/null || {
  echo "hurl is not installed. On macOS: brew install hurl" >&2
  exit 127
}

curl -fsS --max-time 5 "$HOST/api/tags" >/dev/null 2>&1 || {
  echo "no server answering at $HOST -- start one with 'sbt run'" >&2
  exit 1
}

mkdir -p "$SUITE_DIR"
for file in "${FILES[@]}"; do
  curl -fsSL "$SUITE_URL/$file.hurl" -o "$SUITE_DIR/$file.hurl"
done

echo "running ${#FILES[@]} Hurl files against $HOST"
paths=()
for file in "${FILES[@]}"; do paths+=("$SUITE_DIR/$file.hurl"); done

exec hurl --test \
  --variable "host=$HOST" \
  --variable "uid=$(date +%s)$RANDOM" \
  "${paths[@]}"

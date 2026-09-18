#!/usr/bin/env bash
# Records and verifies the requirement statements, so "the swap touched zero requirements" is a check
# rather than a claim.
#
# `SpecTraceSuite` already guards the *set* of ids: add one and the build goes red until a test carries
# it. What it cannot see is a statement quietly reworded to fit the code that now exists -- the same id,
# a different promise. This hashes each statement's text so that rewording is as visible as removal.
#
#   scripts/spec-baseline.sh record    # pre-register the current statements
#   scripts/spec-baseline.sh           # verify nothing has changed since
set -euo pipefail

BASELINE="$(dirname "$0")/spec-baseline.txt"
MODE="${1:-verify}"

current() {
  python3 - "$@" <<'PY'
import hashlib, pathlib, re, sys

SPEC_ROOT = pathlib.Path("src/main/scala/«base»")
OPENS = re.compile(r"^\s*\*\s*-\s+(R\d+\.\d+|S\d+)\s+—\s*(.*)$")
CONTINUES = re.compile(r"^\s*\*\s{3,}(\S.*)$")
BREAKS = re.compile(r"^\s*\*\s*(-\s|#|/|$)")

def statements(path):
    found, ident, parts = [], None, []
    def close():
        if ident:
            text = re.sub(r"\s+", " ", " ".join(parts)).strip()
            found.append((ident, hashlib.sha256(text.encode()).hexdigest()[:12]))
    for line in path.read_text().splitlines():
        opened = OPENS.match(line)
        if opened:
            close()
            ident, parts = opened.group(1), [opened.group(2)]
            continue
        if ident:
            continued = CONTINUES.match(line)
            if continued and not BREAKS.match(line):
                parts.append(continued.group(1))
            else:
                close()
                ident, parts = None, []
    close()
    return found

specs = [("system", SPEC_ROOT / "package.scala")] + sorted(
    (d.name, d / "package.scala") for d in SPEC_ROOT.iterdir() if (d / "package.scala").is_file()
)
for name, path in specs:
    for ident, digest in statements(path):
        print(f"{name:10} {ident:6} {digest}")
PY
}

case "$MODE" in
  record)
    current > "$BASELINE"
    echo "recorded $(wc -l < "$BASELINE" | tr -d ' ') requirement statements in $BASELINE"
    ;;
  verify)
    [[ -f "$BASELINE" ]] || { echo "no baseline yet -- run: $0 record" >&2; exit 2; }
    if diff -u "$BASELINE" <(current) > /tmp/spec-baseline.diff; then
      echo "all $(wc -l < "$BASELINE" | tr -d ' ') requirement statements are unchanged since the baseline"
    else
      echo "requirement statements have changed since the baseline:" >&2
      sed -n '3,$p' /tmp/spec-baseline.diff >&2
      echo >&2
      echo "A '-' line is a statement that was reworded or retired. For the storage-swap experiment," >&2
      echo "any such line is the result, not an obstacle to work around." >&2
      exit 1
    fi
    ;;
  *)
    echo "usage: $0 [record|verify]" >&2
    exit 64
    ;;
esac

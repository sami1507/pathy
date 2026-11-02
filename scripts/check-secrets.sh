#!/usr/bin/env bash
# Fails if a file in the repository looks like it contains a credential.
#
#   scripts/check-secrets.sh            check every tracked file (CI)
#   scripts/check-secrets.sh --staged   check only what is about to be committed (pre-commit hook)
#
# This project once shipped a secret Mapbox token and an AI key in its history, so the check is cheap
# insurance. Public identifiers (a Mapbox `pk.` token, a Firebase app id) are intentionally not matched.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

# name|extended regular expression
PATTERNS=(
  "Google API key|AIza[0-9A-Za-z_-]{35}"
  "Mapbox secret token|sk\.[A-Za-z0-9._-]{30,}"
  "OpenAI-style key|sk-[A-Za-z0-9_-]{20,}"
  "GitHub token|gh[pousr]_[A-Za-z0-9]{30,}"
  "AWS access key|AKIA[0-9A-Z]{16}"
  "Private key block|-----BEGIN [A-Z ]*PRIVATE KEY-----"
  "Slack token|xox[abp]-[A-Za-z0-9-]{20,}"
)

# Files that legitimately hold example or binary data.
EXCLUDES=(':!*.png' ':!*.jpg' ':!*.webp' ':!*.ttf' ':!*.jar' ':!gradle/wrapper/*' ':!functions/package-lock.json')

found=0
for entry in "${PATTERNS[@]}"; do
  name="${entry%%|*}"
  regex="${entry#*|}"
  if [[ "${1:-}" == "--staged" ]]; then
    hits=$(git grep -nIE --cached -e "$regex" -- . "${EXCLUDES[@]}" || true)
  else
    hits=$(git grep -nIE -e "$regex" -- . "${EXCLUDES[@]}" || true)
  fi
  if [[ -n "$hits" ]]; then
    found=1
    echo "Possible $name found:" >&2
    # Print file and line only, never the secret itself, so it does not end up in a CI log.
    echo "$hits" | cut -d: -f1,2 | sed 's/^/  /' >&2
  fi
done

if [[ $found -ne 0 ]]; then
  echo >&2
  echo "Remove the value, load it from local.properties or Firebase Secret Manager instead, and revoke it if it was ever pushed." >&2
  exit 1
fi
echo "No credentials found."

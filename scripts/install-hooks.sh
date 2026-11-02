#!/usr/bin/env bash
# One-time setup after cloning: make git run the secret check before every commit.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
git config core.hooksPath .githooks
echo "Pre-commit secret check enabled (git config core.hooksPath .githooks)."

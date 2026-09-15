#!/usr/bin/env bash
# Replaces the committed Fulcrum spec with the document at the pinned commit. Run it when the
# "Fulcrum Spec In Sync" check fails on a Renovate PR:
#
#   git fetch && git switch <renovate-branch>
#   .github/scripts/sync-fulcrum-spec-to-pin.sh
#   git commit -am 'chore(deps): refresh Fulcrum OpenAPI spec' && git push
#
# The pom is left alone: Renovate owns the pin.
set -euo pipefail

# shellcheck source=.github/scripts/fulcrum-spec-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/fulcrum-spec-common.sh"

resolve_pinned_spec_commit

previous_hash=""
if [ -f "${COMMITTED_SPEC}" ]; then
  previous_hash="$(sha256_of "${COMMITTED_SPEC}")"
fi

echo "Fetching the Fulcrum spec at ${PINNED_SPEC_COMMIT}"

upstream_spec="$(mktemp)"
trap 'rm -f "${upstream_spec}"' EXIT
download_upstream_spec "${PINNED_SPEC_COMMIT}" "${upstream_spec}"

mkdir -p "$(dirname "${COMMITTED_SPEC}")"
cp "${upstream_spec}" "${COMMITTED_SPEC}"

refreshed_hash="$(sha256_of "${COMMITTED_SPEC}")"
if [ "${previous_hash}" = "${refreshed_hash}" ]; then
  echo "Nothing to commit: the pinned commit did not change the spec (sha256 ${refreshed_hash})."
else
  echo "Updated the spec: sha256 ${previous_hash:-<none>} -> ${refreshed_hash}"
  echo "Commit ${COMMITTED_SPEC_PATH}."
fi

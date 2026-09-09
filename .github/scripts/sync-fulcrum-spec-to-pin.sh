#!/usr/bin/env bash
# Brings the committed Fulcrum OpenAPI spec up to the commit pinned in the root pom (MON-142).
#
# Run this when the "Fulcrum Spec In Sync" check fails on a Renovate PR:
#
#   git fetch && git switch <renovate-branch>
#   .github/scripts/sync-fulcrum-spec-to-pin.sh
#   git commit -am 'chore(deps): refresh Fulcrum OpenAPI spec' && git push
#
# It only replaces the committed spec with the document at fulcrum.spec.ref. The pom is left
# alone - Renovate owns the pin, and no hash is stored anywhere that would need updating too.
set -euo pipefail

# shellcheck source=.github/scripts/fulcrum-spec-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/fulcrum-spec-common.sh"

resolve_pinned_spec_commit
previous_hash="$([ -f "${COMMITTED_SPEC}" ] && sha256_of "${COMMITTED_SPEC}" || echo "")"

echo "Fetching the Fulcrum spec at ${PINNED_SPEC_COMMIT}"

upstream_spec="$(mktemp)"
trap 'rm -f "${upstream_spec}"' EXIT
download_upstream_spec "${PINNED_SPEC_COMMIT}" "${upstream_spec}"

mkdir -p "$(dirname "${COMMITTED_SPEC}")"
cp "${upstream_spec}" "${COMMITTED_SPEC}"

refreshed_hash="$(sha256_of "${COMMITTED_SPEC}")"
if [ "${previous_hash}" = "${refreshed_hash}" ]; then
  echo "The committed spec was already up to date (sha256 ${refreshed_hash})."
  echo "Nothing to commit: the pinned commit did not change the spec."
else
  echo "Updated the spec: sha256 ${previous_hash:-<none>} -> ${refreshed_hash}"
  echo "Commit contracts/src/main/resources/fulcrum/rest-api.json."
fi

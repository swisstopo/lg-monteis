#!/usr/bin/env bash
# Verifies that the committed Fulcrum spec is the document at the commit pinned in the root pom
# (MON-142). Run by the "Fulcrum Spec In Sync" job in pr-gate.
#
# Nothing about the upstream document is recorded in the repository except fulcrum.spec.ref, so
# there is no stored hash that could fall out of step: the spec at that commit is fetched and
# compared against the committed copy directly. That covers both ways they can disagree - a
# Renovate bump onto a commit that changed the spec, and a committed spec edited by hand.
#
# Either way the fix is the same: run .github/scripts/sync-fulcrum-spec-to-pin.sh and commit it.
set -euo pipefail

# shellcheck source=.github/scripts/fulcrum-spec-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/fulcrum-spec-common.sh"

if [ ! -f "${COMMITTED_SPEC}" ]; then
  abort "The committed Fulcrum spec is missing at contracts/src/main/resources/fulcrum/rest-api.json. Run .github/scripts/sync-fulcrum-spec-to-pin.sh and commit it."
fi

resolve_pinned_spec_commit

upstream_spec="$(mktemp)"
trap 'rm -f "${upstream_spec}"' EXIT
download_upstream_spec "${PINNED_SPEC_COMMIT}" "${upstream_spec}"

committed_hash="$(sha256_of "${COMMITTED_SPEC}")"
upstream_hash="$(sha256_of "${upstream_spec}")"

if [ "${committed_hash}" != "${upstream_hash}" ]; then
  abort "The committed Fulcrum spec (sha256 ${committed_hash}) is not the document at the pinned commit ${PINNED_SPEC_COMMIT} (sha256 ${upstream_hash}). Run .github/scripts/sync-fulcrum-spec-to-pin.sh on this branch and commit the result."
fi

echo "The committed Fulcrum spec is the document at ${PINNED_SPEC_COMMIT} (sha256 ${committed_hash})."

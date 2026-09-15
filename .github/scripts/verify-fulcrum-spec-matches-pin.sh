#!/usr/bin/env bash
# Fails when the committed Fulcrum spec is not the document at the pinned commit. Run by the
# "Fulcrum Spec In Sync" job in pr-gate.
#
# The spec at the pin is fetched and compared directly, so no stored hash can fall out of step and
# both ways the two can disagree are covered: a Renovate bump onto a commit that changed the spec,
# and a committed spec edited by hand. Either is fixed with sync-fulcrum-spec-to-pin.sh.
set -euo pipefail

# shellcheck source=.github/scripts/fulcrum-spec-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/fulcrum-spec-common.sh"

if [ ! -f "${COMMITTED_SPEC}" ]; then
  abort "The committed Fulcrum spec is missing at ${COMMITTED_SPEC_PATH}. Run .github/scripts/sync-fulcrum-spec-to-pin.sh and commit it."
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

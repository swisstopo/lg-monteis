#!/usr/bin/env bash
# Shared helpers for the Fulcrum spec scripts (MON-142). Sourced, never run directly.
#
# The spec lives in this repository as a committed file, and the root pom records the single
# thing needed to identify it: the upstream commit it came from (fulcrum.spec.ref), the "pin"
# the sibling script names refer to. Renovate bumps that pin, verify-fulcrum-spec-matches-pin.sh
# compares the committed file against the document at it, and sync-fulcrum-spec-to-pin.sh brings
# the file back in line when the two differ.

readonly UPSTREAM_REPO="fulcrumapp/api"
readonly UPSTREAM_SPEC_PATH="reference/rest-api.json"
readonly PINNED_COMMIT_PROPERTY="fulcrum.spec.ref"

REPO_ROOT="$(git rev-parse --show-toplevel)"
readonly REPO_ROOT
readonly ROOT_POM="${REPO_ROOT}/pom.xml"
readonly COMMITTED_SPEC="${REPO_ROOT}/contracts/src/main/resources/fulcrum/rest-api.json"

# GitHub Actions turns ::error:: into an annotation, but only when it is written to stdout;
# elsewhere it is just a readable line.
abort() {
  echo "::error::$1"
  exit 1
}

# Reads the commit the committed spec is supposed to come from into PINNED_SPEC_COMMIT. Read
# through Maven rather than by parsing the XML, so the pom stays the single source of truth even
# if the property moves into a profile or gets inherited.
#
# Sets a variable instead of echoing the value, so that a failure here can still reach the console
# with its message - inside a $( ) the abort text would be captured along with the value.
resolve_pinned_spec_commit() {
  PINNED_SPEC_COMMIT="$(
    mvn -q -B -N -f "${ROOT_POM}" \
      help:evaluate -Dexpression="${PINNED_COMMIT_PROPERTY}" -DforceStdout |
      tail -n 1 | tr -d '[:space:]'
  )"

  # A missing property makes help:evaluate print "null object or invalid expression" instead of
  # failing, so the shape of the value is what tells us it is real.
  if ! [[ "${PINNED_SPEC_COMMIT}" =~ ^[0-9a-f]{40}$ ]]; then
    abort "${PINNED_COMMIT_PROPERTY} in pom.xml is not a commit SHA (got: ${PINNED_SPEC_COMMIT:-<empty>})."
  fi
}

upstream_spec_url() {
  local commit="$1"
  echo "https://raw.githubusercontent.com/${UPSTREAM_REPO}/${commit}/${UPSTREAM_SPEC_PATH}"
}

# Fails on anything but a JSON body: raw.githubusercontent answers an unknown commit with a
# plain-text 404 page rather than an HTTP error in some cases.
download_upstream_spec() {
  local commit="$1" destination="$2" url
  url="$(upstream_spec_url "${commit}")"

  if ! curl --silent --show-error --fail --location --retry 3 --retry-delay 2 \
    --output "${destination}" "${url}"; then
    abort "Could not download the Fulcrum spec from ${url}."
  fi

  if ! head -c 1 "${destination}" | grep -q '{'; then
    abort "${url} did not return a JSON document."
  fi
}

# sha256sum is GNU, shasum ships with macOS.
sha256_of() {
  local file="$1"
  if command -v sha256sum > /dev/null 2>&1; then
    sha256sum "${file}" | cut -d ' ' -f 1
  else
    shasum -a 256 "${file}" | cut -d ' ' -f 1
  fi
}

#!/usr/bin/env bash
# Helpers shared by the Fulcrum spec scripts. Sourced, never run directly.
#
# The spec is committed in the contracts module, and contracts/pom.xml records the upstream commit
# it came from in fulcrum.spec.ref - the "pin" the sibling scripts refer to. Renovate bumps that
# pin, verify-fulcrum-spec-matches-pin.sh checks the committed file still matches it, and
# sync-fulcrum-spec-to-pin.sh brings the file back in line.

readonly UPSTREAM_REPO="fulcrumapp/api"
readonly UPSTREAM_SPEC_PATH="reference/rest-api.json"
readonly PINNED_COMMIT_PROPERTY="fulcrum.spec.ref"

REPO_ROOT="$(git rev-parse --show-toplevel)"
readonly REPO_ROOT
readonly CONTRACTS_POM="${REPO_ROOT}/contracts/pom.xml"

# Kept as both forms: the absolute path to work with, the relative one for messages to a reader.
readonly COMMITTED_SPEC_PATH="contracts/src/main/resources/fulcrum/rest-api.json"
readonly COMMITTED_SPEC="${REPO_ROOT}/${COMMITTED_SPEC_PATH}"

# ::error:: becomes a GitHub Actions annotation, but only on stdout.
abort() {
  echo "::error::$1"
  exit 1
}

# Reads fulcrum.spec.ref into PINNED_SPEC_COMMIT.
#
# Through Maven rather than by parsing the XML, so the property is free to move into a profile or
# be inherited. Assigns instead of echoing: inside a $( ) an abort message would be captured as
# part of the value rather than reaching the console.
resolve_pinned_spec_commit() {
  PINNED_SPEC_COMMIT="$(
    mvn -q -B -N -f "${CONTRACTS_POM}" \
      help:evaluate -Dexpression="${PINNED_COMMIT_PROPERTY}" -DforceStdout |
      tail -n 1 | tr -d '[:space:]'
  )"

  # A missing property prints "null object or invalid expression" rather than failing, so the
  # shape of the value is what tells us it is real.
  if ! [[ "${PINNED_SPEC_COMMIT}" =~ ^[0-9a-f]{40}$ ]]; then
    abort "${PINNED_COMMIT_PROPERTY} in ${CONTRACTS_POM} is not a commit SHA (got: ${PINNED_SPEC_COMMIT:-<empty>})."
  fi
}

upstream_spec_url() {
  local commit="$1"
  echo "https://raw.githubusercontent.com/${UPSTREAM_REPO}/${commit}/${UPSTREAM_SPEC_PATH}"
}

download_upstream_spec() {
  local commit="$1" destination="$2" url
  url="$(upstream_spec_url "${commit}")"

  if ! curl --silent --show-error --fail --location --retry 3 --retry-delay 2 \
    --output "${destination}" "${url}"; then
    abort "Could not download the Fulcrum spec from ${url}."
  fi

  # An unknown commit can come back as a plain-text 404 page instead of an HTTP error.
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

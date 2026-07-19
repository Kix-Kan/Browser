#!/usr/bin/env bash
set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
# shellcheck source=/dev/null
source "${REPO_ROOT}/VERSION.env"

readonly DOWNLOAD_DIR="${REPO_ROOT}/downloads"
readonly SOURCE_NAME="firefox-${FIREFOX_ESR_VERSION}.source.tar.xz"
readonly RELEASE_ROOT="https://archive.mozilla.org/pub/firefox/releases/${FIREFOX_ESR_VERSION}"
readonly MOZILLA_RELEASE_FINGERPRINT="14F26682D0916CDD81E37B6D61B7B526D98F0353"

mkdir -p "${DOWNLOAD_DIR}"
curl --fail --location --proto '=https' --tlsv1.2 --output "${DOWNLOAD_DIR}/${SOURCE_NAME}" \
  "${RELEASE_ROOT}/source/${SOURCE_NAME}"
curl --fail --location --proto '=https' --tlsv1.2 --output "${DOWNLOAD_DIR}/${SOURCE_NAME}.asc" \
  "${RELEASE_ROOT}/source/${SOURCE_NAME}.asc"
curl --fail --location --proto '=https' --tlsv1.2 --output "${DOWNLOAD_DIR}/Mozilla-KEY.asc" \
  "${RELEASE_ROOT}/KEY"

printf '%s  %s\n' "${FIREFOX_SOURCE_SHA512}" "${DOWNLOAD_DIR}/${SOURCE_NAME}" | sha512sum --check --strict

key_home="$(mktemp -d)"
trap 'rm -rf -- "${key_home}"' EXIT
chmod 700 "${key_home}"
gpg --homedir "${key_home}" --batch --import "${DOWNLOAD_DIR}/Mozilla-KEY.asc"
fingerprints="$(gpg --homedir "${key_home}" --batch --with-colons --fingerprint | awk -F: '$1 == "fpr" {print $10}')"
grep -qx "${MOZILLA_RELEASE_FINGERPRINT}" <<<"${fingerprints}" || {
  echo "Mozilla release root-key fingerprint did not match the pinned value" >&2
  exit 65
}
gpg --homedir "${key_home}" --batch --verify "${DOWNLOAD_DIR}/${SOURCE_NAME}.asc" "${DOWNLOAD_DIR}/${SOURCE_NAME}"
echo "Verified Mozilla source: ${SOURCE_NAME}"


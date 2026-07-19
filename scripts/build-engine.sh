#!/usr/bin/env bash
set -Eeuo pipefail
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
# shellcheck source=/dev/null
source "${REPO_ROOT}/VERSION.env"
readonly SOURCE_ARCHIVE="${REPO_ROOT}/downloads/firefox-${FIREFOX_ESR_VERSION}.source.tar.xz"
readonly BUILD_ROOT="${REPO_ROOT}/build/firefox-source"
readonly ARTIFACT_DIR="${REPO_ROOT}/artifacts"

[[ "$(uname -s)" == Linux ]] || { echo "Engine builds require Linux" >&2; exit 69; }
[[ -s "${SOURCE_ARCHIVE}" ]] || { echo "Run scripts/fetch-source.sh first" >&2; exit 66; }
command -v python3 >/dev/null
command -v convert >/dev/null || { echo "ImageMagick 'convert' is required for branding sizes" >&2; exit 69; }
[[ "${BUILD_ROOT}" == "${REPO_ROOT}/build/firefox-source" ]] || exit 70
rm -rf -- "${BUILD_ROOT}"
mkdir -p "${BUILD_ROOT}" "${ARTIFACT_DIR}"
tar --extract --xz --strip-components=1 --file "${SOURCE_ARCHIVE}" --directory "${BUILD_ROOT}"

rm -rf -- "${BUILD_ROOT}/browser/branding/kixkan"
cp -a "${BUILD_ROOT}/browser/branding/unofficial" "${BUILD_ROOT}/browser/branding/kixkan"
cp -a "${REPO_ROOT}/browser/branding/kixkan/." "${BUILD_ROOT}/browser/branding/kixkan/"
for size in 16 32 48 64 128; do
  convert "${REPO_ROOT}/branding/kixkan-logo.png" -resize "${size}x${size}" \
    "${BUILD_ROOT}/browser/branding/kixkan/default${size}.png"
done
convert "${REPO_ROOT}/branding/kixkan-logo.png" -resize 192x192 \
  "${BUILD_ROOT}/browser/branding/kixkan/content/about-logo.png"
cp "${REPO_ROOT}/config/mozconfig" "${BUILD_ROOT}/.mozconfig"

export SOURCE_DATE_EPOCH
cd "${BUILD_ROOT}"
./mach build
./mach package
package="$(find obj-kixkan/dist -maxdepth 1 -type f \( -name '*.tar.xz' -o -name '*.tar.bz2' \) -print -quit)"
[[ -n "${package}" ]] || { echo "Mozilla package artifact was not produced" >&2; exit 65; }
readonly PACKAGE_ROOT="${REPO_ROOT}/build/package-root"
[[ "${PACKAGE_ROOT}" == "${REPO_ROOT}/build/package-root" ]] || exit 70
rm -rf -- "${PACKAGE_ROOT}"
mkdir -p "${PACKAGE_ROOT}"
tar --extract --file "${package}" --directory "${PACKAGE_ROOT}"
tar --create --xz --sort=name --mtime="@${SOURCE_DATE_EPOCH}" --owner=0 --group=0 --numeric-owner \
  --file "${ARTIFACT_DIR}/kixkan-browser-engine-${KIXKAN_BROWSER_VERSION}-amd64.tar.xz" \
  --directory "${PACKAGE_ROOT}" .
sha256sum "${ARTIFACT_DIR}/kixkan-browser-engine-${KIXKAN_BROWSER_VERSION}-amd64.tar.xz" > \
  "${ARTIFACT_DIR}/kixkan-browser-engine-${KIXKAN_BROWSER_VERSION}-amd64.tar.xz.sha256"

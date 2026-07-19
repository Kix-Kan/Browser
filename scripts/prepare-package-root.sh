#!/usr/bin/env bash
set -Eeuo pipefail
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
# shellcheck source=/dev/null
source "${REPO_ROOT}/VERSION.env"
readonly ENGINE="${REPO_ROOT}/artifacts/kixkan-browser-engine-${KIXKAN_BROWSER_VERSION}-amd64.tar.xz"
readonly ROOT="${REPO_ROOT}/build/package-root"
[[ -s "${ENGINE}" ]] || { echo "Missing engine artifact: ${ENGINE}" >&2; exit 66; }
[[ "${ROOT}" == "${REPO_ROOT}/build/package-root" ]] || exit 70
rm -rf -- "${ROOT}"
mkdir -p "${ROOT}/usr/lib/kixkan-browser/extensions" "${ROOT}/usr/bin" \
  "${ROOT}/usr/share/applications" "${ROOT}/usr/share/icons/hicolor/128x128/apps" \
  "${ROOT}/usr/lib/systemd/system" "${ROOT}/usr/lib/kixkan-browser/distribution" \
  "${ROOT}/etc/apt/apt.conf.d"
tar --extract --auto-compress --strip-components=1 --file "${ENGINE}" --directory "${ROOT}/usr/lib/kixkan-browser"
cp -a "${REPO_ROOT}/distribution/." "${ROOT}/usr/lib/kixkan-browser/distribution/"
install -m 0644 "${REPO_ROOT}/artifacts/kixkan-start.xpi" "${ROOT}/usr/lib/kixkan-browser/extensions/"
install -m 0755 "${REPO_ROOT}/packaging/kixkan-browser" "${ROOT}/usr/bin/"
install -m 0755 "${REPO_ROOT}/packaging/kixkan-browser-update" "${ROOT}/usr/bin/"
install -m 0644 "${REPO_ROOT}/packaging/kixkan-browser.desktop" "${ROOT}/usr/share/applications/"
install -m 0644 "${REPO_ROOT}/branding/kixkan-logo.png" "${ROOT}/usr/share/icons/hicolor/128x128/apps/kixkan-browser.png"
install -m 0644 "${REPO_ROOT}/packaging/kixkan-browser-update.service" "${ROOT}/usr/lib/systemd/system/"
install -m 0644 "${REPO_ROOT}/packaging/kixkan-browser-update.timer" "${ROOT}/usr/lib/systemd/system/"
install -m 0644 "${REPO_ROOT}/packaging/52kixkan-browser-unattended-upgrades" "${ROOT}/etc/apt/apt.conf.d/"


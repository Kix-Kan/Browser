#!/usr/bin/env bash
set -Eeuo pipefail
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
: "${KIXKAN_DEB_SIGNING_KEY:?KIXKAN_DEB_SIGNING_KEY must identify the protected signing key}"
command -v debsigs >/dev/null || { echo "debsigs is required" >&2; exit 69; }
shopt -s nullglob
packages=("${REPO_ROOT}"/artifacts/kixkan-browser_*.deb)
(( ${#packages[@]} > 0 )) || { echo "No Debian package to sign" >&2; exit 66; }
for package in "${packages[@]}"; do
  debsigs --sign=origin --default-key="${KIXKAN_DEB_SIGNING_KEY}" "${package}"
  sha256sum "${package}" > "${package}.sha256"
  sha512sum "${package}" > "${package}.sha512"
done


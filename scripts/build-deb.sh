#!/usr/bin/env bash
set -Eeuo pipefail
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
command -v dpkg-buildpackage >/dev/null || { echo "dpkg-buildpackage is required" >&2; exit 69; }
"${REPO_ROOT}/scripts/build-extension.sh"
"${REPO_ROOT}/scripts/prepare-package-root.sh"
(cd "${REPO_ROOT}" && dpkg-buildpackage --build=binary --no-sign)
mkdir -p "${REPO_ROOT}/artifacts"
find "${REPO_ROOT}/.." -maxdepth 1 -type f -name 'kixkan-browser_*.deb' -exec mv -- {} "${REPO_ROOT}/artifacts/" \;


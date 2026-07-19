#!/usr/bin/env bash
set -Eeuo pipefail
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
while IFS= read -r -d '' json; do
  python3 -m json.tool "${json}" >/dev/null
done < <(find "${REPO_ROOT}" -type f -name '*.json' -not -path '*/build/*' -print0)
python3 -m unittest discover -s "${REPO_ROOT}/tests" -v
grep -q '^FIREFOX_SOURCE_SHA512=[0-9a-f]\{128\}$' "${REPO_ROOT}/VERSION.env"
command -v shellcheck >/dev/null && shellcheck "${REPO_ROOT}"/scripts/*.sh "${REPO_ROOT}"/packaging/kixkan-browser*
echo "KixKan Browser overlay validation passed"

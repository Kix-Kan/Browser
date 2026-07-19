#!/usr/bin/env bash
set -Eeuo pipefail
readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd -P)"
readonly OUTPUT_DIR="${REPO_ROOT}/artifacts"
mkdir -p "${OUTPUT_DIR}"
python3 -m json.tool "${REPO_ROOT}/extension/manifest.json" >/dev/null
(cd "${REPO_ROOT}/extension" && zip -q -r -X "${OUTPUT_DIR}/kixkan-start.xpi" manifest.json start)
sha256sum "${OUTPUT_DIR}/kixkan-start.xpi" > "${OUTPUT_DIR}/kixkan-start.xpi.sha256"
echo "Built ${OUTPUT_DIR}/kixkan-start.xpi"


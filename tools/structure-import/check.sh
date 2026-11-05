#!/usr/bin/env bash
# CI entry point for the structure import tool (specs/addon/structures.md): unit tests, then verify that every committed
# generated file (templates, worldgen JSON, tags, data map, docs/licences/structures.md, the NOTICE block) is up to date.
# verify also checks every palette and committed template against vanilla_block_ids.json and me_block_ids.json
# (regenerate those with extract_vanilla_block_ids.py and extract_me_block_ids.py <middle-earth source>).
# Standard library Python 3.11+ only; no game, no network.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
cd "$root"
py="${PYTHON:-python3}"
"$py" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else "structure-import needs Python 3.11+")'
echo "== structure-import: unit tests"
"$py" -I -m unittest discover -s tools/structure-import/tests -t tools/structure-import/tests
echo "== structure-import: verify generated files"
"$py" -I tools/structure-import/import.py verify

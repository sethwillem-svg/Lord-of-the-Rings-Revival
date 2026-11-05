#!/usr/bin/env python3
"""Structure import tool (specs/addon/structures.md). Run: python3 -I tools/structure-import/import.py <command>."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))   # -I does not add the script's folder

from sinr.cli import main  # noqa: E402

if __name__ == "__main__":
    sys.exit(main())

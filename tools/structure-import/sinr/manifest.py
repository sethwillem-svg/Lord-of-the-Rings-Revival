"""Build manifests (``builds/<id>/build.json``): schema validation and the automatic licence gate."""
from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path

from . import licences

TOOL_DIR = Path(__file__).resolve().parent.parent
SCHEMA_PATH = TOOL_DIR / "build.schema.json"
FORBIDDEN_ME = ("middle-earth:structure_manager", "middle-earth:orc_structure_manager", "middle-earth:structure_nest")


class GateError(ValueError):
    def __init__(self, errors):
        super().__init__("; ".join(errors))
        self.errors = list(errors)


# ---------------------------------------------------------------------------------------------------- tiny JSON Schema

_TYPES = {
    "string": lambda v: isinstance(v, str),
    "integer": lambda v: isinstance(v, int) and not isinstance(v, bool),
    "number": lambda v: isinstance(v, (int, float)) and not isinstance(v, bool),
    "boolean": lambda v: isinstance(v, bool),
    "object": lambda v: isinstance(v, dict),
    "array": lambda v: isinstance(v, list),
    "null": lambda v: v is None,
}


def validate_schema(value, schema: dict, path: str = "$") -> list:
    """The subset of JSON Schema our manifest schema uses: type, enum, required, properties, additionalProperties,
    items, pattern, minLength, minimum, maximum, minItems, maxItems."""
    errs = []
    t = schema.get("type")
    if t is not None:
        types = t if isinstance(t, list) else [t]
        if not any(_TYPES[x](value) for x in types):
            return [f"{path}: expected {'/'.join(types)}, got {type(value).__name__}"]
    if "enum" in schema and value not in schema["enum"]:
        errs.append(f"{path}: {value!r} not one of {schema['enum']}")
    if isinstance(value, str):
        if "pattern" in schema and not re.search(schema["pattern"], value):
            errs.append(f"{path}: {value!r} does not match {schema['pattern']}")
        if len(value) < schema.get("minLength", 0):
            errs.append(f"{path}: too short")
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if "minimum" in schema and value < schema["minimum"]:
            errs.append(f"{path}: {value} < {schema['minimum']}")
        if "maximum" in schema and value > schema["maximum"]:
            errs.append(f"{path}: {value} > {schema['maximum']}")
    if isinstance(value, list):
        if len(value) < schema.get("minItems", 0):
            errs.append(f"{path}: fewer than {schema['minItems']} items")
        if "maxItems" in schema and len(value) > schema["maxItems"]:
            errs.append(f"{path}: more than {schema['maxItems']} items")
        if "items" in schema:
            for i, item in enumerate(value):
                errs += validate_schema(item, schema["items"], f"{path}[{i}]")
    if isinstance(value, dict):
        for k in schema.get("required", []):
            if k not in value:
                errs.append(f"{path}: missing {k}")
        props = schema.get("properties", {})
        for k, v in value.items():
            if k in props:
                errs += validate_schema(v, props[k], f"{path}.{k}")
            elif schema.get("additionalProperties", True) is False:
                errs.append(f"{path}: unknown field {k}")
    return errs


def load_schema() -> dict:
    return json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))


def load(path) -> dict:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def save(path, manifest: dict):
    Path(path).write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def sha256(path) -> str:
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


# ---------------------------------------------------------------------------------------------------- licence gate

def buildable(m: dict) -> bool:
    """Manifests that produce outputs: own work, approved and imported (not candidate, rejected or withdrawn)."""
    if m.get("status") in ("rejected", "withdrawn", "candidate"):
        return False
    return bool(m.get("own_work")) or m.get("status") in ("approved", "imported")


def gate(m: dict, build_dir: Path | None = None, grid=None, schema: dict | None = None) -> list:
    """Every reason the tool refuses to build this manifest. Empty list: the gate passes."""
    errs = validate_schema(m, schema if schema is not None else load_schema())
    if errs:
        return errs
    own = bool(m.get("own_work"))
    status = m.get("status")
    if status in ("rejected", "withdrawn"):
        errs.append(f"status is {status}")
    elif not own and status not in ("approved", "imported"):
        errs.append(f"status is {status}: only the maintainer approves builds")
    if own:
        if m["licence"] != licences.OWN:
            errs.append(f"own work must use {licences.OWN}")
    else:
        why = licences.refusal_reason(m["licence"])
        if why:
            errs.append(why)
        for k in ("author", "source_url", "archive_url", "retrieved", "licence_url"):
            if not m.get(k):
                errs.append(f"missing {k}")
        if status in ("approved", "imported") and not (m.get("approved_by") and m.get("approved_on")):
            errs.append("approved without approved_by and approved_on")
    if build_dir is not None and m.get("source_file"):
        src = Path(build_dir) / m["source_file"]
        if not src.exists():
            errs.append(f"source file {m['source_file']} is missing")
        elif m.get("source_sha256") and sha256(src) != m["source_sha256"]:
            errs.append("source_sha256 does not match the source file")
        elif not m.get("source_sha256"):
            errs.append("missing source_sha256")
    if grid is not None:
        errs += content_gate(m, grid)
    return errs


def content_gate(m: dict, grid) -> list:
    errs = []
    names = {s.name for s in grid.blocks} | {str(t.get("id", "")) for t in grid.block_entities.values()}
    lotr = sorted(n for n in names if n.startswith("lotr:"))
    if lotr:
        errs.append(f"original LOTR mod content found ({', '.join(lotr[:5])}): an export, refused")
    if not m.get("own_work"):
        me = sorted(n for n in names if n in FORBIDDEN_ME)
        if me:
            errs.append(f"Middle-earth manager or nest blocks found ({', '.join(me)}): an export of Middle-earth's "
                        "structures, refused")
    return errs

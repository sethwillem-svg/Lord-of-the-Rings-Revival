"""Input formats. ``read_file`` picks the reader from the content (and refuses formats we do not read)."""
from __future__ import annotations

from pathlib import Path

from .. import nbt
from . import litematic, sponge, vanilla


class FormatError(ValueError):
    pass


def detect(root: dict) -> str:
    if litematic.is_litematic(root):
        return "litematic"
    if vanilla.is_vanilla(root):
        return "vanilla"
    if sponge.is_sponge(root):
        s = root.get("Schematic", root)
        return "sponge_v3" if int(s.get("Version", 3 if "Schematic" in root else 2)) >= 3 else "sponge_v2"
    if "Materials" in root or ("Blocks" in root and "Data" in root):
        raise FormatError("MCEdit .schematic with numeric ids (before 1.13): re-save in a 1.13+ editor")
    raise FormatError("unknown structure format")


def read_bytes(data: bytes, name: str = ""):
    """Returns (format name, Grid)."""
    if name.lower().endswith(".mcstructure"):
        raise FormatError("Bedrock .mcstructure is not supported")
    try:
        _, root = nbt.loads(data)
    except Exception as e:  # noqa: BLE001 - any parse failure is a format error for the user
        raise FormatError(f"not an NBT file: {e}") from e
    fmt = detect(root)
    reader = {"litematic": litematic.read, "vanilla": vanilla.read,
              "sponge_v2": sponge.read, "sponge_v3": sponge.read}[fmt]
    return fmt, reader(root)


def read_file(path):
    p = Path(path)
    return read_bytes(p.read_bytes(), p.name)

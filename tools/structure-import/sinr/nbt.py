"""Minimal NBT reader and writer (Java edition, big-endian), standard library only.

Python values map to tags like this:

    Byte/Short/Int/Long/Float/Double   wrapper classes below (subclasses of int / float)
    str                                String
    ByteArray / IntArray / LongArray   wrapper classes (subclasses of list)
    TagList                            List (remembers its element type, so empty lists round-trip)
    dict                               Compound (insertion order is kept, so output is deterministic)

Plain ``int`` is written as Int, ``float`` as Double, ``bool`` as Byte, a plain ``list`` as a TagList whose element type
is guessed from the first item.
"""
from __future__ import annotations

import gzip
import io
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Byte(int):
    tag = BYTE


class Short(int):
    tag = SHORT


class Int(int):
    tag = INT


class Long(int):
    tag = LONG


class Float(float):
    tag = FLOAT


class Double(float):
    tag = DOUBLE


class ByteArray(list):
    tag = BYTE_ARRAY


class IntArray(list):
    tag = INT_ARRAY


class LongArray(list):
    tag = LONG_ARRAY


class TagList(list):
    """A List tag. ``elem`` is the element tag id (END for an empty list of unknown type)."""
    tag = LIST

    def __init__(self, items=(), elem: int | None = None):
        super().__init__(items)
        self.elem = elem if elem is not None else (tag_of(self[0]) if self else END)


class NbtError(ValueError):
    pass


def tag_of(v) -> int:
    t = getattr(type(v), "tag", None)
    if t is not None:
        return t
    if isinstance(v, bool):
        return BYTE
    if isinstance(v, int):
        return INT
    if isinstance(v, float):
        return DOUBLE
    if isinstance(v, str):
        return STRING
    if isinstance(v, dict):
        return COMPOUND
    if isinstance(v, (list, tuple)):
        return LIST
    raise NbtError(f"no NBT tag for {type(v).__name__}")


# ---------------------------------------------------------------------------------------------------- modified UTF-8

def encode_mutf8(s: str) -> bytes:
    out = bytearray()
    for ch in s:
        cp = ord(ch)
        units = [cp] if cp <= 0xFFFF else [0xD800 + ((cp - 0x10000) >> 10), 0xDC00 + ((cp - 0x10000) & 0x3FF)]
        for u in units:
            if 0x0001 <= u <= 0x007F:
                out.append(u)
            elif u <= 0x07FF:   # includes U+0000, written as two bytes
                out += bytes((0xC0 | (u >> 6), 0x80 | (u & 0x3F)))
            else:
                out += bytes((0xE0 | (u >> 12), 0x80 | ((u >> 6) & 0x3F), 0x80 | (u & 0x3F)))
    return bytes(out)


def decode_mutf8(b: bytes) -> str:
    units = []
    i, n = 0, len(b)
    while i < n:
        c = b[i]
        if c < 0x80:
            units.append(c)
            i += 1
        elif c & 0xE0 == 0xC0:
            if i + 1 >= n:
                raise NbtError("truncated modified UTF-8")
            units.append(((c & 0x1F) << 6) | (b[i + 1] & 0x3F))
            i += 2
        elif c & 0xF0 == 0xE0:
            if i + 2 >= n:
                raise NbtError("truncated modified UTF-8")
            units.append(((c & 0x0F) << 12) | ((b[i + 1] & 0x3F) << 6) | (b[i + 2] & 0x3F))
            i += 3
        else:
            raise NbtError(f"bad modified UTF-8 byte 0x{c:02x}")
    chars = []
    j = 0
    while j < len(units):
        u = units[j]
        if 0xD800 <= u <= 0xDBFF and j + 1 < len(units) and 0xDC00 <= units[j + 1] <= 0xDFFF:
            chars.append(chr(0x10000 + ((u - 0xD800) << 10) + (units[j + 1] - 0xDC00)))
            j += 2
        else:
            chars.append(chr(u))
            j += 1
    return "".join(chars)


# ---------------------------------------------------------------------------------------------------- writing

def _write_str(out: io.BytesIO, s: str):
    b = encode_mutf8(s)
    if len(b) > 0xFFFF:
        raise NbtError("string too long for NBT")
    out.write(struct.pack(">H", len(b)))
    out.write(b)


def _write_payload(out: io.BytesIO, t: int, v):
    if t == BYTE:
        out.write(struct.pack(">b", int(v)))
    elif t == SHORT:
        out.write(struct.pack(">h", int(v)))
    elif t == INT:
        out.write(struct.pack(">i", int(v)))
    elif t == LONG:
        out.write(struct.pack(">q", int(v)))
    elif t == FLOAT:
        out.write(struct.pack(">f", float(v)))
    elif t == DOUBLE:
        out.write(struct.pack(">d", float(v)))
    elif t == BYTE_ARRAY:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(f">{len(v)}b", *v))
    elif t == STRING:
        _write_str(out, v)
    elif t == LIST:
        elem = v.elem if isinstance(v, TagList) else (tag_of(v[0]) if v else END)
        for item in v:
            if tag_of(item) != elem and not (BYTE <= elem <= DOUBLE and type(item) in (int, float, bool)):
                raise NbtError(f"mixed list: {tag_of(item)} in a list of {elem}")
        out.write(struct.pack(">bi", elem, len(v)))
        for item in v:
            _write_payload(out, elem, item)
    elif t == COMPOUND:
        for k, item in v.items():
            it = tag_of(item)
            out.write(struct.pack(">b", it))
            _write_str(out, k)
            _write_payload(out, it, item)
        out.write(b"\x00")
    elif t == INT_ARRAY:
        out.write(struct.pack(f">i{len(v)}i", len(v), *v))
    elif t == LONG_ARRAY:
        out.write(struct.pack(f">i{len(v)}q", len(v), *v))
    else:
        raise NbtError(f"unknown tag {t}")


def dumps(root: dict, name: str = "", compress: bool = True) -> bytes:
    """Writes ``root`` as a named root Compound. Gzip output is deterministic (mtime 0)."""
    out = io.BytesIO()
    out.write(b"\x0a")
    _write_str(out, name)
    _write_payload(out, COMPOUND, root)
    raw = out.getvalue()
    return gzip.compress(raw, compresslevel=9, mtime=0) if compress else raw


# ---------------------------------------------------------------------------------------------------- reading

class _Reader:
    def __init__(self, data: bytes):
        self.d = data
        self.i = 0

    def take(self, n: int) -> bytes:
        if self.i + n > len(self.d):
            raise NbtError("unexpected end of NBT data")
        b = self.d[self.i:self.i + n]
        self.i += n
        return b

    def unpack(self, fmt: str):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))

    def string(self) -> str:
        (n,) = self.unpack(">H")
        return decode_mutf8(self.take(n))

    def payload(self, t: int, depth: int = 0):
        if depth > 512:
            raise NbtError("NBT nested too deep")
        if t == BYTE:
            return Byte(self.unpack(">b")[0])
        if t == SHORT:
            return Short(self.unpack(">h")[0])
        if t == INT:
            return Int(self.unpack(">i")[0])
        if t == LONG:
            return Long(self.unpack(">q")[0])
        if t == FLOAT:
            return Float(self.unpack(">f")[0])
        if t == DOUBLE:
            return Double(self.unpack(">d")[0])
        if t == BYTE_ARRAY:
            (n,) = self.unpack(">i")
            return ByteArray(self.unpack(f">{n}b"))
        if t == STRING:
            return self.string()
        if t == LIST:
            elem, n = self.unpack(">bi")
            return TagList([self.payload(elem, depth + 1) for _ in range(max(0, n))], elem)
        if t == COMPOUND:
            out = {}
            while True:
                (it,) = self.unpack(">b")
                if it == END:
                    return out
                k = self.string()
                out[k] = self.payload(it, depth + 1)
        if t == INT_ARRAY:
            (n,) = self.unpack(">i")
            return IntArray(self.unpack(f">{n}i"))
        if t == LONG_ARRAY:
            (n,) = self.unpack(">i")
            return LongArray(self.unpack(f">{n}q"))
        raise NbtError(f"unknown tag {t}")


def loads(data: bytes) -> tuple[str, dict]:
    """Reads gzipped or uncompressed NBT. Returns (root name, root compound)."""
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    r = _Reader(data)
    (t,) = r.unpack(">b")
    if t != COMPOUND:
        raise NbtError(f"root tag is {t}, not a compound")
    name = r.string()
    return name, r.payload(COMPOUND)


def load_file(path) -> dict:
    with open(path, "rb") as f:
        return loads(f.read())[1]


def save_file(path, root: dict, name: str = ""):
    with open(path, "wb") as f:
        f.write(dumps(root, name))

"""The tool's block grid: one dense box of block states, plus block entities and entities, whatever the input format."""
from __future__ import annotations

import copy
from dataclasses import dataclass, field

AIR = "minecraft:air"
VOID = "minecraft:structure_void"
EMPTY_NAMES = frozenset({AIR, "minecraft:cave_air", "minecraft:void_air", VOID})


@dataclass(frozen=True, order=True)
class BlockState:
    name: str
    props: tuple = ()   # sorted ((key, value), ...)

    @staticmethod
    def of(name: str, props: dict | None = None) -> "BlockState":
        if ":" not in name:
            name = "minecraft:" + name
        return BlockState(name, tuple(sorted((props or {}).items())))

    @staticmethod
    def parse(s: str) -> "BlockState":
        """Parses ``ns:id[key=value,...]`` (the Sponge palette form)."""
        s = s.strip()
        if "[" in s:
            name, rest = s.split("[", 1)
            if not rest.endswith("]"):
                raise ValueError(f"bad block state {s!r}")
            props = {}
            for part in filter(None, rest[:-1].split(",")):
                k, v = part.split("=", 1)
                props[k.strip()] = v.strip()
            return BlockState.of(name, props)
        return BlockState.of(s)

    @property
    def prop_dict(self) -> dict:
        return dict(self.props)

    def with_props(self, props: dict) -> "BlockState":
        return BlockState.of(self.name, props)

    def __str__(self):
        if not self.props:
            return self.name
        return self.name + "[" + ",".join(f"{k}={v}" for k, v in self.props) + "]"

    @property
    def is_empty(self) -> bool:
        return self.name in EMPTY_NAMES


AIR_STATE = BlockState(AIR)
VOID_STATE = BlockState(VOID)


@dataclass
class Entity:
    pos: tuple           # (x, y, z) floats, relative to the grid origin
    nbt: dict            # includes "id"

    @property
    def type(self) -> str:
        return str(self.nbt.get("id", ""))


@dataclass
class Grid:
    sx: int
    sy: int
    sz: int
    data_version: int = 0
    blocks: list = field(default=None)                 # BlockState per cell, index x + sx * (z + sz * y)
    block_entities: dict = field(default_factory=dict)  # (x, y, z) -> compound without x/y/z (keeps "id")
    entities: list = field(default_factory=list)

    def __post_init__(self):
        if self.sx < 0 or self.sy < 0 or self.sz < 0:
            raise ValueError("negative grid size")
        if self.blocks is None:
            self.blocks = [AIR_STATE] * (self.sx * self.sy * self.sz)

    @property
    def size(self) -> tuple:
        return (self.sx, self.sy, self.sz)

    def _i(self, x, y, z) -> int:
        if not (0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz):
            raise IndexError(f"{x},{y},{z} outside {self.size}")
        return x + self.sx * (z + self.sz * y)

    def inside(self, x, y, z) -> bool:
        return 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz

    def get(self, x, y, z) -> BlockState:
        return self.blocks[self._i(x, y, z)]

    def set(self, x, y, z, state: BlockState, nbt: dict | None = None):
        self.blocks[self._i(x, y, z)] = state
        if nbt is None:
            self.block_entities.pop((x, y, z), None)
        else:
            self.block_entities[(x, y, z)] = nbt

    def positions(self):
        """Every cell in y, z, x order."""
        for y in range(self.sy):
            for z in range(self.sz):
                for x in range(self.sx):
                    yield x, y, z

    def palette_counts(self) -> dict:
        out = {}
        for s in self.blocks:
            out[s] = out.get(s, 0) + 1
        return out

    def non_air_count(self) -> int:
        return sum(1 for s in self.blocks if not s.is_empty)

    def copy(self) -> "Grid":
        return copy.deepcopy(self)

    def canonical(self) -> tuple:
        """A comparable form: non-air cells, block entities, entity types and positions."""
        cells = tuple((p, str(self.get(*p))) for p in self.positions() if self.get(*p).name != AIR)
        bes = tuple(sorted((p, _canon(v)) for p, v in self.block_entities.items()))
        ents = tuple(sorted((tuple(round(c, 4) for c in e.pos), e.type) for e in self.entities))
        return self.size, cells, bes, ents


def _canon(v):
    if isinstance(v, dict):
        return tuple(sorted((k, _canon(x)) for k, x in v.items()))
    if isinstance(v, list):
        return tuple(_canon(x) for x in v)
    return v

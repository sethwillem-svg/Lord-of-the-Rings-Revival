"""Where everything lives, relative to a repo root (so ``verify`` can build into a temporary copy)."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

TOOL_REL = Path("tools/structure-import")
DATA_REL = Path("addon/src/main/resources/data/lotr_revival")


@dataclass(frozen=True)
class Repo:
    root: Path

    @staticmethod
    def default() -> "Repo":
        return Repo(Path(__file__).resolve().parents[3])

    @property
    def tool(self) -> Path:
        return self.root / TOOL_REL

    @property
    def builds(self) -> Path:
        return self.tool / "builds"

    @property
    def work(self) -> Path:
        return self.tool / "work"

    @property
    def palettes(self) -> Path:
        return self.tool / "palettes"

    @property
    def data(self) -> Path:
        return self.root / DATA_REL

    @property
    def pools(self) -> Path:
        return self.data / "middle-earth" / "structure_manager_data"

    @property
    def notice(self) -> Path:
        return self.root / "NOTICE"

    @property
    def docs(self) -> Path:
        return self.root / "docs" / "licences" / "structures.md"

    def rel(self, p: Path) -> str:
        return Path(p).resolve().relative_to(self.root.resolve()).as_posix()

    def block_dump(self) -> tuple[Path, bool]:
        """(path, is_real_dump). Falls back to the hand-written test fixture when the dump has not been generated."""
        real = self.tool / "blocks.json"
        if real.exists():
            return real, True
        return self.tool / "tests" / "fixtures" / "blocks.json", False

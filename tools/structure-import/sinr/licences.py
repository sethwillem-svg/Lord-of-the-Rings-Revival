"""Which licences are accepted, and what our adapted templates are licensed under (spec "Which licences are accepted")."""
from __future__ import annotations

OWN = "LicenseRef-ASSETS_LICENSE"

# source SPDX -> output SPDX
ACCEPTED = {
    "CC0-1.0": "CC0-1.0",
    "CC-BY-4.0": "CC-BY-4.0",
    "CC-BY-3.0": "CC-BY-4.0",
    "CC-BY-SA-4.0": "CC-BY-SA-4.0",
    "CC-BY-SA-3.0": "CC-BY-SA-4.0",
}

URLS = {
    "CC0-1.0": "https://creativecommons.org/publicdomain/zero/1.0/",
    "CC-BY-4.0": "https://creativecommons.org/licenses/by/4.0/",
    "CC-BY-3.0": "https://creativecommons.org/licenses/by/3.0/",
    "CC-BY-SA-4.0": "https://creativecommons.org/licenses/by-sa/4.0/",
    "CC-BY-SA-3.0": "https://creativecommons.org/licenses/by-sa/3.0/",
    OWN: "ASSETS_LICENSE",
}

NAMES = {
    "CC0-1.0": "CC0 1.0",
    "CC-BY-4.0": "CC BY 4.0",
    "CC-BY-3.0": "CC BY 3.0",
    "CC-BY-SA-4.0": "CC BY-SA 4.0",
    "CC-BY-SA-3.0": "CC BY-SA 3.0",
    OWN: "ASSETS_LICENSE (CC BY-NC-SA 4.0)",
}


def refusal_reason(spdx: str) -> str | None:
    """None when accepted; otherwise why not."""
    if spdx in ACCEPTED:
        return None
    s = (spdx or "").upper()
    if "-ND" in s:
        return f"{spdx}: NoDerivatives forbids sharing our adaptation"
    if "-NC" in s:
        return f"{spdx}: NonCommercial builds are excluded (spec, Open question 1)"
    if s.startswith("CC") and any(v in s for v in ("-1.0", "-2.0", "-2.5")):
        return f"{spdx}: CC 2.x and older are not accepted"
    if not s or s in ("NONE", "NOASSERTION"):
        return "no licence: all rights reserved by default"
    return f"{spdx}: not in the accepted list"


def output_licence(spdx: str) -> str:
    return OWN if spdx == OWN else ACCEPTED[spdx]


def name(spdx: str) -> str:
    return NAMES.get(spdx, spdx)


def url(spdx: str) -> str:
    return URLS.get(spdx, "")

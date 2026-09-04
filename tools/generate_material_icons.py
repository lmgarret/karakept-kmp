#!/usr/bin/env python3
"""Regenerate com.karakept.app.ui.icons.AppIcons from the Material icons sources.

The upstream `material-icons-extended` artifact is frozen at 1.7.3 and no longer
maintained, so the icons the app actually uses are vendored instead. This script reads
tools/material-icons.txt, downloads the matching 24dp SVGs from google/material-design-icons
(the same artwork the Compose artifact was generated from) and writes the generated
Kotlin file.

    python3 tools/generate_material_icons.py

Requires network access; run it manually when the manifest changes, not from the build.
"""

from __future__ import annotations

import json
import math
import sys
import urllib.request
import xml.etree.ElementTree as ET
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
MANIFEST = REPO_ROOT / "tools" / "material-icons.txt"
OUTPUT = (
    REPO_ROOT
    / "composeApp/src/commonMain/kotlin/com/karakept/app/ui/icons/AppIcons.kt"
)

RAW_BASE = "https://raw.githubusercontent.com/google/material-design-icons/master"
VERSIONS_URL = f"{RAW_BASE}/update/current_versions.json"
SVG_NS = "{http://www.w3.org/2000/svg}"

VARIANTS = {
    "filled": ("materialicons", "Filled", False),
    "outlined": ("materialiconsoutlined", "Outlined", False),
    "automirrored-filled": ("materialicons", "AutoMirrored.Filled", True),
    "automirrored-outlined": ("materialiconsoutlined", "AutoMirrored.Outlined", True),
}


def fetch(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


def read_manifest() -> list[tuple[str, str]]:
    entries = []
    for line in MANIFEST.read_text().splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        variant, name = line.split()
        if variant not in VARIANTS:
            sys.exit(f"{MANIFEST}: unknown variant '{variant}'")
        entries.append((variant, name))
    return entries


def pascal(snake: str) -> str:
    return "".join(part[:1].upper() + part[1:] for part in snake.split("_"))


def snake_names_by_pascal() -> dict[str, list[tuple[str, str]]]:
    """Maps the Compose icon name back onto the (category, snake_case) source paths."""
    versions = json.loads(fetch(VERSIONS_URL))
    by_pascal: dict[str, list[tuple[str, str]]] = defaultdict(list)
    for key in versions:
        category, snake = key.split("::")
        by_pascal[pascal(snake)].append((category, snake))
    return by_pascal


def num(value: str | None, default: float = 0.0) -> float:
    return float(value) if value is not None else default


def circle_to_path(element: ET.Element) -> str:
    cx, cy, r = num(element.get("cx")), num(element.get("cy")), num(element.get("r"))
    return f"M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0z"


def rect_to_path(element: ET.Element) -> str:
    x, y = num(element.get("x")), num(element.get("y"))
    w, h = num(element.get("width")), num(element.get("height"))
    corners = [(x, y), (x + w, y), (x + w, y + h), (x, y + h)]
    transform = element.get("transform")
    if transform:
        if not transform.startswith("matrix("):
            raise ValueError(f"unsupported rect transform: {transform}")
        a, b, c, d, e, f = (
            float(v) for v in transform[len("matrix(") : -1].replace(",", " ").split()
        )
        corners = [(a * px + c * py + e, b * px + d * py + f) for px, py in corners]
    points = " ".join(f"{round(px, 4)},{round(py, 4)}" for px, py in corners)
    return f"M{points.split(' ', 1)[0]}L{points.split(' ', 1)[1].replace(' ', 'L')}z"


def polygon_to_path(element: ET.Element) -> str:
    raw = element.get("points", "").replace(",", " ").split()
    pairs = [f"{raw[i]},{raw[i + 1]}" for i in range(0, len(raw) - 1, 2)]
    return f"M{pairs[0]}L{'L'.join(pairs[1:])}z"


def extract_paths(svg: bytes, source: str) -> list[str]:
    root = ET.fromstring(svg)
    if root.get("viewBox") != "0 0 24 24":
        raise ValueError(f"{source}: unexpected viewBox {root.get('viewBox')}")
    paths = []
    for element in root.iter():
        tag = element.tag.replace(SVG_NS, "")
        if tag in ("svg", "g", "title", "defs"):
            continue
        if element.get("fill") == "none":  # the transparent 24x24 bounding box
            continue
        if element.get("fill-rule") == "evenodd":
            raise ValueError(f"{source}: evenodd fill is not supported by the generator")
        if tag == "path":
            paths.append(" ".join(element.get("d", "").split()))
        elif tag == "circle":
            paths.append(circle_to_path(element))
        elif tag == "rect":
            paths.append(rect_to_path(element))
        elif tag == "polygon":
            paths.append(polygon_to_path(element))
        else:
            raise ValueError(f"{source}: unsupported element <{tag}>")
    if not paths:
        raise ValueError(f"{source}: no drawable geometry")
    return paths


def download(entry: tuple[str, str], by_pascal: dict[str, list[tuple[str, str]]]):
    variant, name = entry
    theme = VARIANTS[variant][0]
    candidates = by_pascal.get(name)
    if not candidates:
        raise SystemExit(f"unknown icon '{name}' — no Material icon maps to that name")
    last_error: Exception | None = None
    for category, snake in candidates:
        url = f"{RAW_BASE}/src/{category}/{snake}/{theme}/24px.svg"
        try:
            return entry, extract_paths(fetch(url), url)
        except Exception as error:  # try the next category the name appears in
            last_error = error
    raise SystemExit(f"could not resolve {variant} {name}: {last_error}")


def kotlin_literal(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def render(icons: dict[tuple[str, str], list[str]]) -> str:
    lines = [
        "package com.karakept.app.ui.icons",
        "",
        "import androidx.compose.ui.graphics.vector.ImageVector",
        "",
        "// Generated by tools/generate_material_icons.py from tools/material-icons.txt.",
        "// Do not edit by hand: add the icon to the manifest and re-run the generator.",
        "",
        "/**",
        " * The Material icons the app draws, vendored as [ImageVector]s.",
        " *",
        " * Mirrors the shape of `androidx.compose.material.icons.Icons` so call sites read the same,",
        " * minus the `material-icons-extended` artifact upstream froze at 1.7.3.",
        " */",
        "object AppIcons {",
    ]

    def emit(indent: str, variant: str, names: list[str]) -> None:
        auto = VARIANTS[variant][2]
        for name in names:
            paths = ", ".join(kotlin_literal(p) for p in icons[(variant, name)])
            suffix = ", autoMirrored = true" if auto else ""
            lines.append(
                f"{indent}val {name}: ImageVector by materialIcon("
                f"{kotlin_literal(name)}, {paths}{suffix})"
            )

    grouped = defaultdict(list)
    for variant, name in icons:
        grouped[variant].append(name)
    for variant in grouped:
        grouped[variant].sort()

    lines.append("    object Filled {")
    emit("        ", "filled", grouped["filled"])
    lines += [
        "    }",
        "",
        "    object Outlined {",
    ]
    emit("        ", "outlined", grouped["outlined"])
    lines += [
        "    }",
        "",
        "    object AutoMirrored {",
        "        object Filled {",
    ]
    emit("            ", "automirrored-filled", grouped["automirrored-filled"])
    lines += [
        "        }",
        "",
        "        object Outlined {",
    ]
    emit("            ", "automirrored-outlined", grouped["automirrored-outlined"])
    lines += [
        "        }",
        "    }",
        "",
        "    /** Alias for [Filled], matching the upstream `Icons.Default` spelling. */",
        "    val Default: Filled get() = Filled",
        "}",
        "",
    ]
    return "\n".join(lines)


def main() -> None:
    entries = read_manifest()
    by_pascal = snake_names_by_pascal()
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda e: download(e, by_pascal), entries))
    icons = {entry: paths for entry, paths in results}
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(render(icons))
    print(f"wrote {len(icons)} icons to {OUTPUT.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()

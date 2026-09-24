#!/usr/bin/env python3
"""Ensure every production TODO/FIXME/HACK is classified and debt cannot grow silently."""

from __future__ import annotations

from collections import Counter
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "config" / "engineering-debt.tsv"
MARKER = re.compile(r"\b(TODO|FIXME|HACK)\b")
EXTENSIONS = {".kt", ".java", ".c", ".cpp"}


def load_registry() -> tuple[int, list[tuple[str, str, str, str, str]]]:
    maximum = -1
    rules: list[tuple[str, str, str, str, str]] = []
    for raw in REGISTRY.read_text(encoding="utf-8").splitlines():
        if raw.startswith("# maximum_markers="):
            maximum = int(raw.partition("=")[2])
        if not raw or raw.startswith("#"):
            continue
        fields = raw.split("\t")
        if len(fields) != 5:
            raise ValueError(f"Invalid debt registry row: {raw}")
        rules.append(tuple(fields))
    if maximum < 0 or not rules:
        raise ValueError("Debt registry requires a maximum and classification rules")
    broad_p1 = [rule[0] for rule in rules if rule[2] == "P1" and Path(rule[0]).suffix not in EXTENSIONS]
    if broad_p1:
        raise ValueError(f"P1 debt must identify exact source files, not broad prefixes: {broad_p1}")
    return maximum, sorted(rules, key=lambda rule: len(rule[0]), reverse=True)


def main() -> int:
    maximum, rules = load_registry()
    markers: list[tuple[str, int, str]] = []
    source_roots = [ROOT / "app/src/main", ROOT / "core-files-api/src/main"]
    for source_root in source_roots:
        for path in source_root.rglob("*"):
            if path.suffix not in EXTENSIONS or not path.is_file():
                continue
            relative = path.relative_to(ROOT).as_posix()
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if MARKER.search(line):
                    markers.append((relative, number, line.strip()))

    unclassified = []
    categories: Counter[tuple[str, str, str]] = Counter()
    for path, number, text in markers:
        rule = next((candidate for candidate in rules if path.startswith(candidate[0])), None)
        if rule is None:
            unclassified.append(f"{path}:{number}: {text}")
        else:
            _, category, priority, owner, _ = rule
            categories[(priority, category, owner)] += 1

    if unclassified:
        print("Unclassified production debt:\n" + "\n".join(unclassified), file=sys.stderr)
        return 1
    if len(markers) > maximum:
        print(f"Production debt grew from the approved maximum {maximum} to {len(markers)} markers", file=sys.stderr)
        return 1
    print(f"Classified {len(markers)}/{maximum} production debt markers:")
    for (priority, category, owner), count in sorted(categories.items()):
        print(f"  {priority} {category} owner={owner}: {count}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
"""Audit Android string-resource coverage and formatting safety."""

from __future__ import annotations

import argparse
import collections
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

RESOURCE_TAGS = {"string", "string-array", "plurals"}
LOCALE_DIRECTORY_RE = re.compile(
    r"^values-(?:[a-z]{2,3}(?:-r(?:[A-Z]{2}|[0-9]{3}))?|"
    r"b\+[A-Za-z0-9]{2,8}(?:\+[A-Za-z0-9]{2,8})+)$"
)
PLACEHOLDER_RE = re.compile(
    r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]"
)
MALFORMED_POSITIONAL_PLACEHOLDER_RE = re.compile(r"%\s+\d")


@dataclass(frozen=True)
class Resource:
    kind: str
    name: str
    values: tuple[tuple[str, str], ...]

    @property
    def key(self) -> tuple[str, str]:
        return self.kind, self.name


def element_text(element: ET.Element) -> str:
    return "".join(element.itertext())


def placeholders(text: str) -> collections.Counter[str]:
    return collections.Counter(PLACEHOLDER_RE.findall(text))


def parse_file(path: Path) -> list[Resource]:
    root = ET.parse(path).getroot()
    resources: list[Resource] = []
    for element in root:
        if element.tag not in RESOURCE_TAGS:
            continue
        if element.attrib.get("translatable") == "false":
            continue
        name = element.attrib.get("name")
        if not name:
            continue
        if element.tag == "string":
            values = (("value", element_text(element)),)
        elif element.tag == "string-array":
            values = tuple(
                (str(index), element_text(item))
                for index, item in enumerate(element.findall("item"))
            )
        else:
            values = tuple(
                (item.attrib.get("quantity", ""), element_text(item))
                for item in element.findall("item")
            )
        resources.append(Resource(element.tag, name, values))
    return resources


def load_directory(directory: Path) -> tuple[dict[tuple[str, str], Resource], list[str]]:
    resources: dict[tuple[str, str], Resource] = {}
    errors: list[str] = []
    for path in sorted(directory.glob("*.xml")):
        if directory.name == "values" and path.name == "donottranslate_strings.xml":
            continue
        try:
            parsed = parse_file(path)
        except ET.ParseError as error:
            errors.append(f"{path}: malformed XML: {error}")
            continue
        for resource in parsed:
            previous = resources.get(resource.key)
            if previous is not None:
                errors.append(
                    f"{directory.name}: duplicate {resource.kind} resource "
                    f"{resource.name!r} (including {path.name})"
                )
            else:
                resources[resource.key] = resource
    return resources, errors


def validate_resource(base: Resource, localized: Resource, locale: str) -> list[str]:
    errors: list[str] = []
    for label, translation in localized.values:
        if MALFORMED_POSITIONAL_PLACEHOLDER_RE.search(translation):
            errors.append(
                f"{locale}: malformed positional placeholder for {base.name}[{label}]"
            )
    if base.kind == "string":
        expected = placeholders(base.values[0][1])
        actual = placeholders(localized.values[0][1])
        if expected != actual:
            errors.append(
                f"{locale}: placeholder mismatch for {base.name}: "
                f"expected {dict(expected)}, found {dict(actual)}"
            )
        return errors

    if base.kind == "string-array":
        if len(base.values) != len(localized.values):
            errors.append(
                f"{locale}: array length mismatch for {base.name}: "
                f"expected {len(base.values)}, found {len(localized.values)}"
            )
            return errors
        for (index, source), (_, translation) in zip(base.values, localized.values):
            expected = placeholders(source)
            actual = placeholders(translation)
            if expected != actual:
                errors.append(
                    f"{locale}: placeholder mismatch for {base.name}[{index}]: "
                    f"expected {dict(expected)}, found {dict(actual)}"
                )
        return errors

    base_quantities = dict(base.values)
    fallback = base_quantities.get("other", "")
    localized_quantities = {quantity for quantity, _ in localized.values}
    if "other" not in localized_quantities:
        errors.append(f"{locale}: plural {base.name} is missing quantity='other'")
    for quantity, translation in localized.values:
        source = base_quantities.get(quantity, fallback)
        expected = placeholders(source)
        actual = placeholders(translation)
        if expected != actual:
            errors.append(
                f"{locale}: placeholder mismatch for {base.name}[{quantity}]: "
                f"expected {dict(expected)}, found {dict(actual)}"
            )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--res-dir",
        type=Path,
        default=Path("app/src/main/res"),
        help="Android resource directory",
    )
    parser.add_argument(
        "--require-complete",
        action="store_true",
        help="fail when any advertised locale is missing a translatable resource",
    )
    parser.add_argument(
        "--locale",
        action="append",
        default=[],
        help="limit the audit to a values-* locale directory name; may be repeated",
    )
    args = parser.parse_args()

    base_dir = args.res_dir / "values"
    base, errors = load_directory(base_dir)
    if not base:
        print(f"No translatable resources found in {base_dir}", file=sys.stderr)
        return 2

    locale_dirs = sorted(
        directory
        for directory in args.res_dir.glob("values-*")
        if directory.is_dir()
        and LOCALE_DIRECTORY_RE.fullmatch(directory.name)
        and (not args.locale or directory.name in args.locale)
    )
    if args.locale:
        found = {directory.name for directory in locale_dirs}
        for requested in sorted(set(args.locale) - found):
            errors.append(f"Locale directory not found: {requested}")

    print(f"Base catalog: {len(base)} translatable resources")
    incomplete = False
    for locale_dir in locale_dirs:
        localized, locale_errors = load_directory(locale_dir)
        errors.extend(locale_errors)
        matching = base.keys() & localized.keys()
        missing = sorted(base.keys() - localized.keys())
        extras = sorted(localized.keys() - base.keys())
        coverage = 100.0 * len(matching) / len(base)
        print(
            f"{locale_dir.name}: {len(matching)}/{len(base)} "
            f"({coverage:.1f}%), missing {len(missing)}, extra {len(extras)}"
        )
        for key in matching:
            errors.extend(validate_resource(base[key], localized[key], locale_dir.name))
        if missing:
            incomplete = True
            if args.require_complete:
                preview = ", ".join(name for _, name in missing[:20])
                suffix = "" if len(missing) <= 20 else f", … and {len(missing) - 20} more"
                errors.append(
                    f"{locale_dir.name}: missing {len(missing)} resources: "
                    f"{preview}{suffix}"
                )

    if errors:
        print("\nTranslation validation errors:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    if incomplete and not args.require_complete:
        print("\nCoverage is incomplete; rerun with --require-complete before release.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

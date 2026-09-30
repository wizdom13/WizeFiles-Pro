#!/usr/bin/env python3
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
"""Generate the offline app changelog from changelogs.md; no third-party packages."""
import argparse
import html
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'changelogs.md'
TARGET = ROOT / 'app/src/main/assets/changelogs.html'
HEADING = re.compile(r'## ([0-9]+(?:\.[0-9]+)+) — (\d{4}-\d{2}-\d{2})')
ORDER = re.compile(r'<!-- release-order: (\d+) -->')
INLINE = re.compile(r'\*\*(.+?)\*\*|`([^`]+)`')


def inline(text):
    result, start = [], 0
    for match in INLINE.finditer(text):
        result.append(html.escape(text[start:match.start()]))
        tag, value = ('strong', match[1]) if match[1] else ('code', match[2])
        result.append(f'<{tag}>{html.escape(value)}</{tag}>')
        start = match.end()
    return ''.join(result) + html.escape(text[start:])


def blocks(lines):
    output, paragraph, items = [], [], []

    def flush():
        if paragraph:
            output.append('<p>' + inline(' '.join(paragraph)) + '</p>')
            paragraph.clear()
        if items:
            output.append('<ul>\n' + '\n'.join('<li>' + inline(x) + '</li>' for x in items) + '\n</ul>')
            items.clear()

    for raw in lines:
        line = raw.strip()
        if not line:
            flush()
        elif line.startswith('### '):
            flush()
            output.append('<h3>' + inline(line[4:]) + '</h3>')
        elif line.startswith('- '):
            if paragraph:
                flush()
            items.append(line[2:])
        else:
            if items:
                flush()
            paragraph.append(line)
    flush()
    return '\n'.join(output)


def generate(markdown, current_version):
    sections, introduction, current = [], [], None
    for line in markdown.splitlines():
        heading = HEADING.fullmatch(line)
        if heading or line == '## Unreleased':
            current = {'version': heading[1] if heading else None, 'date': heading[2] if heading else None,
                       'order': None, 'lines': []}
            sections.append(current)
        elif line.startswith('## '):
            raise ValueError('Unrecognized release heading: ' + line)
        elif current is None:
            if not line.startswith('# '):
                introduction.append(line)
        elif order := ORDER.fullmatch(line):
            if current['order'] is not None or current['version'] is None:
                raise ValueError('Duplicate or unreleased ordering marker')
            current['order'] = int(order[1])
        else:
            current['lines'].append(line)
    releases = [s for s in sections if s['version']]
    orders = [s['order'] for s in releases]
    versions = [s['version'] for s in releases]
    if (not orders or any(x is None or x <= 0 for x in orders)
            or orders != sorted(set(orders), reverse=True) or len(versions) != len(set(versions))):
        raise ValueError('Release versions and chronological ordering must be unique and newest first')
    if current_version not in versions:
        raise ValueError(f'Add release notes for app version {current_version} to changelogs.md')
    navigation, content = [], []
    for section in sections:
        body = blocks(section['lines'])
        if '<li>' not in body:
            raise ValueError('Each release needs at least one change')
        version = section['version']
        anchor = 'v' + version.replace('.', '-') if version else 'unreleased'
        title = f'WizeFiles {version}' if version else 'Unreleased'
        attributes = f' data-version="{version}" data-release-order="{section["order"]}"' if version else ''
        date = f'<p class="date">{section["date"]}</p>' if version else ''
        navigation.append(f'<a href="#{anchor}">{html.escape(version or title)}</a>')
        content.append(f'<section id="{anchor}"{attributes}>\n<h2>{title}</h2>\n{date}\n{body}\n</section>')
    return '''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
<title>WizeFiles Changelog</title>
<style>
:root { color-scheme:light dark; --background:#faf7fc; --card:#f2edf7; --text:#211d27; --muted:#625a6b; --primary:#6750a4; --outline:#d0c7d9; }
@media(prefers-color-scheme:dark) { :root { --background:#151218; --card:#211d27; --text:#eee6f2; --muted:#cbc1d4; --primary:#d0bcff; --outline:#4b4355; } }
* { box-sizing:border-box; } body { margin:0 auto; padding:20px 16px 36px; max-width:880px; background:var(--background); color:var(--text); font:16px/1.65 system-ui,sans-serif; overflow-wrap:anywhere; }
h1 { font-size:1.8rem; line-height:1.25; margin:8px 0 18px; } h2 { font-size:1.4rem; margin:0; } h3 { font-size:1.05rem; margin:22px 0 8px; }
p { margin:10px 0; } .date { color:var(--muted); margin-top:2px; } section { background:var(--card); padding:22px; margin:18px 0; border:1px solid var(--outline); border-radius:20px; scroll-margin-top:12px; }
ul { padding-left:1.4em; } li { margin:8px 0; } a { color:var(--primary); text-underline-offset:3px; } nav { display:flex; flex-wrap:wrap; gap:10px; margin:24px 0; } nav a { display:inline-block; padding:7px 12px; border:1px solid var(--outline); border-radius:24px; text-decoration:none; }
a:focus-visible { outline:2px solid var(--primary); outline-offset:3px; } code { font-family:monospace; } @media(max-width:400px) { section { padding:16px; } }
</style>
</head>
<body>
<h1>WizeFiles Changelog</h1>
''' + blocks(introduction) + '\n<nav aria-label="Release versions">\n' + '\n'.join(navigation) + '\n</nav>\n<main>\n' + '\n'.join(content) + '\n</main>\n</body>\n</html>\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Fail if the committed HTML is stale')
    args = parser.parse_args()
    config = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
    version = re.search(r"^def appVersionName = '([^']+)'", config, re.M)[1]
    rendered = generate(SOURCE.read_text(encoding='utf-8'), version)
    if args.check:
        if not TARGET.exists() or TARGET.read_text(encoding='utf-8') != rendered:
            sys.exit('Bundled changelog is stale. Run python3 scripts/generate-changelogs.py')
        print('Offline changelog matches changelogs.md and contains the current app version.')
    else:
        TARGET.parent.mkdir(parents=True, exist_ok=True)
        TARGET.write_text(rendered, encoding='utf-8')
        print(f'Generated {TARGET.relative_to(ROOT)}')


if __name__ == '__main__':
    main()

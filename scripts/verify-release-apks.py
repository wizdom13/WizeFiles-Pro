#!/usr/bin/env python3
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
"""Validate the three release APKs and report their actual sizes and SHA-256 hashes."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
ABIS = ('arm64-v8a', 'armeabi-v7a')
ELF_TYPES = {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40)}


def check(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(stream):
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1024 * 1024), b''):
        digest.update(chunk)
    return digest.hexdigest()


def inspect_payload(apk, expected_abis):
    libraries, dex = {}, {}
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        check(len(names) == len(set(names)), f'{apk.name}: duplicate ZIP entries')
        for name in names:
            if name.startswith('lib/') and not name.endswith('/'):
                parts = name.split('/')
                check(len(parts) == 3 and parts[1] in expected_abis and name.endswith('.so'),
                      f'{apk.name}: unexpected native entry {name}')
                abi = parts[1]
                with archive.open(name) as stream:
                    header = stream.read(20)
                check(len(header) == 20 and header[:4] == b'\x7fELF' and header[5] == 1,
                      f'{apk.name}: invalid little-endian ELF: {name}')
                check((header[4], int.from_bytes(header[18:20], 'little')) == ELF_TYPES[abi],
                      f'{apk.name}: ELF architecture does not match {abi}: {name}')
                with archive.open(name) as stream:
                    libraries[name] = sha256(stream)
            elif re.fullmatch(r'classes\d*\.dex', name):
                with archive.open(name) as stream:
                    dex[name] = sha256(stream)
        check(dex, f'{apk.name}: no DEX files')
        for abi in expected_abis:
            for library in ('libsyncthing.so', 'libsyncthing-launcher.so', 'libgojni.so'):
                check(f'lib/{abi}/{library}' in libraries,
                      f'{apk.name}: missing {abi}/{library}')
    return libraries, dex


def verify(directory, file_tag, signed):
    gradle = (ROOT / 'app/build.gradle').read_text()
    version = re.search(r"^def appVersionName = '([^']+)'$", gradle, re.M)[1]
    version_code = re.search(r'^def appVersionCode = (\d+)$', gradle, re.M)[1]
    file_tag = file_tag or f'v{version}'
    check(re.fullmatch(r'v[0-9][0-9A-Za-z._-]*', file_tag), 'Invalid release label')
    build_tools = re.search(r"buildToolsVersion = '([^']+)'", gradle)[1]
    sdk = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    check(sdk, 'ANDROID_SDK_ROOT or ANDROID_HOME is required')
    tools = Path(sdk) / 'build-tools' / build_tools
    expected = {f'WizeFiles_{file_tag}.apk': ABIS}
    expected.update({f'WizeFiles_{file_tag}_{abi}.apk': (abi,) for abi in ABIS})
    check({p.name for p in directory.glob('*.apk')} == set(expected),
          'Expected exactly the universal, arm64-v8a and armeabi-v7a APKs')
    records, universal_libraries, universal_dex, certificate = [], None, None, None
    for filename, abis in expected.items():
        apk = directory / filename
        badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
        package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",
                            badging, re.M)
        check(package and package.groups() == ('com.wisso.wizefiles', version_code, version),
              f'{filename}: wrong package or version')
        libraries, dex = inspect_payload(apk, abis)
        if universal_libraries is None:
            universal_libraries, universal_dex = libraries, dex
        else:
            subset = {name: digest for name, digest in universal_libraries.items()
                      if name.split('/')[1] in abis}
            check(libraries == subset, f'{filename}: native payload differs from universal APK')
            check(dex == universal_dex, f'{filename}: app code differs from universal APK')
            check(apk.stat().st_size < records[0]['bytes'], f'{filename}: no size reduction')
        subprocess.run([str(tools / 'zipalign'), '-c', '-P', '16', '4', str(apk)], check=True)
        if signed:
            verification = subprocess.check_output(
                [str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
            certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$',
                                      verification, re.M)
            check(len(certificates) == 1, f'{filename}: expected one signing certificate')
            check(certificate in (None, certificates[0]), f'{filename}: signing certificates differ')
            certificate = certificates[0]
        with apk.open('rb') as stream:
            checksum = sha256(stream)
        records.append({'file': filename, 'abis': list(abis), 'bytes': apk.stat().st_size,
                        'sha256': checksum, 'native_libraries': len(libraries)})
    report = {'version': version, 'version_code': int(version_code), 'signed': signed,
              'certificate_sha256': certificate, 'apks': records}
    (directory / 'apk-sizes.json').write_text(json.dumps(report, indent=2) + '\n')
    (directory / 'SHA256SUMS').write_text(''.join(f"{r['sha256']}  {r['file']}\n" for r in records))
    lines = [f"### Release APKs ({'signed' if signed else 'unsigned'})", '',
             '| APK | Architectures | Bytes | MiB |', '| --- | --- | ---: | ---: |']
    lines.extend(f"| {r['file']} | {', '.join(r['abis'])} | {r['bytes']:,} | {r['bytes'] / 1048576:.2f} |"
                 for r in records)
    if certificate:
        lines.extend(['', f'Signing certificate SHA-256: `{certificate}`'])
    markdown = '\n'.join(lines) + '\n'
    (directory / 'apk-sizes.md').write_text(markdown)
    print(markdown)
    if summary := os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(summary, 'a') as stream:
            stream.write(markdown + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--file-tag')
    parser.add_argument('--signed', action='store_true')
    args = parser.parse_args()
    try:
        verify(args.directory, args.file_tag, args.signed)
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f'Release APK validation failed: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())

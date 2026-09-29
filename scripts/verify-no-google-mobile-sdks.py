#!/usr/bin/env python3
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
"""Reject GMS/ML Kit namespaces in actual APK DEX files, without filtering FOSS com.google libraries."""
import argparse
import hashlib
import re
import struct
import zipfile
from pathlib import Path

PREFIXES = (b'Lcom/google/android/gms/', b'Lcom/google/mlkit/', b'Lcom/google/android/odml/')

def dex_classes(data):
    if len(data) < 112 or not data.startswith(b'dex\n'):
        raise ValueError('Malformed DEX header')
    strings_size, strings_offset = struct.unpack_from('<II', data, 56)
    types_size, types_offset = struct.unpack_from('<II', data, 64)
    classes_size, classes_offset = struct.unpack_from('<II', data, 96)
    for size, offset, width in ((strings_size, strings_offset, 4), (types_size, types_offset, 4), (classes_size, classes_offset, 32)):
        if offset + size * width > len(data):
            raise ValueError('Malformed DEX table')
    for index in range(classes_size):
        type_index = struct.unpack_from('<I', data, classes_offset + index * 32)[0]
        if type_index >= types_size:
            raise ValueError('Invalid DEX type')
        string_index = struct.unpack_from('<I', data, types_offset + type_index * 4)[0]
        if string_index >= strings_size:
            raise ValueError('Invalid DEX string')
        offset = struct.unpack_from('<I', data, strings_offset + string_index * 4)[0]
        for _ in range(5):
            byte = data[offset]
            offset += 1
            if byte < 128:
                break
        else:
            raise ValueError('Invalid DEX string length')
        end = data.index(b'\x00', offset)
        yield data[offset:end]

def verify(path):
    offending = set()
    classes = 0
    dex_count = 0
    acra = False
    with zipfile.ZipFile(path) as apk:
        for name in apk.namelist():
            if re.fullmatch(r'classes(?:\d+)?\.dex', name):
                dex_count += 1
                for descriptor in dex_classes(apk.read(name)):
                    classes += 1
                    acra |= descriptor.startswith(b'Lorg/acra/')
                    if descriptor.startswith(PREFIXES):
                        offending.add(descriptor.decode('ascii', errors='replace'))
    if dex_count == 0 or classes == 0:
        raise SystemExit(f'{path}: APK contains no readable DEX classes')
    if offending:
        raise SystemExit(f'{path}: forbidden Google mobile SDK classes:\n' + '\n'.join(sorted(offending)))
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    print(f'{path}: PASS; {classes} classes in {dex_count} DEX files; SHA-256 {digest}')
    print(f'ACRA namespace present: {acra}. Local/manual crash reporting is retained intentionally.')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apks', type=Path, nargs='+')
    for apk in parser.parse_args().apks:
        verify(apk)

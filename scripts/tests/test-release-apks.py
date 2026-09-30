#!/usr/bin/env python3
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
"""Regression checks for malformed native payloads at the release publication gate."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import warnings
import zipfile

SPEC = importlib.util.spec_from_file_location(
    'release_apks', Path(__file__).resolve().parents[1] / 'verify-release-apks.py')
RELEASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RELEASE)


def elf(abi):
    elf_class, machine = RELEASE.ELF_TYPES[abi]
    header = bytearray(64)
    header[:4] = b'\x7fELF'
    header[4:6] = bytes((elf_class, 1))
    header[18:20] = machine.to_bytes(2, 'little')
    return bytes(header)


class ReleasePayloadTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.apk = Path(temporary.name) / 'test.apk'

    def write_apk(self, abis, missing=None, wrong_machine=False):
        with zipfile.ZipFile(self.apk, 'w') as archive:
            archive.writestr('classes.dex', b'app-code')
            for abi in abis:
                for library in ('libsyncthing.so', 'libsyncthing-launcher.so', 'libgojni.so'):
                    if library != missing:
                        archive.writestr(f'lib/{abi}/{library}',
                                         elf('armeabi-v7a' if wrong_machine else abi))

    def test_complete_universal_and_single_abi_payloads(self):
        for abis in (RELEASE.ABIS, ('arm64-v8a',), ('armeabi-v7a',)):
            with self.subTest(abis=abis):
                self.write_apk(abis)
                libraries, dex = RELEASE.inspect_payload(self.apk, abis)
                self.assertEqual(len(libraries), 3 * len(abis))
                self.assertEqual(set(dex), {'classes.dex'})

    def test_rejects_other_architecture_in_single_abi_apk(self):
        self.write_apk(RELEASE.ABIS)
        with self.assertRaisesRegex(ValueError, 'unexpected native entry'):
            RELEASE.inspect_payload(self.apk, ('arm64-v8a',))

    def test_rejects_missing_native_engines(self):
        for library in ('libsyncthing.so', 'libsyncthing-launcher.so', 'libgojni.so'):
            with self.subTest(library=library):
                self.write_apk(('arm64-v8a',), missing=library)
                with self.assertRaisesRegex(ValueError, 'missing'):
                    RELEASE.inspect_payload(self.apk, ('arm64-v8a',))

    def test_rejects_wrong_elf_architecture_inside_correct_directory(self):
        self.write_apk(('arm64-v8a',), wrong_machine=True)
        with self.assertRaisesRegex(ValueError, 'ELF architecture does not match'):
            RELEASE.inspect_payload(self.apk, ('arm64-v8a',))

    def test_rejects_duplicate_zip_entries(self):
        self.write_apk(('arm64-v8a',))
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.apk, 'a') as archive:
                archive.writestr('classes.dex', b'overwritten-code')
        with self.assertRaisesRegex(ValueError, 'duplicate ZIP entries'):
            RELEASE.inspect_payload(self.apk, ('arm64-v8a',))


if __name__ == '__main__':
    unittest.main()

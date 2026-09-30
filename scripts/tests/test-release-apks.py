#!/usr/bin/env python3
# Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
# SPDX-License-Identifier: GPL-3.0-only
"""Regression checks for malformed native payloads at the release publication gate."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
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


class SignedReleaseCollectionTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        scripts = self.root / 'scripts'
        scripts.mkdir()
        self.collector = scripts / 'collect-signed-release-apks.sh'
        shutil.copyfile(Path(__file__).resolve().parents[1] / self.collector.name, self.collector)
        # Exercise the real shell collector without requiring Android SDK tools.
        # The full APK and signature verifier still runs on real APKs in CI.
        (scripts / 'verify-release-apks.py').write_text(
            'import json, os, sys\nfrom pathlib import Path\n'
            'Path("verification.json").write_text(json.dumps(sys.argv[1:]))\n'
            'sys.exit(int(os.environ.get("TEST_VERIFIER_EXIT", "0")))\n')
        self.input = self.root / 'app/build/outputs/release-apks/unsigned'
        self.input.mkdir(parents=True)
        self.output = self.input.parent / 'signed'
        self.names = ('WizeFiles_v1.2.0', 'WizeFiles_v1.2.0_arm64-v8a',
                      'WizeFiles_v1.2.0_armeabi-v7a')
        self.env = dict(os.environ, FILE_TAG='v1.2.0')
        self.env.pop('SIGNED_RELEASE_FILES', None)
        self.env.pop('SIGNED_RELEASE_FILE', None)
        for name in self.names:
            (self.input / f'{name}-signed.apk').write_bytes(name.encode())

    def collect(self):
        return subprocess.run(['bash', str(self.collector)], env=self.env,
                              text=True, capture_output=True)

    def test_collects_three_apks_without_action_outputs_and_excludes_signing_material(self):
        for name in self.names:
            (self.input / f'{name}.apk').write_bytes(b'unsigned')
            (self.input / f'{name}-aligned.apk').write_bytes(b'aligned')
        (self.input / 'signingKey.jks').write_bytes(b'test key placeholder')
        result = self.collect()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual({p.name for p in self.output.iterdir()}, {f'{n}.apk' for n in self.names})
        for name in self.names:
            self.assertEqual((self.output / f'{name}.apk').read_bytes(), name.encode())
        self.assertEqual(json.loads((self.root / 'verification.json').read_text()),
                         ['app/build/outputs/release-apks/signed', '--file-tag', 'v1.2.0', '--signed'])

    def test_rejects_missing_signed_apk(self):
        (self.input / f'{self.names[1]}-signed.apk').unlink()
        result = self.collect()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Expected exactly three', result.stderr)

    def test_rejects_no_signed_apks(self):
        for apk in self.input.iterdir():
            apk.unlink()
        self.assertNotEqual(self.collect().returncode, 0)

    def test_rejects_unexpected_signed_apk(self):
        (self.input / f'{self.names[1]}-signed.apk').rename(self.input / 'wrong-signed.apk')
        result = self.collect()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Unexpected signed APK', result.stderr)

    def test_rejects_extra_stale_signed_apk(self):
        (self.input / 'WizeFiles_v1.1.0-signed.apk').write_bytes(b'stale')
        self.assertNotEqual(self.collect().returncode, 0)

    def test_propagates_signature_verification_failure(self):
        self.env['TEST_VERIFIER_EXIT'] = '29'
        self.assertEqual(self.collect().returncode, 29)


if __name__ == '__main__':
    unittest.main()

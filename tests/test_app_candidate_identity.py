"""Synthetic metadata only; never accesses a signing key, device or business data."""
import unittest
from unittest import mock
import subprocess
from pathlib import Path
import tempfile
import zipfile
from verify_app_candidate import compare_identity, metadata, candidate_assets, PACKAGE


class CandidateIdentityTests(unittest.TestCase):
    def test_generated_art_profiles_require_exact_compiler_bytes(self):
        with tempfile.TemporaryDirectory(prefix='qa_art_profiles_') as temp:
            root = Path(temp); assets = root/'assets'; assets.mkdir(); (assets/'qa.txt').write_bytes(b'qa public asset')
            profiles = {'baseline.prof': ('binary_art_profile', b'pro\x00010\x00qa_profile'),
                        'baseline.profm': ('binary_art_profile_metadata', b'prm\x00002\x00qa_metadata')}
            for name, (folder, raw) in profiles.items():
                output = root/folder/'release/compileReleaseArtProfile'/name
                output.parent.mkdir(parents=True); output.write_bytes(raw)
            apk = root/'qa.apk'
            def archive(extra=None):
                with zipfile.ZipFile(apk, 'w') as z:
                    z.writestr('assets/qa.txt', b'qa public asset')
                    for name, (_, raw) in profiles.items(): z.writestr('assets/dexopt/'+name, raw)
                    if extra: z.writestr('assets/'+extra, b'qa unexpected')
            archive(); result = candidate_assets(apk, assets, root)
            self.assertTrue(result['matches']); self.assertEqual(len(result['generated_profiles']), 2)
            archive('dexopt/qa_unknown.prof'); self.assertFalse(candidate_assets(apk, assets, root)['matches'])
            archive(); reference = root/'binary_art_profile/release/compileReleaseArtProfile/baseline.prof'
            reference.write_bytes(b'pro\x00010\x00qa_other')
            with self.assertRaises(ValueError): candidate_assets(apk, assets, root)
            reference.unlink()
            with self.assertRaises(ValueError): candidate_assets(apk, assets, root)

    def setUp(self):
        self.previous = dict(package=PACKAGE, version_code=65, signature_valid=True, certificates=['a'*64])
        self.candidate = dict(self.previous, version_code=66)

    def test_same_verified_certificate_and_higher_version(self):
        self.assertEqual(compare_identity(self.previous, self.candidate), 'PASS')

    def test_package_version_and_certificate_mismatches_fail_closed(self):
        for changes in ({'package': PACKAGE+'.qa'}, {'version_code': 65}, {'version_code': 64},
                        {'signature_valid': False}, {'certificates': ['b'*64]},
                        {'certificates': []}, {'certificates': ['a'*64, 'b'*64]}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                compare_identity(self.previous, dict(self.candidate, **changes))

    def test_unverified_or_foreign_original_is_rejected(self):
        for changes in ({'signature_valid': False}, {'package': PACKAGE+'.qa'}, {'certificates': []}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                compare_identity(dict(self.previous, **changes), self.candidate)

    def test_candidate_metadata_recognizes_build_tools_37_certificate(self):
        digest = 'a' * 64
        badging = "package: name='at.hausgarten.service' versionCode='66' versionName='1.46.1'\n"
        signed = 'Verifies\nNumber of signers: 1\nV3.0 Signer: certificate SHA-256 digest: ' + digest + '\n'
        with mock.patch('verify_app_candidate.subprocess.run', side_effect=[
            subprocess.CompletedProcess([], 0, badging, ''),
            subprocess.CompletedProcess([], 0, signed, ''),
        ]) as run:
            result = metadata('synthetic.apk', 'mock-aapt', 'mock-apksigner')
        self.assertTrue(result['signature_valid'])
        self.assertEqual(result['certificates'], [digest])
        self.assertIn('--verbose', run.call_args_list[1].args[0])

    def test_candidate_metadata_rejects_unverified_or_ambiguous_signers(self):
        badging = "package: name='at.hausgarten.service' versionCode='66' versionName='1.46.1'\n"
        certificate = 'V3.0 Signer: certificate SHA-256 digest: ' + 'a' * 64 + '\n'
        for code, signed in ((1, 'Number of signers: 1\n' + certificate),
                             (0, 'Number of signers: 2\n' + certificate),
                             (0, 'Number of signers: 1\n' + certificate * 2)):
            with self.subTest(exit_code=code), mock.patch('verify_app_candidate.subprocess.run', side_effect=[
                subprocess.CompletedProcess([], 0, badging, ''),
                subprocess.CompletedProcess([], code, signed, ''),
            ]):
                self.assertFalse(metadata('synthetic.apk', 'mock-aapt', 'mock-apksigner')['signature_valid'])

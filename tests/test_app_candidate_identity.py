"""Synthetic metadata only; never accesses a signing key, device or business data."""
import unittest
from verify_app_candidate import compare_identity, PACKAGE


class CandidateIdentityTests(unittest.TestCase):
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

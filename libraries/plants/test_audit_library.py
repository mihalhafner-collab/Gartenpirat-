"""Synthetic negative checks: an internally consistent file must still be rejected
after a credit, evidence, identity, approval, or baseline claim is corrupted.
"""
from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


class LibraryAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='qa_plant_audit_')
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.library = root / 'libraries/plants'
        self.library.mkdir(parents=True)
        (self.library / 'evidence').mkdir()
        shutil.copyfile(Path(__file__).with_name('audit_library.py'), self.library / 'audit_library.py')
        shutil.copyfile(Path(__file__).with_name('historical_baseline.py'), self.library / 'historical_baseline.py')
        asset = root / 'android/app/src/main/assets'
        asset.mkdir(parents=True)
        raw = json.dumps({'plants': [{'id': 'qa_legacy_plant'}]}).encode()
        (asset / 'plant-catalog.json').write_bytes(raw)
        # A real immutable synthetic Git object, independent of the mutable fixture.
        env = {k: v for k, v in os.environ.items() if not k.startswith('HG_ORIGINAL_')}
        def git(*args):
            return subprocess.check_output(['git', *args], cwd=root, env=env, stderr=subprocess.DEVNULL).decode().strip()
        git('init', '--quiet')
        git('add', 'android/app/src/main/assets/plant-catalog.json')
        git('-c', 'user.name=QA Test', '-c', 'user.email=qa@example.invalid',
            '-c', 'commit.gpgsign=false', 'commit', '--quiet', '--no-verify', '-m', 'QA baseline')
        self.baseline_commit = git('rev-parse', 'HEAD')
        photos = [{'id': 'qa_photo_' + str(n), 'caption': 'QA plant detail ' + str(n),
                   'author': 'QA Author', 'license': 'CC-BY-4.0',
                   'licenseUrl': 'https://creativecommons.org/licenses/by/4.0/',
                   'sourcePage': 'https://example.invalid/qa-plant',
                   'originalLocator': 'https://example.invalid/qa-photo-' + str(n) + '.jpg',
                   'visualReview': 'NOT_PERFORMED', 'binaryVerification': 'NOT_DOWNLOADED'}
                  for n in range(2)]
        self.taxons = [{'query': 'QA plant ' + str(n), 'result': {
            'matchType': 'EXACT', 'kingdom': 'Plantae', 'confidence': 99,
            'rank': 'SPECIES', 'usageKey': n + 100}} for n in range(2)]

        def entry(n):
            p = {'id': 'qa_new_plant' if n else 'qa_legacy_plant',
                 'name': 'QA Plant ' + str(n), 'scientificName': 'QA plant ' + str(n),
                 'category': 'QA category', 'origin': 'extension' if n else 'legacy_reaudited',
                 'taxonomy': {'query': 'QA plant ' + str(n), 'match': self.taxons[n]['result'],
                              'acceptedUsageKey': n + 100, 'status': 'EXACT_MATCH'},
                 'fieldEvidence': {'light': {'source': 'https://example.invalid/qa-plant',
                                            'field': 'Light', 'values': ['Full sun']}},
                 'safety': {'status': 'UNKNOWN_NOT_PROVEN_HARMLESS', 'poisonSeveritySource': []},
                 'review': {'implementation': 'HOLD', 'sourceFacts': 'CHECKED', 'blockers': ['QA pending']},
                 'images': deepcopy(photos), 'legacyPhotos': []}
            if not n:
                p['legacyId'] = p['id']
            return p
        self.data = {'baseline': {'catalogSha256': hashlib.sha256(raw).hexdigest(), 'repositoryCommit': self.baseline_commit},
                     'plants': [entry(0), entry(1)]}
        evidence = [{'url': 'https://example.invalid/qa-plant', 'facts': {'Light': ['Full sun']},
                     'figures': [{'url': p['originalLocator'], 'author': p['author'],
                                  'caption': p['caption'], 'licenseId': p['license'], 'eligible': True}
                                 for p in photos]}]
        for name, value in [('evidence/profiles.json', evidence),
                            ('evidence/taxonomy.json', self.taxons),
                            ('evidence/commons.json', {'sources': []}),
                            ('evidence/replacements.json', {'sources': []}),
                            ('evidence/visual-screening.json', {'records': []}),
                            ('evidence/official-context.json', {}), ('review-queue.json', {})]:
            (self.library / name).write_text(json.dumps(value))
        for name, value in [('evidence/supplemental-photos.json', {'sources': []}),
                            ('evidence/taxonomic-resolution.json', {'records': []}),
                            ('evidence/legal-review.json', {'records': []}),
                            ('evidence/legal-sources.json', {'sources': [{'rows': []}]}),
                            ('evidence/commons-api.json', {}), ('evidence/eu-restrictions.json', {}),
                            ('evidence/photo-exclusions.json', {'records': []}),
                            ('review-scope.json', {})]:
            (self.library / name).write_text(json.dumps(value))

    def run_audit(self):
        (self.library / 'plant-library.json').write_text(json.dumps(self.data))
        p = subprocess.run([sys.executable, str(self.library / 'audit_library.py'), '--baseline-commit', self.baseline_commit],
                           capture_output=True, text=True)
        self.assertIn(p.returncode, (0, 1), p.stderr)
        return p.returncode, json.loads(p.stdout)

    def assert_rejected(self, code):
        rc, report = self.run_audit()
        self.assertEqual(1, rc)
        self.assertTrue(any(code in error for error in report['errors']), report)

    def test_consistent_synthetic_fixture(self):
        rc, report = self.run_audit()
        self.assertEqual(0, rc)
        self.assertEqual('PASS_WITH_DOCUMENTED_HOLDS', report['status'])

    def test_noncommercial_license_is_rejected(self):
        self.data['plants'][1]['images'][0]['license'] = 'CC-BY-NC-4.0'
        self.assert_rejected('INELIGIBLE_LICENSE')

    def test_false_author_is_rejected(self):
        self.data['plants'][1]['images'][0]['author'] = 'QA Wrong Author'
        self.assert_rejected('UNSUPPORTED_PHOTO_CREDIT')

    def test_signed_url_is_rejected(self):
        self.data['plants'][1]['images'][0]['originalLocator'] += '?qa_temporary=1'
        self.assert_rejected('SIGNED_OR_UNSTABLE_URL')

    def test_unsupported_plant_fact_is_rejected(self):
        self.data['plants'][1]['fieldEvidence']['light']['values'] = ['Full shade']
        self.assert_rejected('UNSUPPORTED_FIELD')

    def test_false_visual_review_is_rejected(self):
        self.data['plants'][1]['images'][0]['visualReview'] = 'EXPERT_APPROVED'
        self.assert_rejected('UNSUPPORTED_PHOTO_REVIEW_CLAIM')

    def test_repeated_original_is_rejected(self):
        self.data['plants'][1]['images'][1] = deepcopy(self.data['plants'][1]['images'][0])
        self.assert_rejected('DUPLICATE_PHOTO')

    def test_duplicate_taxon_is_rejected(self):
        self.data['plants'][1]['taxonomy']['acceptedUsageKey'] = 100
        self.assert_rejected('DUPLICATE_ACCEPTED_TAXON')

    def test_missing_legacy_entry_is_rejected(self):
        self.data['plants'].pop(0)
        self.assert_rejected('LEGACY_INVENTORY_MISMATCH')

    def test_wrong_baseline_hash_is_rejected(self):
        self.data['baseline']['catalogSha256'] = '0' * 64
        self.assert_rejected('APPLICATION_BASELINE_CHANGED')

    def test_current_app_changes_do_not_redefine_historical_baseline(self):
        (self.library.parents[1] / 'android/app/src/main/assets/plant-catalog.json').write_text('{"plants": []}')
        rc, report = self.run_audit()
        self.assertEqual(0, rc)
        self.assertEqual([], report['errors'])

    def test_different_baseline_identity_is_rejected(self):
        self.data['baseline']['repositoryCommit'] = '0' * 40
        self.assert_rejected('APPLICATION_BASELINE_CHANGED')

    def test_unproven_implementation_approval_is_rejected(self):
        self.data['plants'][1]['review']['implementation'] = 'APPROVED'
        self.assert_rejected('UNPROVEN_IMPLEMENTATION_APPROVAL')

    def test_wrong_license_version_url_is_rejected(self):
        self.data['plants'][1]['images'][0]['licenseUrl'] = 'https://creativecommons.org/licenses/by/3.0/'
        self.assert_rejected('LICENSE_URL')

    def test_excluded_source_cannot_be_reintroduced_as_supplement(self):
        (self.library / 'evidence/photo-exclusions.json').write_text(json.dumps({'records': [
            {'sourcePage': 'https://example.invalid/qa-plant'}]}))
        self.assert_rejected('EXCLUDED_PHOTO_REINTRODUCED')

    def test_future_listing_cannot_be_marked_in_force(self):
        review = {'plantId': 'qa_new_plant', 'checkedAt': '2026-10-23', 'EU': {
            'listedName': 'QA future plant', 'effectiveDate': '2027-08-02', 'status': 'RESTRICTIONS_IN_FORCE'}}
        self.data['plants'][1]['legalReview'] = review
        (self.library / 'evidence/legal-review.json').write_text(json.dumps({'records': [review]}))
        (self.library / 'evidence/legal-sources.json').write_text(json.dumps({'sources': [{'rows': [
            {'scientificName': 'QA future plant', 'effectiveDate': '02.08.2027'}]}]}))
        self.assert_rejected('LEGAL_EFFECTIVE_STATUS_MISMATCH')

    def test_false_kew_approval_is_rejected(self):
        self.data['plants'][1]['taxonomy']['status'] = 'VERIFIED_BY_KEW'
        self.assert_rejected('UNSUPPORTED_KEW_CLAIM')

    def test_false_official_legal_review_is_rejected(self):
        self.data['plants'][1]['legalReview'] = {'EU': {}}
        self.assert_rejected('LEGAL_REVIEW_EVIDENCE_MISMATCH')

    def test_unsupported_rhs_warning_is_rejected(self):
        self.data['plants'][1]['safety']['rhsWarningEvidence'] = [
            {'source': 'https://example.invalid/qa-plant', 'field': 'Potentially harmful', 'values': ['QA warning']}]
        self.assert_rejected('UNSUPPORTED_RHS_WARNING')

    def test_license_ports_and_cc0_urls(self):
        from audit_library import license_url_matches
        self.assertTrue(license_url_matches('CC-BY-3.0-US', 'https://creativecommons.org/licenses/by/3.0/us/'))
        self.assertTrue(license_url_matches('CC-BY-SA-2.1-JP', 'https://creativecommons.org/licenses/by-sa/2.1/jp/'))
        self.assertTrue(license_url_matches('CC0-1.0', 'https://creativecommons.org/publicdomain/zero/1.0/'))
        self.assertFalse(license_url_matches('CC-BY-3.0-US', 'https://creativecommons.org/licenses/by/3.0/'))
        self.assertFalse(license_url_matches('CC0-1.0', 'https://creativecommons.org/licenses/by/4.0/'))

    def test_author_comparison_preserves_non_latin_identity(self):
        from build_library import norm, license_key
        self.assertNotEqual(norm('植物'), norm('花卉'))
        self.assertEqual(license_key('CC0 1.0'), license_key('CC0-1.0'))


if __name__ == '__main__':
    unittest.main()

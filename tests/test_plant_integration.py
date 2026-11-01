"""Synthetic checks of the real importer; no business store or network access."""
from copy import deepcopy
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.import_plant_library import (FORMAT, LIBRARY_COMMIT, adapt_profile,
    compile_catalog, encoded, entry_sha, sha)


class PlantIntegrationTests(unittest.TestCase):
    def setUp(self):
        from PIL import Image
        self.temp = tempfile.TemporaryDirectory(prefix='qa_plant_import_')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.photos = self.root / 'qa_public_photos'
        self.photos.mkdir()
        self.review = self.root / 'libraries/plants/integration-reviews/qa-plant.md'
        self.review.parent.mkdir(parents=True)
        self.choices = []
        refs = []
        hashes = []
        for n, color in enumerate(('green', 'red'), 1):
            stream = io.BytesIO()
            Image.new('RGB', (240, 180), color).save(stream, format='JPEG')
            raw = stream.getvalue()
            name = f'qa-{n}.jpg'
            (self.photos / name).write_bytes(raw)
            hashes.append(sha(raw))
            refs.append({'id': f'qa-ref-{n}', 'author': 'QA Author', 'caption': f'QA living plant motif {n}',
                'license': 'CC-BY-4.0', 'licenseUrl': 'https://creativecommons.org/licenses/by/4.0/',
                'sourcePage': 'https://example.invalid/qa-plant',
                'originalLocator': f'https://example.invalid/qa-{n}.jpg'})
            self.choices.append({'sourceKind': 'downloaded_original', 'referenceId': f'qa-ref-{n}',
                'originalPath': name, 'originalSha256': sha(raw), 'derivativePath': name,
                'derivativeSha256': sha(raw), 'width': 240, 'height': 180,
                'changes': 'Synthetic JPEG fixture; no metadata.', 'creditReconfirmedAt': '2026-10-23'})
        self.review.write_text('Synthetic fixture, not a real botanical review. qa-plant\n' + '\n'.join(hashes))
        for choice in self.choices:
            choice['visualReview'] = {'status': 'ACCEPTED_LIVING_PLANT', 'checkedAt': '2026-10-23',
                'evidencePath': 'libraries/plants/integration-reviews/qa-plant.md',
                'evidenceSha256': sha(self.review.read_bytes()), 'note': 'Synthetic fixture only.'}
        self.plant = {'id': 'qa-plant', 'legacyId': 'qa-plant', 'name': 'QA plant',
            'scientificName': 'QA species', 'category': 'QA category', 'taxonScope': 'species',
            'traits': {'family': ['QA family'], 'soilDrainage': ['Gut durchlässig'],
                'soilReaction': ['Sauer'], 'hardinessUSDA': ['5a'], 'light': ['Sonne'],
                'dimensionsSource': ['Height: 2 ft.'], 'originSource': ['QA source region']},
            'taxonomy': {'status': 'EXACT_MATCH', 'acceptedUsageKey': 100, 'checkedAt': '2026-10-23'},
            'cultivationScope': 'SOURCE_REGION_FACTS_ONLY',
            'safety': {'status': 'UNKNOWN_NOT_PROVEN_HARMLESS', 'notice': 'Keine Verzehrfreigabe.',
                'rhsWarningSource': ['QA contact warning'],
                'legacyReportedWarningsPendingReconfirmation': {'poison': ['giftig']}},
            'review': {'sourceFacts': 'CHECKED', 'implementation': 'HOLD'}, 'images': refs,
            'sources': [{'url': 'https://example.invalid/qa-plant', 'checkedAt': '2026-10-23',
                         'evidenceSha256': '1' * 64}]}
        self.library = {'plants': [self.plant]}
        self.current = {'schemaVersion': 2, 'edition': 'qa-old', 'plants': [
            {'id': 'qa-plant', 'name': 'QA old', 'scientificName': 'QA species', 'care': 'QA unverified care',
             'planting': 'QA unverified planting', 'poison': ['giftig'], 'sources': [], 'images': []},
            {'id': 'qa-unrelated', 'name': 'QA extra app entry'}]}
        self.decision = {'id': 'qa-plant', 'decision': 'APPROVED', 'factsCheckedAt': '2026-10-23',
            'profileSha256': entry_sha(self.plant), 'images': self.choices}
        self.manifest = {'format': FORMAT, 'sourceCommit': LIBRARY_COMMIT, 'edition': 'qa-new',
            'sourceLibrarySha256': entry_sha(self.library), 'profiles': [self.decision]}

    def compile(self, *, current=None, complete=False):
        return compile_catalog(self.library, current or self.current, self.manifest,
            root=self.root, photo_root=self.photos, require_complete=complete)

    def refresh_binding(self):
        self.decision['profileSha256'] = entry_sha(self.plant)
        self.manifest['sourceLibrarySha256'] = entry_sha(self.library)

    def test_preserves_ids_extra_entries_and_is_idempotent(self):
        result, files, report = self.compile(complete=True)
        again, again_files, _ = self.compile(current=result, complete=True)
        self.assertEqual(encoded(result), encoded(again))
        self.assertEqual(files, again_files)
        self.assertEqual(['qa-plant', 'qa-unrelated'], [p['id'] for p in result['plants']])
        self.assertEqual(self.current['plants'][1], result['plants'][1])
        self.assertEqual(1, report['approved'])
        self.assertEqual(2, len(files))

    def test_hold_is_not_promoted_and_complete_release_is_blocked(self):
        self.decision.update(decision='HOLD', reasons=['QA image review pending'])
        out, files, report = self.compile()
        self.assertEqual(self.current, out)
        self.assertEqual({}, files)
        self.assertEqual(1, report['held'])
        with self.assertRaisesRegex(ValueError, 'Integration blocked'):
            self.compile(complete=True)

    def test_sourced_mapping_does_not_invent_care_water_or_frost_guarantees(self):
        plant = self.compile()[0]['plants'][0]
        self.assertEqual(['Gut durchlässig'], plant['water'])
        self.assertEqual(['Height: 2 ft.'], plant['dimensions'])
        self.assertEqual(['USDA 5a'], plant['hardiness'])
        self.assertEqual('', plant['care'])
        self.assertEqual('', plant['planting'])
        self.assertEqual(['giftig'], plant['poison'])
        self.assertEqual(['QA contact warning'], plant['rhsWarnings'])

    def test_actual_original_hash_is_required(self):
        self.choices[0]['originalSha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'Original photo hash'):
            self.compile()

    def test_actual_derivative_hash_is_required(self):
        self.choices[0]['derivativeSha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'App image hash'):
            self.compile()

    def test_metadata_or_thumbnail_approval_is_insufficient(self):
        self.choices[0]['visualReview']['status'] = 'CONTACT_SHEET_SCREENED_NO_GROSS_MISMATCH_DETECTED'
        with self.assertRaisesRegex(ValueError, 'Actual image review'):
            self.compile()

    def test_visual_evidence_is_bound_to_actual_image_bytes(self):
        self.review.write_text('qa-plant, synthetic unbound visual evidence')
        self.choices[0]['visualReview']['evidenceSha256'] = sha(self.review.read_bytes())
        with self.assertRaisesRegex(ValueError, 'does not bind the actual'):
            self.compile()

    def test_visual_evidence_hash_is_checked(self):
        self.choices[0]['visualReview']['evidenceSha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'Visual review evidence differs'):
            self.compile()

    def test_two_distinct_images_are_required(self):
        self.choices[1].update(self.choices[0])
        with self.assertRaisesRegex(ValueError, 'selected twice'):
            self.compile()

    def test_noncommercial_and_wrong_country_version_licenses_rejected(self):
        for value in ['CC-BY-NC-4.0', 'CC-BY-SA-3.0-AT']:
            with self.subTest(value=value):
                self.plant['images'][0]['license'] = value
                self.refresh_binding()
                with self.assertRaisesRegex(ValueError, 'image license'):
                    self.compile()

    def test_arbitrary_or_traversing_image_path_is_rejected(self):
        self.choices[0]['originalPath'] = '../qa-private.jpg'
        with self.assertRaisesRegex(ValueError, 'Unsafe public file path'):
            self.compile()

    def test_symlinked_image_is_rejected(self):
        (self.photos / 'qa-link.jpg').symlink_to(self.photos / 'qa-1.jpg')
        self.choices[0]['originalPath'] = 'qa-link.jpg'
        with self.assertRaisesRegex(ValueError, 'Symlinked public source'):
            self.compile()

    def test_exif_is_rejected_even_with_matching_hash(self):
        from PIL import Image
        path = self.photos / 'qa-1.jpg'
        stream = io.BytesIO()
        exif = Image.Exif()
        exif[0x010E] = 'QA synthetic metadata'
        Image.new('RGB', (240, 180), 'green').save(stream, format='JPEG', exif=exif)
        path.write_bytes(stream.getvalue())
        self.choices[0].update(originalSha256=sha(path.read_bytes()), derivativeSha256=sha(path.read_bytes()))
        with self.assertRaisesRegex(ValueError, 'metadata-free JPEG'):
            self.compile()

    def test_old_excluded_image_cannot_return(self):
        self.plant['legacyPhotos'] = [{'path': 'app/src/main/assets/plant-images/qa-old.jpg',
            'editorialDecision': 'CLOSED_EXCLUDED_FROM_LIBRARY_USE'}]
        self.choices[0].update(sourceKind='legacy_derivative', legacyPath=self.plant['legacyPhotos'][0]['path'])
        self.refresh_binding()
        with self.assertRaisesRegex(ValueError, 'excluded from reuse'):
            self.compile()

    def test_renewed_exclusion_cannot_be_overridden_by_old_metadata_approval(self):
        review=self.root/'libraries/plants/integration-legacy-review.json'
        review.write_text(json.dumps({'records':[{'sha256':self.choices[0]['derivativeSha256'],
            'decision':'EXCLUDE_FROM_INTEGRATION'}]}))
        with self.assertRaisesRegex(ValueError, 'Renewed visual review excludes'):
            self.compile()

    def test_profile_change_invalidates_its_review(self):
        self.plant['name'] = 'QA changed'
        self.manifest['sourceLibrarySha256'] = entry_sha(self.library)
        with self.assertRaisesRegex(ValueError, 'Profile review is stale'):
            self.compile()

    def test_reference_profile_has_no_propagation_or_new_care_advice(self):
        self.plant['cultivationScope'] = 'IDENTIFICATION_AND_PREVENTION_REFERENCE_NO_CULTIVATION_ADVICE'
        self.plant['traits']['propagation'] = ['QA unsuitable propagation']
        self.refresh_binding()
        plant = self.compile()[0]['plants'][0]
        self.assertEqual([], plant['propagation'])
        self.assertEqual('', plant['care'])

    def test_rejected_reference_cannot_return_as_a_newly_encoded_original(self):
        path = self.root / 'libraries/plants/integration-photo-exclusions.json'
        path.write_text(json.dumps({'records': [{'plantId': 'qa-plant', 'referenceId': 'qa-ref-1'}]}))
        with self.assertRaisesRegex(ValueError, 'reference is excluded'):
            self.compile()

    def supplemental(self):
        photo = self.plant['images'].pop(0)
        path = self.root / 'libraries/plants/integration-sources/qa-plant.json'
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps({'plantId': 'qa-plant', 'checkedAt': '2026-10-23',
            'responseSha256': '2' * 64, 'images': [photo]}))
        self.decision['additionalPhotoReferences'] = [{'referenceId': photo['id'],
            'evidencePath': path.relative_to(self.root).as_posix(), 'evidenceSha256': sha(path.read_bytes())}]
        self.refresh_binding()
        return path

    def test_supplemental_photo_preserves_frozen_library_and_requires_bound_source(self):
        self.supplemental()
        self.assertEqual(2, len(self.compile()[0]['plants'][0]['images']))
        self.decision['additionalPhotoReferences'][0]['evidenceSha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'source evidence differs'):
            self.compile()

    def test_supplemental_source_for_another_profile_is_rejected(self):
        path = self.supplemental()
        evidence = json.loads(path.read_text()); evidence['plantId'] = 'qa-other'
        path.write_text(json.dumps(evidence))
        self.decision['additionalPhotoReferences'][0]['evidenceSha256'] = sha(path.read_bytes())
        with self.assertRaisesRegex(ValueError, 'source profile differs'):
            self.compile()

    def test_supplemental_source_does_not_override_existing_reference(self):
        path = self.supplemental()
        evidence = json.loads(path.read_text()); self.plant['images'].append(evidence['images'][0])
        self.refresh_binding()
        with self.assertRaisesRegex(ValueError, 'missing or duplicated'):
            self.compile()

    def test_camera_mpo_is_decoded_but_never_distributed_as_a_derivative(self):
        from PIL import Image
        from tools.import_plant_library import decode
        stream = io.BytesIO()
        Image.new('RGB', (240, 180), 'green').save(stream, format='MPO', save_all=True,
            append_images=[Image.new('RGB', (240, 180), 'red')])
        self.assertEqual(240, decode(stream.getvalue())['width'])
        with self.assertRaisesRegex(ValueError, 'metadata-free JPEG'):
            decode(stream.getvalue(), derivative=True)

    def test_unknown_safety_cannot_promote_historical_harmlessness(self):
        self.current['plants'][0]['poison'] = ['ungiftig']
        plant = self.compile()[0]['plants'][0]
        self.assertNotIn('ungiftig', plant['poison'])
        self.assertTrue(any('nicht erneut bestätigt' in text for text in plant['poison']))
        self.assertIn('giftig', plant['poison'])

    def test_accepted_name_is_added_without_renaming_existing_id(self):
        self.plant['taxonomy']['authorityResolution'] = {'acceptedName': 'QA accepted', 'legacyName': 'QA species'}
        self.refresh_binding()
        plant = self.compile()[0]['plants'][0]
        self.assertEqual('qa-plant', plant['id'])
        self.assertEqual('QA accepted', plant['acceptedName'])
        self.assertIn('QA species', plant['alternativeNames'])

    def test_future_eu_and_swiss_sales_restrictions_remain_separate(self):
        self.current['plants'][0]['neophyte'] = {'unionConcern': True, 'legalNotes': ['QA old blanket ban']}
        self.plant['legalReview'] = {'checkedAt': '2026-10-23',
            'EU': {'status': 'LISTED_FUTURE_EFFECTIVE_DATE', 'effectiveDate': '2027-08-02',
                   'source': 'https://example.invalid/qa-eu', 'restrictionsSummary': 'QA dated restrictions'},
            'CH': {'status': 'ANNEX_2_2_MARKETING_BAN', 'annex': '2.2',
                   'lawSource': 'https://example.invalid/qa-ch', 'notice': 'QA sales restriction'},
            'regionalRules': 'QA local rules apply'}
        self.refresh_binding()
        plant = self.compile()[0]['plants'][0]
        self.assertIn('ab 2027-08-02', plant['neophyte']['legalNotes'][0])
        self.assertNotIn('blanket ban', json.dumps(plant['neophyte']))
        self.assertEqual('2.2', plant['regionalRestrictions']['CH']['annex'])
        self.assertTrue(plant['neophyte']['unionConcern'])


if __name__ == '__main__':
    unittest.main()

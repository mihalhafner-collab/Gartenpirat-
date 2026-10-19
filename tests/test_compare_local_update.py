"""Synthetic ZIP fixtures only; no production APK, database or device is used."""
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from compare_local_update import (COLLECTIONS, apk_inventory, compare_inventory,
                                  compare_assets_to_source, empty_seed,
                                  source_equivalence)


class LocalUpdateComparisonTests(unittest.TestCase):
    def test_only_reviewed_export_label_is_equivalent(self):
        self.assertEqual(source_equivalence(b'label="Arbeitsaufzeichnungen"',
                                           b'label="Cloudname_189a14e78c"'),
                         'reviewed_export_label_only')
        self.assertEqual(source_equivalence(b'save(id, false)', b'save(id, true)'),
                         'unreviewed_difference')
        self.assertEqual(source_equivalence(b'same', b'same'), 'identical')

    def test_change_categories(self):
        self.assertEqual(compare_inventory({'old': 'a', 'same': 's', 'edit': '1'},
                                           {'new': 'b', 'same': 's', 'edit': '2'}),
                         {'added': ['new'], 'removed': ['old'], 'changed': ['edit'],
                          'unchanged_count': 1})

    def test_package_must_match_every_source_asset(self):
        with __import__("synthetic_fixtures").temporary_directory() as directory:
            root = Path(directory)
            assets = root/'assets'
            assets.mkdir()
            (assets/'ui.js').write_bytes(b'const fixture = true;')
            apk = root/'synthetic.zip'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('assets/ui.js', b'const fixture = false;')
                archive.writestr('assets/unexpected.txt', b'fictional')
            result = compare_assets_to_source(apk, assets)
            self.assertFalse(result['matches'])
            self.assertEqual(result['changed'], ['ui.js'])
            self.assertEqual(result['added'], ['unexpected.txt'])

    def test_seed_is_empty_and_no_values_are_returned(self):
        with __import__("synthetic_fixtures").temporary_directory() as directory:
            apk = Path(directory)/'synthetic.zip'
            seed = {key: [] for key in COLLECTIONS}
            for occupied in (False, True):
                seed['customers'] = [{'id': 'qa_synthetic-only'}] if occupied else []
                with zipfile.ZipFile(apk, 'w') as archive:
                    archive.writestr('assets/initial-data.json', json.dumps(seed))
                result = empty_seed(apk)
                self.assertEqual(result['all_business_collections_empty'], not occupied)
                self.assertNotIn('qa_synthetic-only', json.dumps(result))
                self.assertIn('assets/initial-data.json', apk_inventory(apk))


if __name__ == '__main__':
    unittest.main()

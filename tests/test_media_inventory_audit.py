"""Synthetic metadata only; no Pixel, Drive or productive fixture reads."""
import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from tools.audit_media_inventory import audit


def fixture():
    return {'version': 1, 'files': [
        {'object_id': 'qa_photo', 'kind': 'photo', 'byte_size': 30},
        {'object_id': 'qa_spare', 'kind': 'photo', 'byte_size': 40},
        {'object_id': 'qa_receipt', 'kind': 'receipt', 'byte_size': 50},
        {'object_id': 'qa_temp', 'kind': 'temporary', 'byte_size': 20},
        {'object_id': 'qa_unknown', 'kind': 'unknown', 'byte_size': None}],
        'references': [{'object_id': 'qa_photo', 'source': 'active'}],
        'coverage': dict(inventory_complete=True, references_complete=True,
                         backups_complete=True, history_complete=True, queue_empty=True,
                         before_revision='qa_revision', after_revision='qa_revision')}


class MediaInventoryAuditTests(unittest.TestCase):
    def test_complete_classification_and_no_mutation(self):
        source = fixture(); before = copy.deepcopy(source)
        result = audit(source)
        self.assertEqual(source, before)
        self.assertEqual(result['status'], 'PASS')
        self.assertEqual({k: v['count'] for k, v in result['classes'].items()},
                         {'referenced': 1, 'orphaned': 1, 'temporary': 1, 'protected': 1, 'unknown': 1})
        self.assertFalse(result['deletion_authorized'])
        self.assertNotIn('qa_photo', json.dumps(result))

    def test_each_missing_coverage_fact_prevents_orphan_claims(self):
        for key in ('inventory_complete', 'references_complete', 'backups_complete', 'history_complete', 'queue_empty'):
            with self.subTest(key=key):
                source = fixture(); source['coverage'][key] = False
                result = audit(source)
                self.assertEqual(result['status'], 'INCOMPLETE')
                self.assertEqual(result['classes']['orphaned']['count'], 0)
                self.assertEqual(result['classes']['temporary']['count'], 0)

    def test_reordered_and_repeated_inventory_is_stable(self):
        source = fixture(); expected = audit(source)
        source['files'].reverse(); source['files'].append(copy.deepcopy(source['files'][0]))
        source['references'] *= 2
        self.assertEqual(expected, audit(source))

    def test_backup_history_final_and_archive_references(self):
        for source_kind in ('backup', 'history', 'final', 'archive', 'pending'):
            source = fixture(); source['references'][0]['source'] = source_kind
            result = audit(source)
            protected = source_kind in {'backup', 'history', 'final'}
            self.assertEqual(result['classes']['protected']['count'], 2 if protected else 1)
            self.assertEqual(result['classes']['referenced']['count'], 0 if protected else 1)

    def test_missing_objects_conflicts_and_revision_races_fail_closed(self):
        for variant in ('missing', 'collision', 'changed', 'empty'):
            source = fixture()
            if variant == 'missing':
                source['references'].append({'object_id': 'qa_missing', 'source': 'archive'})
            elif variant == 'collision':
                source['files'].append({'object_id': 'qa_spare', 'kind': 'photo', 'byte_size': 41})
            elif variant == 'changed':
                source['coverage']['after_revision'] = 'qa_other'
            else:
                source['coverage']['before_revision'] = ''
            self.assertEqual(audit(source)['status'], 'INCOMPLETE')
            self.assertEqual(audit(source)['classes']['orphaned']['count'], 0)

    def test_strict_schema_and_constant_errors(self):
        for change in (lambda s: s.update(customer='qa_forbidden'),
                       lambda s: s['files'][0].update(byte_size=True),
                       lambda s: s['files'][0].update(kind='invalid'),
                       lambda s: s['coverage'].update(queue_empty='true')):
            source = fixture(); change(source)
            with self.assertRaisesRegex(ValueError, '^Invalid normalized audit manifest; no files changed.$'):
                audit(source)

    def test_unknown_size_cannot_be_orphaned(self):
        source = fixture(); source['files'][1]['byte_size'] = None
        self.assertEqual(audit(source)['classes']['orphaned']['count'], 0)

    def test_unreferenced_documents_and_historical_backups_stay_protected(self):
        source = fixture()
        for kind in ('document', 'backup', 'history'):
            source['files'].append({'object_id': 'qa_'+kind, 'kind': kind, 'byte_size': 10})
        result = audit(source)
        self.assertEqual(result['classes']['protected']['count'], 4)
        self.assertEqual(result['classes']['orphaned']['count'], 1)
        self.assertFalse(result['deletion_authorized'])

    def test_cli_has_no_private_root_fallback_or_repository_input(self):
        project = Path(__file__).resolve().parents[1]
        env = dict(os.environ); env.pop('HG_PRIVATE_DATA_ROOT', None)
        result = subprocess.run([sys.executable, str(project/'tools/audit_media_inventory.py'),
                                 '--manifest', str(project/'android/app/src/main/assets/initial-data.json')],
                                env=env, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('AUDIT_FAILED:', result.stderr)

    def test_cli_reads_only_explicit_private_metadata_and_never_writes_it(self):
        project = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory(prefix='qa_media_audit_') as directory:
            path = Path(directory)/'qa_manifest.json'; path.write_text(json.dumps(fixture()))
            before = path.read_bytes(); env = dict(os.environ, HG_PRIVATE_DATA_ROOT=directory)
            result = subprocess.run([sys.executable, str(project/'tools/audit_media_inventory.py'),
                                     '--manifest', str(path)], env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(path.read_bytes(), before)
            self.assertEqual(len(list(Path(directory).iterdir())), 1)
            self.assertEqual(json.loads(result.stdout)['mode'], 'READ_ONLY')

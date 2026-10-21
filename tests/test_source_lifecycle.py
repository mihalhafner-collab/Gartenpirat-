"""Isolated synthetic housekeeping and lossless export scenarios; no accounts."""
import copy
import datetime as dt
import json
import io
from pathlib import Path
import tempfile
import unittest
from unittest import mock
import zipfile

from tools import source_lifecycle as life


class SourceLifecycleTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='qa_source_lifecycle_')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.now = dt.datetime(2026, 10, 21, tzinfo=dt.timezone.utc)
        self.policy = {'folders': {'old': 'qa_old_folder'}, 'runtime_folder_ids': ['qa_runtime_folder'],
                       'protected_file_ids': ['qa_current_file'], 'keep_builds': [89, 90],
                       'retention_days': {'apk_retired': 0, 'test_apk': 14, 'ki_export': 7}}
        self.row = {'id': 'qa_old_file', 'name': 'Haus-Garten-qa-old.apk',
                    'mime_type': 'application/vnd.android.package-archive', 'bytes': '123',
                    'folder_id': 'qa_old_folder', 'modified_time': '2026-10-01T00:00:00Z',
                    'shared': False}
        self.registered = {**self.row, 'role': 'apk_retired', 'replacement_verified': True,
                           'retired_at': '2026-10-01T00:00:00Z'}

    def plan(self, row=None, item=None, complete=True):
        return life.cleanup_plan(self.policy, {'files': [item or self.registered]},
                                 {'complete': complete, 'files': [row or self.row]}, self.now)

    def test_known_retired_apk_with_verified_replacement_is_selected(self):
        result = self.plan()
        self.assertEqual(len(result['delete']), 1)
        self.assertEqual(result['delete_bytes'], 123)
        self.assertTrue(life.check_before_delete(result['delete'][0], self.row, self.policy))

    def test_unknown_or_runtime_folder_is_never_deleted(self):
        for folder in ('qa_runtime_folder', 'qa_unmanaged_folder'):
            self.assertFalse(self.plan(row={**self.row, 'folder_id': folder})['delete'])

    def test_protected_active_or_retained_release_is_never_deleted(self):
        for extra in ({'protected': True}, {'active': True}, {'build': 89}, {'build': 90}):
            self.assertFalse(self.plan(item={**self.registered, **extra})['delete'])

    def test_current_apk_file_id_is_never_deleted(self):
        row = {**self.row, 'id': 'qa_current_file'}
        self.assertFalse(self.plan(row=row, item={**self.registered, **row})['delete'])

    def test_changed_metadata_sharing_and_missing_timestamp_block_deletion(self):
        for extra in ({'bytes': '124'}, {'name': 'qa_renamed.apk'}, {'modified_time': None},
                      {'modified_time': '2026-10-02T00:00:00Z'}, {'shared': True}, {'trashed': True}):
            self.assertFalse(self.plan(row={**self.row, **extra})['delete'])

    def test_missing_replacement_and_retirement_time_block_deletion(self):
        for extra in ({'replacement_verified': False}, {'retired_at': None}, {'role': 'unknown'}):
            self.assertFalse(self.plan(item={**self.registered, **extra})['delete'])

    def test_incomplete_and_duplicate_inventory_is_rejected(self):
        with self.assertRaises(ValueError):
            self.plan(complete=False)
        for records, rows in [([self.registered] * 2, [self.row]), ([self.registered], [self.row] * 2)]:
            with self.assertRaises(ValueError):
                life.cleanup_plan(self.policy, {'files': records}, {'complete': True, 'files': rows}, self.now)

    def test_runtime_and_secret_names_are_not_artifact_candidates(self):
        for name in ('HausGarten-Mobile-qa.json', '.env.qa'):
            row = {**self.row, 'name': name}
            self.assertFalse(self.plan(row=row, item={**self.registered, **row})['delete'])

    def test_wrong_mime_and_unknown_id_are_held(self):
        row = {**self.row, 'mime_type': 'application/json'}
        self.assertFalse(self.plan(row=row, item={**self.registered, **row})['delete'])
        self.assertFalse(self.plan(row={**self.row, 'id': 'qa_unknown'})['delete'])

    def test_retention_boundary_and_future_retirement(self):
        item = {**self.registered, 'role': 'test_apk', 'retired_at': '2026-10-08T00:00:00Z'}
        self.assertFalse(self.plan(item=item)['delete'])
        item['retired_at'] = '2026-10-07T00:00:00Z'
        self.assertEqual(len(self.plan(item=item)['delete']), 1)
        item['retired_at'] = '2026-11-01T00:00:00Z'
        self.assertFalse(self.plan(item=item)['delete'])

    def test_final_metadata_recheck_rejects_concurrent_changes(self):
        candidate = self.plan()['delete'][0]
        for key, value in [('bytes', '9'), ('folder_id', 'qa_runtime_folder'), ('shared', True)]:
            with self.assertRaises(ValueError):
                life.check_before_delete(candidate, {**self.row, key: value}, self.policy)

    def test_naive_retirement_time_is_rejected(self):
        with self.assertRaises(ValueError):
            self.plan(item={**self.registered, 'retired_at': '2026-10-01'})

    def test_generation_folders_require_verified_managed_parent(self):
        row = {**self.row, 'folder_id': 'qa_generation'}
        item = {**self.registered, **row}
        policy = {**self.policy, 'folders': {**self.policy['folders'], 'development': 'qa_development'}}
        registry = {'files': [item], 'generation_folders': [
            {'id': 'qa_generation', 'parent_id': 'qa_development', 'verified': True}]}
        inventory = {'complete': True, 'files': [row]}
        self.assertEqual(len(life.cleanup_plan(policy, registry, inventory, self.now)['delete']), 1)
        for change in ({'verified': False}, {'parent_id': 'qa_runtime_folder'}):
            bad = {**registry, 'generation_folders': [{**registry['generation_folders'][0], **change}]}
            self.assertFalse(life.cleanup_plan(policy, bad, inventory, self.now)['delete'])

    def test_utf8_chunks_preserve_multibyte_bom_and_empty_source(self):
        for raw in (b'', b'\xef\xbb\xbf', ('\ufeffä🙂\r\n' * 20).encode()):
            parts = list(life.utf8_parts(raw, limit=7))
            self.assertEqual(b''.join(p for _, p in parts), raw)
            for _, chunk in parts:
                chunk.decode('utf-8')

    def test_review_bundle_losslessly_recovers_all_text_and_inventories_binary(self):
        archive = self.root / 'qa_source.zip'
        files = {'qa_empty.py': b'', 'qa_source.py': b'\xef\xbb\xbf# QA source\r\n',
                 'qa_public.bin': b'\0QA_PUBLIC_BINARY'}
        with zipfile.ZipFile(archive, 'w') as z:
            for name, raw in files.items():
                z.writestr(name, raw)
        out = self.root / 'qa_export'
        result = life.review_bundle(archive, out, 'a' * 40)
        self.assertEqual(result['text_files'], 2)
        self.assertEqual(result['public_binary_files'], 1)
        self.assertEqual(result['text_reconstruction'], 'PASS')
        paths = list(out.glob('*TEIL-*.txt'))
        life.verify_text_exports(paths, {k: v for k, v in files.items() if k.endswith('.py')})
        raw = paths[0].read_bytes().replace(b'# QA source', b'# QA damage')
        paths[0].write_bytes(raw)
        with self.assertRaises(ValueError):
            life.verify_text_exports(paths, {k: v for k, v in files.items() if k.endswith('.py')})

    def test_local_cleanup_requires_registered_hash_completed_state_and_age(self):
        (self.root / 'outputs').mkdir()
        path = self.root / 'outputs/qa_old.log'
        path.write_text('QA_TECHNICAL_LOG')
        item = {'path': 'outputs/qa_old.log', 'classification': 'SYNTHETIC_TEST', 'role': 'log',
                'completed': True, 'retired_at': '2026-09-01T00:00:00Z',
                'sha256': life.digest(path.read_bytes())}
        self.assertEqual(len(life.cleanup_local(self.root, [item], self.now)['files']), 1)
        self.assertTrue(path.exists())
        for extra in ({'completed': False}, {'protected': True}, {'sha256': '0' * 64},
                      {'classification': 'PRIVATE_RUNTIME'}, {'retired_at': '2026-10-20T00:00:00Z'}):
            self.assertFalse(life.cleanup_local(self.root, [{**item, **extra}], self.now)['files'])
        life.cleanup_local(self.root, [item], self.now, apply=True)
        self.assertFalse(path.exists())

    def test_local_cleanup_rejects_parent_traversal_and_symlink_escape(self):
        (self.root / 'outputs').mkdir()
        outside = self.root / 'qa_important.py'
        outside.write_text('QA_IMPORTANT = True')
        (self.root / 'outputs/qa_link').symlink_to(self.root, target_is_directory=True)
        for name in ('outputs/../qa_important.py', 'outputs/qa_link/qa_important.py'):
            with self.assertRaises(ValueError):
                life.cleanup_local(self.root, [{'path': name}], self.now, apply=True)
        self.assertTrue(outside.exists())

    def test_restore_checks_pinned_remote_tag_and_original_source_bytes(self):
        files = {'android/app/build.gradle': b'// QA build', 'qa_source.py': b'QA_SOURCE = True\n'}
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            archive.writestr('android/', b'')
            archive.writestr('android/app/', b'')
            for name, raw in files.items():
                archive.writestr(name, raw)
        release = {'tag': 'source/qa_build', 'git_sha': 'a' * 40, 'source_files': 2,
                   'source_sha256': life.fingerprint({'android/app/build.gradle': files['android/app/build.gradle']})}
        def git(*args):
            return (('a' * 40) + '\trefs/tags/source/qa_build^{}').encode() if args[0] == 'ls-remote' else buffer.getvalue()
        self.assertEqual(life.restore_check([release], git)[0]['status'], 'PASS')
        for bad in ({'git_sha': 'b' * 40}, {'source_files': 3}, {'source_sha256': '0' * 64}):
            with self.assertRaises(ValueError):
                life.restore_check([{**release, **bad}], git)

    def test_external_export_cache_requires_verified_public_replacement(self):
        cache = self.root / 'hausgarten-source-exports'
        cache.mkdir()
        path = cache / 'qa_source.txt'
        path.write_text('QA_PUBLIC_SOURCE')
        item = {'path': path.name, 'classification': 'PUBLIC_SOURCE', 'replacement_verified': True,
                'retired_at': '2026-10-01T00:00:00Z', 'sha256': life.digest(path.read_bytes())}
        for change in ({'replacement_verified': False}, {'protected': True}, {'classification': 'PRIVATE_RUNTIME'},
                       {'sha256': '0' * 64}, {'retired_at': '2026-10-20T00:00:00Z'}):
            self.assertFalse(life.cleanup_export_cache(cache, [{**item, **change}], self.now, apply=True)['files'])
        self.assertTrue(path.exists())
        self.assertEqual(life.cleanup_export_cache(cache, [item], self.now, apply=True)['files'], [path.name])
        self.assertFalse(path.exists())

    def test_external_export_cache_cannot_escape_or_target_repository(self):
        cache = self.root / 'hausgarten-source-exports'
        cache.mkdir()
        for path in ('../qa_source.txt', str(self.root / 'qa_source.txt')):
            with self.assertRaises(ValueError):
                life.cleanup_export_cache(cache, [{'path': path}], self.now, apply=True)
        with self.assertRaises(ValueError):
            life.cleanup_export_cache(life.ROOT / 'hausgarten-source-exports', [], self.now)

    def test_local_retired_artifact_keeps_current_build_and_requires_verified_replacement(self):
        (self.root / 'outputs').mkdir()
        path = self.root / 'outputs/qa_old.apk'
        path.write_bytes(b'QA_NOT_A_REAL_APK')
        item = {'path': 'outputs/qa_old.apk', 'classification': 'PUBLIC_ARTIFACT', 'role': 'apk_retired',
                'completed': True, 'build': 88, 'replacement_verified': True,
                'retired_at': '2026-10-20T00:00:00Z', 'sha256': life.digest(path.read_bytes())}
        for change in ({'replacement_verified': False}, {'build': 89}, {'build': 90}, {'active': True}):
            self.assertFalse(life.cleanup_local(self.root, [{**item, **change}], self.now, apply=True)['files'])
        self.assertTrue(path.exists())
        with mock.patch.object(life, 'artifact_gate', return_value={'status': 'PASS'}):
            self.assertEqual(life.cleanup_local(self.root, [item], self.now, apply=True)['files'], [item['path']])
        self.assertFalse(path.exists())

    def test_public_generated_build_requires_replacement_and_seven_days(self):
        (self.root / 'outputs').mkdir()
        path = self.root / 'outputs/qa_build.log'
        path.write_text('QA_PUBLIC_BUILD')
        item = {'path': 'outputs/qa_build.log', 'classification': 'PUBLIC_ARTIFACT', 'role': 'build_output',
                'completed': True, 'replacement_verified': True, 'retired_at': '2026-10-14T00:00:00Z',
                'sha256': life.digest(path.read_bytes())}
        self.assertFalse(life.cleanup_local(self.root, [{**item, 'replacement_verified': False}], self.now)['files'])
        self.assertFalse(life.cleanup_local(self.root, [{**item, 'retired_at': '2026-10-15T00:00:00Z'}], self.now)['files'])
        self.assertEqual(life.cleanup_local(self.root, [item], self.now, apply=True)['files'], [item['path']])


if __name__ == '__main__':
    unittest.main()

"""Synthetic transfer plans and public source bytes; no Drive/account access."""
import copy
from pathlib import Path
import tempfile
import unittest
import zipfile

from tools.drive_source_transfer import (local_artifacts, observed, plan_transfer,
                                        sha256, transfer_schedule, verified_receipt, write_verified_zip)


class DriveSourceTransferTests(unittest.TestCase):
    def setUp(self):
        self.folder = 'qa_source_folder_001'
        self.artifact = {'name': 'qa-source.txt', 'bytes': 2, 'sha256': sha256(b'{}'), 'mime_type': 'text/plain'}
        self.row = {'id': 'qa_source_file_001', 'title': self.artifact['name'], 'size': '2',
                    'mime_type': 'text/plain', 'modified_time': '2026-10-21T00:00:00Z', 'shared': False}
        self.inventory = {'folder_id': self.folder, 'complete': True, 'files': [self.row]}
        self.receipt = verified_receipt(self.artifact, self.row, self.folder, readback_sha256=self.artifact['sha256'])
        self.previous = {'folder_id': self.folder, 'receipts': [self.receipt]}

    def plan(self, artifacts=None):
        return plan_transfer(artifacts or [self.artifact], self.inventory, self.previous)

    def test_unchanged_verified_export_is_noop(self):
        plan = self.plan()
        self.assertEqual(plan['counts']['reuse'], 1)
        self.assertEqual(plan['upload_bytes'], 0)
        self.assertEqual(plan['deletions'], [])

    def test_only_changed_or_missing_exports_upload(self):
        changed = {**self.artifact, 'sha256': sha256(b'[]')}
        missing = {**self.artifact, 'name': 'qa-new.txt'}
        plan = self.plan([changed, missing])
        self.assertEqual(plan['counts']['upload'], 2)
        self.assertEqual(plan['actions'][0]['supersedes_id'], self.row['id'])
        self.assertEqual(plan['deletions'], [])

    def test_name_and_size_without_receipt_require_body_verification(self):
        self.previous['receipts'] = []
        self.assertEqual(self.plan()['counts']['verify_existing'], 1)
        self.assertEqual(self.plan()['upload_bytes'], 0)

    def test_every_metadata_change_invalidates_reuse(self):
        for key, value in [('modified_time', None), ('modified_time', '2026-10-22T00:00:00Z'),
                           ('size', '3'), ('mime_type', 'application/json'), ('shared', True), ('trashed', True)]:
            with self.subTest(key=key, value=value):
                inventory = copy.deepcopy(self.inventory); inventory['files'][0][key] = value
                plan = plan_transfer([self.artifact], inventory, self.previous)
                self.assertEqual(plan['counts']['reuse'], 0)
                self.assertEqual(plan['counts']['verify_existing'], 1)

    def test_replaced_id_requires_verification_even_same_name_size(self):
        self.row['id'] = 'qa_source_file_002'
        self.assertEqual(self.plan()['counts']['verify_existing'], 1)

    def test_ambiguous_upload_is_reconciled_without_reposting(self):
        # Upload response lost, but fresh inventory contains its new file.
        plan = plan_transfer([self.artifact], self.inventory)
        self.assertEqual(plan['counts']['upload'], 0)
        self.assertEqual(plan['counts']['verify_existing'], 1)
        self.inventory['files'].append({**self.row, 'id': 'qa_duplicate_file_001'})
        self.assertEqual(self.plan()['counts']['blocked_duplicates'], 1)
        self.assertEqual(self.plan()['counts']['upload'], 0)

    def test_wrong_folder_and_duplicate_inventory_ids_fail_closed(self):
        self.previous['folder_id'] = 'qa_other_folder_001'
        with self.assertRaises(ValueError):
            self.plan()
        self.previous['folder_id'] = self.folder
        self.inventory['files'].append(copy.deepcopy(self.row))
        with self.assertRaises(ValueError):
            self.plan()

    def test_readback_mismatch_or_missing_proof_cannot_create_receipt(self):
        with self.assertRaises(ValueError):
            verified_receipt(self.artifact, self.row, self.folder, readback_sha256='0'*64)
        with self.assertRaises(ValueError):
            verified_receipt(self.artifact, self.row, self.folder)
        with self.assertRaises(ValueError):
            verified_receipt(self.artifact, self.row, self.folder, stream_bytes=2)

    def test_large_zip_stream_limit_is_explicit_not_remote_sha(self):
        artifact = {**self.artifact, 'name': 'qa-source.zip', 'mime_type': 'application/zip'}
        row = {**self.row, 'title': artifact['name'], 'mime_type': artifact['mime_type']}
        receipt = verified_receipt(artifact, row, self.folder, stream_bytes=artifact['bytes'])
        plan = plan_transfer([artifact], {'folder_id': self.folder, 'complete': True, 'files': [row]},
                             {'folder_id': self.folder, 'receipts': [receipt]})
        self.assertEqual(plan['actions'][0]['verification'], 'STREAM_SIZE_ONLY')

    def test_partial_inventory_cannot_cause_duplicate_upload(self):
        self.inventory.pop('complete')
        with self.assertRaises(ValueError):
            self.plan()

    def test_parallel_artifact_pipelines_keep_readback_and_completion_barrier(self):
        actions = [{'name': 'qa-first.txt', 'sha256': 'a'*64, 'action': 'upload'},
                   {'name': 'qa-second.txt', 'sha256': 'b'*64, 'action': 'verify_existing'},
                   {'name': 'qa-unchanged.txt', 'sha256': 'c'*64, 'action': 'reuse'},
                   {'name': 'qa-duplicate.txt', 'sha256': 'd'*64, 'action': 'blocked_duplicates'}]
        schedule = transfer_schedule(actions)
        self.assertEqual(schedule['max_parallel_artifacts'], 3)
        self.assertEqual(len(schedule['tasks']), 2)
        self.assertEqual(schedule['tasks'][0]['steps'][0], 'upload_once')
        self.assertEqual(schedule['tasks'][1]['steps'][0], 'readback')
        self.assertTrue(all(t['steps'][-1] == 'record_receipt' for t in schedule['tasks']))
        self.assertIn('verified', schedule['barrier'])
        self.assertEqual(schedule['cleanup'], [])
        self.assertEqual(schedule['blocked_names'], ['qa-duplicate.txt'])
        self.assertIsNone(schedule['mobile_data_budget'])
        with self.assertRaises(ValueError):
            transfer_schedule(actions, concurrency=4)

    def test_unknown_files_are_reported_and_never_deleted(self):
        self.inventory['files'].append({**self.row, 'id': 'qa_unrelated_file_001', 'title': 'qa-other.txt'})
        plan = self.plan()
        self.assertEqual(plan['unreferenced_ids'], ['qa_unrelated_file_001'])
        self.assertEqual(plan['deletions'], [])

    def test_large_payload_starts_first_indexes_and_start_file_follow_verified_payloads(self):
        actions = [{'name': name, 'sha256': 'a'*64, 'action': 'upload', 'bytes': size}
                   for name, size in [('00-START-PERPLEXITY.txt', 100), ('qa-small.txt', 20),
                                      ('VERSIONSNACHWEIS.json', 50), ('qa-original.zip', 70000000)]]
        plan = transfer_schedule(actions)
        self.assertEqual([t['name'] for t in plan['tasks']],
                         ['qa-original.zip', 'qa-small.txt', 'VERSIONSNACHWEIS.json', '00-START-PERPLEXITY.txt'])
        self.assertEqual([t['stage'] for t in plan['tasks']], ['payloads', 'payloads', 'indexes', 'start'])
        self.assertEqual(len(plan['stage_barriers']), 2)

    def test_deterministic_archive_original_unicode_empty_and_bom_bytes(self):
        files = {'qa-source.py': b'# qa_synthetic\r\n', 'qa-empty.txt': b'',
                 'qa-unicode.txt': '\ufeffGrüße 🌱\r\n'.encode('utf-8')}
        with tempfile.TemporaryDirectory(prefix='qa_source_transfer_') as folder:
            first, second = Path(folder)/'qa-first.zip', Path(folder)/'qa-second.zip'
            write_verified_zip(files, first)
            write_verified_zip(dict(reversed(list(files.items()))), second)
            self.assertEqual(first.read_bytes(), second.read_bytes())
            before = first.stat().st_mtime_ns
            self.assertTrue(write_verified_zip(files, first, reuse_existing=True)['reused'])
            self.assertEqual(first.stat().st_mtime_ns, before)
            with zipfile.ZipFile(first) as archive:
                self.assertEqual({n: archive.read(n) for n in archive.namelist()}, files)
            with self.assertRaises(ValueError):
                write_verified_zip({**files, 'qa-empty.txt': b'changed'}, first, reuse_existing=True)

    def test_path_escape_and_directory_entries_rejected(self):
        with tempfile.TemporaryDirectory(prefix='qa_source_transfer_') as folder:
            with self.assertRaises(ValueError):
                write_verified_zip({'../qa-escape.txt': b''}, Path(folder)/'qa.zip')
            (Path(folder)/'qa-subdir').mkdir()
            with self.assertRaises(ValueError):
                local_artifacts(folder)

    def test_export_folder_privacy_and_utf8_verified(self):
        with tempfile.TemporaryDirectory(prefix='qa_source_transfer_') as folder:
            path = Path(folder)/'qa-example.txt'; path.write_text('qa_synthetic Grüße', encoding='utf-8')
            artifact = local_artifacts(folder)[0]
            self.assertEqual(artifact['sha256'], sha256(path.read_bytes()))
            self.assertEqual(artifact['name'], path.name)

    def test_public_compressed_image_is_stored_without_recompression(self):
        # Existing public catalogue resource; never a runtime/customer image.
        root = Path(__file__).resolve().parents[1]
        name = 'android/app/src/main/assets/plant-images/zinnia-elegans-1.jpg'
        raw = (root/name).read_bytes()
        with tempfile.TemporaryDirectory(prefix='qa_source_transfer_') as folder:
            target = Path(folder)/'qa-public-image.zip'
            write_verified_zip({name: raw}, target)
            with zipfile.ZipFile(target) as archive:
                self.assertEqual(archive.getinfo(name).compress_type, zipfile.ZIP_STORED)
                self.assertEqual(archive.read(name), raw)

    def test_native_and_connector_metadata_normalize_equally(self):
        official = {'id': self.row['id'], 'name': self.row['title'], 'size': '2',
                    'mimeType': 'text/plain', 'modifiedTime': self.row['modified_time'], 'shared': False}
        self.assertEqual(observed(official, self.folder), observed(self.row, self.folder))


if __name__ == '__main__':
    unittest.main()

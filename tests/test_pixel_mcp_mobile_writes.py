"""Actual importer/SQLite tests, exclusively temporary Drive folders and databases."""
import json
import unittest
import threading
from unittest.mock import patch
import db
import server
import test_mcp_integration as mcp_tests


class PixelMobileWrites(unittest.TestCase):
    setUp = mcp_tests.MCPIntegrationTests.setUp
    tearDown = mcp_tests.MCPIntegrationTests.tearDown

    def base(self, row):
        return {key: value for key, value in row.items() if key not in ('created_at', 'source')}

    def import_action(self, identifier, payload):
        (self.drive / 'HausGarten-Mobile-qa.json').write_text(json.dumps({
            'format': 1, 'device_id': 'qa', 'actions': [
                {'client_id': identifier, 'kind': 'worklog', 'payload': payload}
            ]}), encoding='utf-8')
        result = server.drive_sync(str(self.drive))
        snapshot = json.loads((self.drive / 'HausGarten-Laptop.json').read_text(encoding='utf-8'))
        return result, snapshot

    def test_create_read_replay_mobile_channel_no_order(self):
        payload = {'id': 'wrk_mobile_mcp_qa1', 'customer_id': self.customer['id'],
                   'order_id': '', 'work_date': '2026-10-10', 'activity': 'QA Hecke ÄÖÜ',
                   'hours': 3, 'break_minutes': 0, 'kilometers': 0, 'billed': 0,
                   'customer_visible': 1, 'source': 'ChatGPT Sprache', '_mcp_create': True}
        result, snapshot = self.import_action('mcp_pixel_qa1', payload)
        self.assertTrue(result['ok'])
        self.assertIs(snapshot['capabilities']['worklog_compare_and_save'], True)
        self.assertIn('mcp_pixel_qa1', snapshot['accepted_ids'])
        row = next(row for row in snapshot['worklogs'] if row['id'] == payload['id'])
        self.assertEqual(row['customer_id'], self.customer['id'])
        self.assertFalse(row['order_id'])
        self.assertEqual(row['hours'], 3)
        _, repeated = self.import_action('mcp_pixel_qa1', payload)
        self.assertEqual(sum(row['id'] == payload['id'] for row in repeated['worklogs']), 1)

    def test_update_read_receipt_and_parallel_change_rejected(self):
        previous = dict(self.record)
        payload = {**previous, 'notes': 'QA Nachtrag', '_mcp_base': self.base(previous)}
        _, snapshot = self.import_action('mcp_pixel_edit1', payload)
        self.assertIn('mcp_pixel_edit1', snapshot['accepted_ids'])
        changed = next(row for row in snapshot['worklogs'] if row['id'] == previous['id'])
        self.assertEqual(changed['notes'], 'QA Nachtrag')
        _, snapshot = self.import_action('mcp_pixel_edit2', {**payload, 'notes': 'stale'})
        self.assertNotIn('mcp_pixel_edit2', snapshot['accepted_ids'])
        current = next(row for row in snapshot['worklogs'] if row['id'] == previous['id'])
        self.assertEqual(current['notes'], 'QA Nachtrag')
        self.assertTrue(snapshot['sync_errors'])

    def test_remote_deleted_record_not_recreated(self):
        previous = dict(self.record)
        before = (self.drive / 'HausGarten-Laptop.json').read_bytes()
        with db.connect() as connection:
            connection.execute('DELETE FROM worklogs WHERE id=?', (previous['id'],))
        result, snapshot = self.import_action('mcp_pixel_deleted', {**previous, '_mcp_base': self.base(previous), 'notes': 'stale'})
        self.assertNotIn('mcp_pixel_deleted', snapshot['accepted_ids'])
        self.assertFalse(any(row['id'] == previous['id'] for row in db.list_worklogs()))
        # Absence in a temporary web replica is not proof of a central Drive deletion.
        self.assertFalse(result['ok'])
        self.assertEqual((self.drive / 'HausGarten-Laptop.json').read_bytes(),before)

    def test_create_collision_not_overwrite_existing(self):
        _, snapshot = self.import_action('mcp_pixel_collision', {**self.record, '_mcp_create': True, 'activity': 'bad overwrite'})
        self.assertNotIn('mcp_pixel_collision', snapshot['accepted_ids'])
        row = next(row for row in snapshot['worklogs'] if row['id'] == self.record['id'])
        self.assertEqual(row['activity'], self.record['activity'])

    def test_compare_save_two_threads_only_one_wins(self):
        outcomes = []
        barrier = threading.Barrier(2)
        def update(note):
            barrier.wait()
            try:
                db.save_worklog({**self.record, '_mcp_base': self.base(self.record), 'notes': note})
                outcomes.append('saved')
            except ValueError:
                outcomes.append('conflict')
        threads = [threading.Thread(target=update, args=(note,)) for note in ('A', 'B')]
        for thread in threads: thread.start()
        for thread in threads: thread.join(timeout=10)
        self.assertCountEqual(outcomes, ['saved', 'conflict'])

    def test_photo_added_between_validation_and_commit_keeps_relation(self):
        original = db._upsert
        def interleaved(table, identifier, columns, stamp, database=None, expected=None):
            if table == 'worklogs' and database is None:
                with db.connect() as connection:
                    connection.execute(
                        'INSERT INTO photos(id,customer_id,order_id,worklog_id,filename,original_name,created_at) VALUES(?,?,?,?,?,?,?)',
                        ('qa_parallel_photo', self.customer['id'], self.order['id'],
                         self.record['id'], 'qa.jpg', 'qa.jpg', db.now()))
            return original(table, identifier, columns, stamp, database, expected)
        with patch.object(db, '_upsert', side_effect=interleaved):
            with self.assertRaisesRegex(ValueError, 'Fotozuordnung'):
                db.save_worklog({**self.record, 'order_id': '', '_mcp_base': self.base(self.record)})
        with db.connect() as connection:
            record = connection.execute('SELECT order_id FROM worklogs WHERE id=?', (self.record['id'],)).fetchone()
            photo = connection.execute('SELECT order_id FROM photos WHERE id=?', ('qa_parallel_photo',)).fetchone()
        self.assertEqual(record['order_id'], self.order['id'])
        self.assertEqual(record['order_id'], photo['order_id'])


if __name__ == '__main__': unittest.main()

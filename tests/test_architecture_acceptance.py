"""Additional release acceptance checks. All business writes use temporary data.

Failures are deliberately visible: do not mask a known defect with expectedFailure.
"""
import base64
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path
import db
import server


class ArchitectureAcceptanceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='hg-acceptance-')
        self.previous = (db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR)
        root = Path(self.temp.name)
        db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR = root, root/'qa.db', root/'photos', root/'backups'
        db.init()
        self.customer = db.save_customer({'name':'QA Acceptance', 'city':'Feldkirchen'})
        self.http = server.AppHTTPServer(('127.0.0.1', 0), server.Handler)
        self.thread = threading.Thread(target=self.http.serve_forever, daemon=True)
        self.thread.start()
        self.url = f'http://127.0.0.1:{self.http.server_port}'

    def tearDown(self):
        self.http.shutdown(); self.http.server_close(); self.thread.join()
        db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR = self.previous
        self.temp.cleanup()

    def post(self, path, value):
        request = urllib.request.Request(self.url+path, data=json.dumps(value).encode(), headers={'Content-Type':'application/json'})
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                return response.status, json.loads(response.read())
        except urllib.error.HTTPError as error:
            return error.code, json.loads(error.read())

    def test_every_collection_endpoint_and_static_asset_loads(self):
        for path in ['/api/health','/api/dashboard','/api/integrity','/api/customers','/api/orders',
                     '/api/worklogs','/api/invoices','/api/photos','/api/settings',
                     '/', '/app.js', '/invoice-editor.js','/invoice-flow.js','/styles.css','/sw.js','/manifest.webmanifest']:
            with self.subTest(path=path), urllib.request.urlopen(self.url+path, timeout=5) as response:
                self.assertEqual(response.status, 200)
                self.assertTrue(response.read())

    def test_invalid_invoice_position_shape_is_client_error_not_server_crash(self):
        for positions in [[None], ['text'], [7], [[]]]:
            with self.subTest(positions=positions):
                status, result = self.post('/api/invoices', {'customer_id':self.customer['id'],
                    'invoice_number':'Entwurf-QA','positions':positions})
                self.assertEqual(status, 400, 'Malformed line item must be a readable 4xx validation response')
                self.assertNotIn('Traceback', result.get('error',''))
        self.assertEqual(db.list_invoices(), [])

    def test_new_zero_hour_work_matches_native_positive_duration_rule(self):
        status, _ = self.post('/api/worklogs', {'customer_id':self.customer['id'],
            'activity':'QA ohne Zeit','work_date':'2026-10-12','hours':0})
        self.assertEqual(status, 400, 'New manual work must not silently create zero-hour records')
        self.assertEqual(db.list_worklogs(), [])

    def test_historical_zero_hours_can_still_receive_notes_without_id_changes(self):
        work = db.save_worklog({'customer_id':self.customer['id'],'activity':'QA historische Arbeit','hours':1})
        with db.connect() as database:
            database.execute('UPDATE worklogs SET hours=0 WHERE id=?',(work['id'],))
        updated = db.save_worklog({'id':work['id'],'notes':'Historischer Nachtrag'})
        self.assertEqual(updated['id'],work['id']); self.assertEqual(updated['hours'],0)
        self.assertEqual(updated['notes'],'Historischer Nachtrag')

    def test_dates_are_canonical_for_order_and_work_sorting(self):
        for route, extra in [('/api/orders', {'title':'QA Auftrag','scheduled_date':'20261012'}),
                             ('/api/worklogs', {'activity':'QA Arbeit','hours':1,'work_date':'20261012'})]:
            with self.subTest(route=route):
                status, row = self.post(route, {'customer_id':self.customer['id'], **extra})
                self.assertTrue(status == 400 or row.get('scheduled_date', row.get('work_date')) == '2026-10-12',
                                'Accepted dates must be normalized to YYYY-MM-DD before sorting/export')

    def test_sync_failure_does_not_block_valid_action_and_replay_is_stable(self):
        package = {'actions':[{'client_id':'qa-invalid','kind':'worklog','payload':{'customer_id':'unknown','activity':'bad','hours':1}},
            {'client_id':'qa-valid','kind':'worklog','payload':{'customer_id':self.customer['id'],'activity':'QA gültig','hours':1,'work_date':'2026-10-12'}}]}
        first = self.post('/api/sync', package)
        second = self.post('/api/sync', package)
        self.assertEqual(first[0], 200); self.assertEqual(second[0], 200)
        self.assertEqual([row['ok'] for row in first[1]['results']], [False, True])
        self.assertEqual(len(db.list_worklogs()), 1)
        self.assertIsNone(second[1]['results'][1]['result'])
        self.assertTrue(db.integrity_report()['ok'])

    def test_partial_update_preserves_manual_work_relations_and_defaults(self):
        order = db.save_order({'customer_id':self.customer['id'],'title':'QA Pflege','location':'Garten 4'})
        status, created = self.post('/api/worklogs', {'customer_id':self.customer['id'],'order_id':order['id'],
            'work_date':'2026-10-12','activity':'QA Hecke','start_time':'08:00','end_time':'14:30','break_minutes':60})
        self.assertEqual(status, 201); self.assertEqual(created['hours'], 5.5)
        updated = db.save_worklog({'id':created['id'],'notes':'Nachtrag äöü'})
        for field in ['id','customer_id','order_id','hours','break_minutes','location','created_at']:
            self.assertEqual(created[field], updated[field])
        self.assertEqual(updated['kilometers'], 0); self.assertEqual(updated['customer_visible'], 1)
        self.assertEqual(updated['billed'], 0)

    def test_no_cross_customer_photo_or_invoice_write(self):
        other = db.save_customer({'name':'QA Other'})
        order = db.save_order({'customer_id':other['id'],'title':'Other order'})
        status, _ = self.post('/api/photos', {'customer_id':self.customer['id'],'order_id':order['id'],
            'original_name':'qa.png','data':base64.b64encode(b'qa-image').decode()})
        self.assertEqual(status, 400); self.assertEqual(db.list_photos(), [])
        status, _ = self.post('/api/invoices', {'customer_id':self.customer['id'],'order_id':order['id'],
            'invoice_number':'Entwurf-QA','positions':[{'title':'QA','quantity':1,'price':10}]})
        self.assertEqual(status, 400); self.assertEqual(db.list_invoices(), [])

    def test_archived_customer_history_survives_and_duplicate_is_flagged(self):
        work = db.save_worklog({'customer_id':self.customer['id'],'activity':'QA Historie','hours':1})
        db.remove_entity('customer', self.customer['id'])
        self.assertEqual(db.list_customers()[0]['active'], 0)
        self.assertEqual(db.list_worklogs()[0]['id'], work['id'])
        status, error = self.post('/api/customers', {'name':'QA Acceptance','city':'Feldkirchen'})
        self.assertEqual(status,409); self.assertEqual(error['code'],'DUPLICATE')
        self.assertTrue(db.integrity_report()['ok'])

    def test_unknown_sync_kind_and_malformed_actions_never_mutate_data(self):
        for actions in [[None], [{'client_id':'qa-unknown','kind':'execute_sql','payload':{'sql':'DROP TABLE customers'}}]]:
            status, result = self.post('/api/sync', {'actions':actions})
            self.assertEqual(status,200); self.assertFalse(result['results'][0]['ok'])
        self.assertEqual(len(db.list_customers()),1); self.assertEqual(db.list_worklogs(),[])

    def test_settings_backup_and_voice_endpoints_use_disposable_workspace(self):
        self.assertEqual(self.post('/api/settings', {'owner_name':'QA Aussteller'})[0],200)
        status, voice = self.post('/api/voice/parse', {'text':'Heute 3 Stunden Hecke geschnitten 15 Kilometer'})
        self.assertEqual(status,200); self.assertIsInstance(voice,dict)
        status, backup = self.post('/api/backup', {})
        self.assertEqual(status,200)
        self.assertTrue(list(db.BACKUP_DIR.glob('*.zip')))

if __name__ == '__main__':
    unittest.main()

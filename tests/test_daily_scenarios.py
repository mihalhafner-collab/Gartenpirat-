"""Failure scenarios on disposable synthetic data; never on productive Drive."""
import base64
import hashlib
import json
import tempfile
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from unittest.mock import patch
import db
import invoicing
import server
from photo_fixture import VALID_GIF_BASE64


class DailyScenarioTests(unittest.TestCase):
    def setUp(self):
        self.previous = db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR
        self.temp = tempfile.TemporaryDirectory(prefix='hausgarten-scenarios-')
        root = Path(self.temp.name)
        db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR = root, root/'qa.db', root/'photos', root/'backups'
        db.init()
        self.customer = db.save_customer({'name':'QA Gartenkunde','street':'Testweg 1','postal_code':'9560','city':'Feldkirchen','country':'Österreich'})
        self.order = db.save_order({'customer_id':self.customer['id'],'title':'Gartenpflege','location':'Testweg 1'})
        db.save_settings({'owner_name':'QA Aussteller','business_street':'Testweg 2','business_postal_code':'9560','business_city':'Feldkirchen',
            'invoice_issuer_confirmed':'1','invoice_numbering_confirmed':'1','invoice_number_format':'sevdesk','invoice_prefix':'RE',
            'invoice_sevdesk_last_number':'500','invoice_sevdesk_checked_at':db.now()})

    def tearDown(self):
        db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR = self.previous
        self.temp.cleanup()

    def work(self, **values):
        return db.save_worklog({'customer_id':self.customer['id'],'order_id':self.order['id'],'_require_order':True,
            'work_date':invoicing.date.today().isoformat(),'activity':'Hecke pflegen','hours':2,**values})

    def draft(self, work=None, **values):
        position = {'title':'Gartenpflege','quantity':2,'price':50}
        if work: position['worklog_id'] = work['id']
        return db.save_invoice({'customer_id':self.customer['id'],'order_id':self.order['id'],'invoice_number':'Entwurf-'+db.uid('qa'),
            'invoice_date':invoicing.date.today().isoformat(),'service_start':invoicing.date.today().isoformat(),
            'service_end':invoicing.date.today().isoformat(),'domestic_service_confirmed':True,'positions':[position],**values})

    def drive(self, actions=None):
        folder = db.DATA_DIR/'test-drive'; folder.mkdir(exist_ok=True)
        if actions is not None:
            (folder/'HausGarten-Mobile-qa.json').write_text(json.dumps({'actions':actions}),encoding='utf-8')
        return folder

    def test_parallel_double_issue_assigns_one_number_and_one_archive(self):
        draft = self.draft(self.work())
        with ThreadPoolExecutor(max_workers=4) as pool:
            issued = list(pool.map(lambda _:invoicing.issue(draft['id'],True),range(4)))
        self.assertEqual(len({row['invoice_number'] for row in issued}),1)
        with db.connect() as database:
            self.assertEqual(database.execute('SELECT count(*) FROM issued_invoice_files').fetchone()[0],1)

    def test_two_drafts_cannot_bill_same_work_twice(self):
        work = self.work(); first, second = self.draft(work), self.draft(work)
        invoicing.issue(first['id'],True)
        with self.assertRaisesRegex(ValueError,'bereits'): invoicing.issue(second['id'],True)
        self.assertEqual(next(row for row in db.list_invoices() if row['id']==second['id'])['status'],'Entwurf')

    def test_pdf_failure_rolls_back_number_archive_and_billing(self):
        work = self.work(); draft = self.draft(work)
        with patch.object(invoicing,'render_pdf',side_effect=OSError('QA PDF failure')):
            with self.assertRaises(OSError): invoicing.issue(draft['id'],True)
        self.assertFalse(db.list_worklogs()[0]['billed']); self.assertEqual(db.list_invoices()[0]['status'],'Entwurf')
        with db.connect() as database:
            self.assertEqual(database.execute('SELECT count(*) FROM issued_invoice_files').fetchone()[0],0)
        self.assertEqual(invoicing.issue(draft['id'],True)['invoice_number'],f'RE-501/{invoicing.date.today().year}')

    def test_issued_pdf_tampering_detected_and_database_preserved(self):
        draft = self.draft(); invoicing.issue(draft['id'],True)
        with db.connect() as database:
            database.execute('UPDATE issued_invoice_files SET pdf=? WHERE invoice_id=?',(b'damaged',draft['id']))
        with self.assertRaisesRegex(ValueError,'Archivprüfung'): invoicing.issued_pdf(draft['id'])
        self.assertEqual(db.list_invoices()[0]['status'],'Gestellt')

    def test_corrupt_held_deletion_list_stops_sync_without_deleting(self):
        work = self.work()
        folder = self.drive([{'client_id':'old-held-delete','kind':'delete','payload':{'entity':'worklog','id':work['id']}}])
        (db.DATA_DIR/'drive-held-deletions.json').write_text('{damaged',encoding='utf-8')
        with self.assertRaises(ValueError): server.drive_sync(str(folder))
        self.assertEqual(db.list_worklogs()[0]['id'],work['id'])
        self.assertEqual((db.DATA_DIR/'drive-held-deletions.json').read_text(),'{damaged')

    def test_invalid_held_deletion_shape_stops_sync(self):
        folder = self.drive()
        for value in [{},'old-delete',[42]]:
            with self.subTest(value=value):
                (db.DATA_DIR/'drive-held-deletions.json').write_text(json.dumps(value),encoding='utf-8')
                with self.assertRaises(ValueError): server.drive_sync(str(folder))

    def test_draft_linked_work_cannot_be_deleted_to_leave_orphan(self):
        work = self.work(); draft = self.draft(work)
        with self.assertRaises(ValueError): db.remove_entity('worklog',work['id'])
        self.assertEqual(db.list_worklogs()[0]['id'],work['id'])
        self.assertIn(work['id'],db.list_invoices()[0]['worklog_ids'])
        db.remove_entity('invoice',draft['id']); db.remove_entity('worklog',work['id'])
        self.assertEqual(db.list_worklogs(),[])

    def test_drive_archive_mismatch_visible_in_exported_snapshot(self):
        draft = self.draft(); invoicing.issue(draft['id'],True); folder = self.drive()
        self.assertTrue(server.drive_sync(str(folder))['ok'])
        pdf = next((folder/'Rechnungen').rglob('*.pdf')); pdf.write_bytes(b'Unexpected external change')
        before = hashlib.sha256(pdf.read_bytes()).hexdigest(); result = server.drive_sync(str(folder))
        self.assertFalse(result['ok']); self.assertEqual(hashlib.sha256(pdf.read_bytes()).hexdigest(),before)
        snapshot = json.loads((folder/'HausGarten-Laptop.json').read_text(encoding='utf-8'))
        self.assertTrue(any(pdf.name==error.get('file') for error in snapshot['sync_errors']))

    def test_day_from_time_to_invoice_archive_and_internal_note(self):
        work = self.work(start_time='08:00',end_time='14:30',break_minutes=60,hours=99,material='Lärchenrindenmulch fein',kilometers='12,5',notes='Nur intern')
        self.assertEqual(work['hours'],5.5)
        work = db.save_worklog({'id':work['id'],'notes':'Nachtrag Äste entfernt'})
        self.assertEqual(work['material'],'Lärchenrindenmulch fein')
        draft = self.draft(work,positions=[{'title':work['activity'],'quantity':work['hours'],'price':50,'worklog_id':work['id']}])
        self.assertEqual(draft['amount'],275); self.assertEqual(db.dashboard()['unbilled_hours'],5.5)
        issued = invoicing.issue(draft['id'],True)
        self.assertEqual(issued['invoice_number'],f'RE-501/{invoicing.date.today().year}')
        self.assertEqual(db.dashboard()['unbilled_hours'],0)
        self.assertTrue(invoicing.issued_pdf(draft['id']).startswith(b'%PDF-'))
        saved = db.save_worklog({'id':work['id'],'notes':'Interner Nachtrag nach Abschluss'})
        self.assertEqual(saved['id'],work['id']); self.assertEqual(saved['hours'],5.5)
        self.assertTrue(db.integrity_report()['ok'])

    def test_offline_update_new_action_preserves_id_and_replays_once(self):
        payload = {'id':'wrk_qa_offline_stable','customer_id':self.customer['id'],'order_id':self.order['id'],'activity':'Offline Pflege','hours':2,'_require_order':True}
        first = {'client_id':'offline-create','kind':'worklog','payload':payload}; folder = self.drive([first])
        self.assertEqual(server.drive_sync(str(folder))['imported'],1)
        previous=db.list_worklogs()[0]
        update = {'client_id':'offline-update','kind':'worklog','payload':{'id':payload['id'],'notes':'Änderung nach Abgleich',
                  '_sync_base':{key:previous[key] for key in ('id','updated_at','notes')}}}
        self.drive([first,update]); self.assertEqual(server.drive_sync(str(folder))['imported'],1)
        self.assertEqual(server.drive_sync(str(folder))['imported'],0); self.assertEqual(len(db.list_worklogs()),1)
        saved = db.list_worklogs()[0]
        self.assertEqual(saved['id'],payload['id']); self.assertEqual(saved['notes'],'Änderung nach Abgleich')
        self.assertEqual(saved['order_id'],self.order['id']); self.assertEqual(saved['hours'],2)

    def test_customer_view_hides_private_notes_and_hidden_records(self):
        db.save_customer({'id':self.customer['id'],'notes':'PRIVATE CUSTOMER'})
        visible = self.work(notes='PRIVATE WORK',customer_visible=1)
        self.work(activity='PRIVATE ACTIVITY',customer_visible=0)
        db.save_order({'customer_id':self.customer['id'],'title':'PRIVATE ORDER','customer_visible':0})
        server.save_photo({'customer_id':self.customer['id'],'original_name':'test.gif','caption':'PRIVATE PHOTO',
            'customer_visible':0,'data':VALID_GIF_BASE64})
        public = db.customer_view(self.customer['id'])
        self.assertEqual([row['id'] for row in public['worklogs']],[visible['id']]); self.assertNotIn('PRIVATE',json.dumps(public))
        self.assertNotIn('notes',public['customer'])

    def test_date_sort_filters_and_missing_customer_are_explicit(self):
        for date in ['2026-01-02','2026-10-09','2026-02-28']: self.work(work_date=date)
        self.assertEqual([row['work_date'] for row in db.list_worklogs()],['2026-10-09','2026-02-28','2026-01-02'])
        self.assertEqual(len(db.list_worklogs({'work_date':'2026-02-28'})),1)
        self.assertEqual(len(db.list_worklogs({'order_id':self.order['id'],'unbilled':True})),3)
        self.assertIsNone(db.customer_view('missing')); self.assertTrue(db.integrity_report()['ok'])

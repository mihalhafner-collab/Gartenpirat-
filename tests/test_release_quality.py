"""Release regression suite. All writes are confined to temporary synthetic data."""
import base64
import json
import sqlite3
import tempfile
import threading
import unittest
import urllib.request
import urllib.error
import zipfile
from pathlib import Path
from http.server import ThreadingHTTPServer
import db
import server
import invoicing
import import_2026_archive as archive_import
from photo_fixture import VALID_GIF_BASE64


class ReleaseQualityTests(unittest.TestCase):
    def setUp(self):
        self.temp = __import__("synthetic_fixtures").temporary_directory()
        root = Path(self.temp.name)
        self.previous = (db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR)
        db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR = root, root/'test.db', root/'photos', root/'backups'
        __import__("synthetic_fixtures").configure_db(db.DATA_DIR); db.init()
        self.customer = db.save_customer({'name':'Gärtner ÄÖÜ <Test>', 'notes':'Historie erhalten', 'active':1})
        self.order = db.save_order({'customer_id':self.customer['id'], 'title':'Pflege', 'location':'Gartenweg 1'})
        self.work = dict(customer_id=self.customer['id'], order_id=self.order['id'], activity='Hecke pflegen', hours=2, work_date='2026-10-08')

    def tearDown(self):
        db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR = self.previous
        self.temp.cleanup()

    def photo(self, **values):
        return server.save_photo({'customer_id':self.customer['id'], 'original_name':'test.gif',
            'data':VALID_GIF_BASE64, **values})

    def test_crud_all_entities_stable_ids_and_relations(self):
        work=db.save_worklog(self.work)
        invoice=db.save_invoice({'customer_id':self.customer['id'], 'order_id':self.order['id'], 'invoice_number':'Entwurf-Test',
            'positions':[{'title':'Pflege', 'quantity':2, 'price':50, 'worklog_id':work['id']}]})
        photo=self.photo(order_id=self.order['id'], worklog_id=work['id'])
        for save, row, change in [(db.save_customer,self.customer,{'name':'Neuer Kundenname'}),
                                  (db.save_order,self.order,{'title':'Pflege aktualisiert'}),
                                  (db.save_worklog,work,{'notes':'Nachtrag'}),
                                  (db.save_invoice,invoice,{'notes':'Belegnotiz'})]:
            updated=save({**row, **change})
            self.assertEqual(updated['id'],row['id'])
            self.assertEqual(updated['created_at'],row['created_at'])
        self.assertTrue(db.integrity_report()['ok'])
        db.remove_entity('photo',photo['id']); db.remove_entity('invoice',invoice['id'])
        db.remove_entity('worklog',work['id']); db.remove_entity('order',self.order['id']); db.remove_entity('customer',self.customer['id'])
        self.assertEqual(db.list_customers(),[]); self.assertEqual(db.list_orders(),[])
        self.assertEqual(db.list_worklogs(),[]); self.assertEqual(db.list_invoices(),[]); self.assertEqual(db.list_photos(),[])

    def test_partial_updates_preserve_existing_fields(self):
        row=db.save_customer({'id':self.customer['id'], 'name':'Änderung'})
        self.assertEqual(row['notes'],'Historie erhalten')
        work=db.save_worklog({**self.work,'material':'3 Säcke','customer_visible':0})
        updated=db.save_worklog({'id':work['id'],'notes':'Ergänzung'})
        self.assertEqual(updated['material'],'3 Säcke'); self.assertEqual(updated['customer_visible'],0)

    def test_partial_draft_cannot_detach_customer_from_worklog(self):
        work=db.save_worklog(self.work)
        draft=db.save_invoice({'customer_id':self.customer['id'],'invoice_number':'Entwurf-QA',
            'positions':[{'title':'Pflege','quantity':2,'price':50,'worklog_id':work['id']}]})
        other=db.save_customer({'name':'Anderer Kunde'})
        with self.assertRaises(ValueError): db.save_invoice({'id':draft['id'],'customer_id':other['id']})
        saved=db.save_invoice({'id':draft['id'],'notes':'Nachtrag'})
        self.assertEqual(saved['amount'],100)
        self.assertEqual(saved['worklog_ids'],[work['id']])

    def test_issued_performance_locked_but_numeric_equivalent_notes_allowed(self):
        db.save_customer({**self.customer,'street':'Testweg 1','postal_code':'9560','city':'QA Beispielort','country':'Österreich'})
        db.save_settings({'owner_name':'QA Aussteller','business_street':'Testweg 2','business_postal_code':'9560',
            'business_city':'QA Beispielort','tax_mode':'small_business','tax_exemption_text':'Umsatzsteuerfrei gemäß § 6 Abs. 1 Z 27 UStG.','invoice_issuer_confirmed':'1','invoice_prefix':'QA','invoice_numbering_confirmed':'1',
            'invoice_number_format':'sevdesk','invoice_sevdesk_last_number':'100','invoice_sevdesk_checked_at':db.now()})
        work=db.save_worklog(self.work)
        draft=db.save_invoice({'customer_id':self.customer['id'],'invoice_number':'Entwurf-QA',
            'invoice_date':invoicing.date.today().isoformat(),'service_start':'2026-10-08','service_end':'2026-10-08',
            'domestic_service_confirmed':True,'positions':[{'title':'Pflege','quantity':2,'price':50,'worklog_id':work['id']}]})
        invoicing.issue(draft['id'],True)
        saved=db.save_worklog({'id':work['id'],'hours':2,'notes':'Interner Nachtrag'})
        self.assertEqual(saved['notes'],'Interner Nachtrag')
        with self.assertRaises(ValueError): db.save_worklog({'id':work['id'],'hours':3})
        with self.assertRaises(ValueError): db.save_worklog({'id':work['id'],'billed':0})

    def test_long_special_text_defaults_and_missing_references(self):
        text='ÄÖÜ ß <script>alert(1)</script> 🌳 ' * 1000
        row=db.save_worklog({**self.work,'notes':text})
        self.assertEqual(row['notes'],text)
        for values in [{'customer_id':'qa_missing'},{'order_id':'qa_missing'},{'activity':'   '}]:
            with self.subTest(values=values), self.assertRaises(ValueError): db.save_worklog({**self.work,**values})
        with self.assertRaises(ValueError): self.photo(worklog_id='qa_missing')
        with self.assertRaises(ValueError): self.photo(order_id='qa_missing')

    def test_archive_parser_preserves_explicit_year_and_ambiguous_customer(self):
        self.assertEqual(archive_import.parse_date('06.10.2025').isoformat(),'2025-10-06')
        self.assertEqual(archive_import.parse_date('06.10.26').isoformat(),'2026-10-06')
        self.assertEqual(archive_import.parse_date('06.10.').isoformat(),'2026-10-06')
        fallback={'id':'qa_unassigned','name':'Ungeklärt'}
        rows=[{'id':'qa_a','name':'Georg Seidl'},{'id':'qa_b','name':'Andreas Seidl'}]
        self.assertEqual(archive_import.find_customer('Seidl',rows,fallback)['id'],'qa_unassigned')
        self.assertEqual(archive_import.find_customer('Georg Seidl',rows,fallback)['id'],'qa_a')

    def test_malformed_drive_action_does_not_block_next_valid_action(self):
        folder=db.DATA_DIR/'drive';folder.mkdir()
        (folder/'HausGarten-ChatGPT.json').write_text(json.dumps({'actions':[None,
            {'client_id':'bad','kind':'worklog','payload':[]},
            {'client_id':'good','kind':'worklog','payload':self.work}]}),encoding='utf-8')
        result=server.drive_sync(str(folder))
        self.assertEqual(result['imported'],1)
        self.assertEqual(len(result['failed']),2)
        self.assertEqual(len(db.list_worklogs()),1)

    def test_invoice_currency_rounding_and_invalid_values(self):
        result=db.calculate_invoice({'positions':[{'title':'Test','quantity':'3','price':'0,335'}]})
        self.assertEqual(result['gross_total'],1.01)
        for values in [{'quantity':0},{'quantity':-1},{'price':-1},{'price':'NaN'},{'discount':101},{'tax_rate':20}]:
            with self.subTest(values=values), self.assertRaises(ValueError):
                db.calculate_invoice({'positions':[{'title':'Test','quantity':1,'price':10,**values}]})

    def test_parallel_sync_replays_do_not_duplicate_records(self):
        folder=db.DATA_DIR/'drive';folder.mkdir()
        (folder/'HausGarten-ChatGPT.json').write_text(json.dumps({'actions':[
            {'client_id':'parallel','kind':'worklog','payload':self.work}]}),encoding='utf-8')
        failures=[]
        def run():
            try: server.drive_sync(str(folder))
            except Exception as error: failures.append(str(error))
        threads=[threading.Thread(target=run) for _ in range(4)]
        for thread in threads: thread.start()
        for thread in threads: thread.join()
        self.assertEqual(failures,[]);self.assertEqual(len(db.list_worklogs()),1)

    def test_order_reassignment_with_children_rejected(self):
        db.save_worklog(self.work)
        other=db.save_customer({'name':'Zweiter Kunde'})
        with self.assertRaises(ValueError): db.save_order({**self.order,'customer_id':other['id']})
        self.assertTrue(db.integrity_report()['ok'])

    def test_worklog_reassignment_with_photo_rejected(self):
        work=db.save_worklog(self.work); self.photo(worklog_id=work['id'])
        other=db.save_customer({'name':'Zweiter Kunde'})
        with self.assertRaises(ValueError): db.save_worklog({**work,'customer_id':other['id'],'order_id':None})
        self.assertTrue(db.integrity_report()['ok'])

    def test_worklog_reassignment_with_draft_rejected(self):
        work=db.save_worklog(self.work)
        db.save_invoice({'customer_id':self.customer['id'],'invoice_number':'Entwurf','positions':[{'title':'Pflege','quantity':2,'price':50,'worklog_id':work['id']}]})
        other=db.save_customer({'name':'Zweiter Kunde'})
        with self.assertRaises(ValueError): db.save_worklog({**work,'customer_id':other['id'],'order_id':None})

    def test_shared_photo_file_survives_single_delete(self):
        first=self.photo(); second=self.photo(caption='Zweite Zuordnung')
        self.assertEqual(first['filename'],second['filename'])
        db.remove_entity('photo',first['id'])
        self.assertTrue((db.PHOTO_DIR/second['filename']).is_file())
        db.remove_entity('photo',second['id'])
        self.assertFalse((db.PHOTO_DIR/second['filename']).exists())

    def test_photo_update_preserves_id_created_at_and_relations(self):
        work=db.save_worklog(self.work)
        photo=self.photo(order_id=self.order['id'],worklog_id=work['id'],caption='Vorher')
        updated=server.save_photo({'id':photo['id'],'data':VALID_GIF_BASE64,'caption':'Nachher'})
        self.assertEqual(updated['id'],photo['id']);self.assertEqual(updated['created_at'],photo['created_at'])
        self.assertEqual(updated['worklog_id'],work['id']);self.assertEqual(updated['caption'],'Nachher')
        self.assertEqual(len(db.list_photos()),1)

    def test_invalid_times_and_numbers_rejected(self):
        invalid=[{'start_time':'25:00','end_time':'26:00'}, {'start_time':'08:00','end_time':'12:00','break_minutes':241},
                 {'start_time':'08:00','end_time':'08:00'}, {'start_time':'12:00','end_time':'08:00'},
                 {'start_time':'08:00','end_time':''}, {'break_minutes':-1}, {'break_minutes':0.5},
                 {'hours':'nan'}, {'kilometers':'inf'}, {'kilometers':-1}, {'work_date':'2026-02-30'}]
        for values in invalid:
            with self.subTest(values=values), self.assertRaises(ValueError): db.save_worklog({**self.work,**values})
        self.assertEqual(db.list_worklogs(),[])

    def test_effective_time_and_defaults(self):
        row=db.save_worklog({**self.work,'start_time':'08:00','end_time':'14:30','break_minutes':60,'hours':99})
        self.assertEqual(row['hours'],5.5); self.assertEqual(row['kilometers'],0)
        self.assertEqual(row['billed'],0); self.assertEqual(row['customer_visible'],1)
        overnight=db.save_worklog({**self.work,'start_time':'23:30','end_time':'01:30','allow_overnight':True,'break_minutes':30})
        self.assertEqual(overnight['hours'],1.5)

    def test_offline_supplied_id_cannot_bypass_new_workflow_validation(self):
        with self.assertRaises(ValueError): db.save_worklog({**self.work,'id':'qa_wrk_offline','order_id':None,'_require_order':True})
        with self.assertRaises(ValueError): db.save_worklog({**self.work,'id':'qa_wrk_offline','hours':0,'_require_order':True})
        saved=db.save_worklog({**self.work,'id':'qa_wrk_offline','_require_order':True})
        self.assertEqual(saved['id'],'qa_wrk_offline')

    def test_invalid_order_date_rejected(self):
        with self.assertRaises(ValueError): db.save_order({**self.order,'scheduled_date':'2026-02-30'})

    def test_customer_search_includes_address(self):
        db.save_customer({**self.customer,'street':'Sonnenstraße 123'})
        self.assertEqual(len(db.list_customers('Sonnenstraße')),1)

    def test_invalid_action_does_not_block_valid_drive_actions(self):
        folder=db.DATA_DIR/'drive'; folder.mkdir()
        (folder/'HausGarten-ChatGPT.json').write_text(json.dumps({'actions':[
            {'client_id':'invalid','kind':'unknown','payload':{}},
            {'client_id':'good','kind':'worklog','payload':self.work}]}),encoding='utf-8')
        result=server.drive_sync(str(folder))
        self.assertEqual(result['imported'],1); self.assertFalse(result['ok'])
        self.assertEqual(server.drive_sync(str(folder))['imported'],0)

    def test_bad_receipts_do_not_silently_reset_duplicate_protection(self):
        folder=db.DATA_DIR/'drive'; folder.mkdir()
        (db.DATA_DIR/'drive-receipts.json').write_text('{damaged',encoding='utf-8')
        with self.assertRaises(ValueError): server.drive_sync(str(folder))

    def test_backup_restores_valid_database_and_photos(self):
        (db.DATA_DIR/'drive-held-deletions.json').write_text('["held-old-delete"]',encoding='utf-8')
        (db.DATA_DIR/'drive-receipts.json').write_text('["accepted-action"]',encoding='utf-8')
        reader=db.connect()
        try:
            reader.execute('BEGIN');reader.execute('SELECT count(*) FROM worklogs').fetchone()
            db.save_worklog(self.work); photo=self.photo()
            # Keep a reader open so recent commits remain in WAL, not the main file.
            result=server.create_backup()
        finally: reader.close()
        restored=db.DATA_DIR/'restored'; restored.mkdir()
        with zipfile.ZipFile(result['path']) as archive: archive.extractall(restored)
        connection=sqlite3.connect(restored/'haus-garten.db')
        try:
            self.assertEqual(connection.execute('PRAGMA integrity_check').fetchone()[0],'ok')
            self.assertEqual(connection.execute('SELECT count(*) FROM worklogs').fetchone()[0],1)
        finally: connection.close()
        self.assertTrue((restored/'photos'/photo['filename']).is_file())
        self.assertEqual(json.loads((restored/'drive-held-deletions.json').read_text()),['held-old-delete'])
        self.assertEqual(json.loads((restored/'drive-receipts.json').read_text()),['accepted-action'])


class HttpQualityTests(ReleaseQualityTests):
    def setUp(self):
        super().setUp()
        self.http=server.AppHTTPServer(('127.0.0.1',0),server.Handler)
        self.thread=threading.Thread(target=self.http.serve_forever,daemon=True); self.thread.start()
        self.url=f'http://127.0.0.1:{self.http.server_port}'

    def tearDown(self):
        self.http.shutdown(); self.http.server_close(); self.thread.join()
        super().tearDown()

    def request(self,path,data=None,method=None,headers=None):
        request=urllib.request.Request(self.url+path, data=json.dumps(data).encode() if data is not None else None,
            method=method,headers={'Content-Type':'application/json',**(headers or {})})
        try:
            with urllib.request.urlopen(request,timeout=5) as response: return response.status,json.loads(response.read())
        except urllib.error.HTTPError as error: return error.code,json.loads(error.read())

    def test_http_health_and_crud(self):
        self.assertEqual(self.request('/api/health')[0],200)
        status,row=self.request('/api/worklogs',self.work)
        self.assertEqual(status,201)
        self.assertEqual(self.request('/api/worklogs')[1][0]['id'],row['id'])
        self.assertEqual(self.request('/api/worklogs/'+row['id'],{**row,'notes':'HTTP Änderung'},'PUT')[1]['notes'],'HTTP Änderung')
        self.assertEqual(self.request('/api/worklogs/'+row['id'],method='DELETE')[0],200)
        self.assertEqual(self.request('/api/worklogs')[1],[])

    def test_sync_without_entity_id_is_idempotent(self):
        package={'actions':[{'client_id':'offline-fixed','kind':'worklog','payload':self.work}]}
        self.assertEqual(self.request('/api/sync',package)[0],200)
        self.assertEqual(self.request('/api/sync',package)[0],200)
        self.assertEqual(len(db.list_worklogs()),1)

    def test_invalid_body_is_readable_client_error(self):
        status,result=self.request('/api/worklogs',[])
        self.assertEqual(status,400); self.assertNotIn('has no attribute',result.get('error',''))

    def test_cross_origin_mutations_denied(self):
        status,_=self.request('/api/worklogs',self.work,headers={'Origin':'https://foreign.example'})
        self.assertEqual(status,403); self.assertEqual(db.list_worklogs(),[])

    def test_concurrent_http_requests_remain_available(self):
        from concurrent.futures import ThreadPoolExecutor
        barrier=threading.Barrier(24)
        def request(_):
            barrier.wait(timeout=5)
            return self.request('/api/health')[0]
        with ThreadPoolExecutor(max_workers=24) as workers:
            self.assertEqual(list(workers.map(request,range(24))),[200]*24)

    def test_invoice_http_confirmation_issue_and_pdf(self):
        db.save_customer({**self.customer,'street':'QA Straße 1','postal_code':'9560','city':'QA Beispielort','country':'Österreich'})
        db.save_settings({'owner_name':'QA Aussteller','business_street':'Testweg 2','business_postal_code':'9560',
            'business_city':'QA Beispielort','tax_mode':'small_business','tax_exemption_text':'Umsatzsteuerfrei gemäß § 6 Abs. 1 Z 27 UStG.','invoice_issuer_confirmed':'1','invoice_prefix':'QA','invoice_numbering_confirmed':'1',
            'invoice_number_format':'own'})
        draft=db.save_invoice({'customer_id':self.customer['id'],'invoice_number':'Entwurf-HTTP',
            'invoice_date':invoicing.date.today().isoformat(),'service_start':'2026-10-01','service_end':'2026-10-01',
            'domestic_service_confirmed':True,'positions':[{'title':'Pflege','quantity':2,'price':50}]})
        self.assertEqual(self.request('/api/invoice-issue',{'id':draft['id'],'confirmed':False})[0],400)
        status,issued=self.request('/api/invoice-issue',{'id':draft['id'],'confirmed':True})
        self.assertEqual(status,200);self.assertEqual(issued['status'],'Gestellt')
        with urllib.request.urlopen(self.url+'/api/invoice-pdf/'+draft['id'],timeout=5) as response:
            self.assertEqual(response.status,200);self.assertTrue(response.read().startswith(b'%PDF-'))

    def test_remote_authorization_and_rate_limit(self):
        from unittest.mock import patch
        import http.cookiejar
        server.AUTH_ATTEMPTS.clear()
        with patch.object(server.Handler,'is_local',return_value=False):
            self.assertEqual(self.request('/api/customers')[0],401)
            bad='000000' if server.ACCESS_PIN!='000000' else '999999'
            for _ in range(5): self.assertEqual(self.request('/api/auth',{'pin':bad})[0],401)
            self.assertEqual(self.request('/api/auth',{'pin':server.ACCESS_PIN})[0],429)
            server.AUTH_ATTEMPTS.clear()
            jar=http.cookiejar.CookieJar(); opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
            request=urllib.request.Request(self.url+'/api/auth',data=json.dumps({'pin':server.ACCESS_PIN}).encode(),headers={'Content-Type':'application/json'})
            with opener.open(request,timeout=5) as response:self.assertEqual(response.status,200)
            with opener.open(self.url+'/api/customers',timeout=5) as response:self.assertEqual(response.status,200)
        server.AUTH_ATTEMPTS.clear()


if __name__=='__main__': unittest.main()

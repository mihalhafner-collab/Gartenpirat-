import base64
import json
import tempfile
import unittest
from pathlib import Path

import db
import server
import invoicing
from photo_fixture import VALID_GIF_DATA_URI


class AppTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        db.DATA_DIR = root
        db.DB_PATH = root / "test.db"
        db.PHOTO_DIR = root / "photos"
        db.BACKUP_DIR = root / "backups"
        db.init()

    def tearDown(self):
        self.temp.cleanup()

    def test_core_workflow(self):
        customer = db.save_customer({"name": "Testkunde", "city": "Glanegg"})
        order = db.save_order({"customer_id": customer["id"], "title": "Gartenpflege", "status": "Aktiv"})
        worklog = db.save_worklog({
            "customer_id": customer["id"], "order_id": order["id"],
            "work_date": "2026-10-01", "start_time": "08:00", "end_time": "12:30",
            "break_minutes": 30, "activity": "Rasenpflege", "kilometers": "12,5",
        })
        self.assertEqual(worklog["hours"], 4.0)
        self.assertEqual(worklog["kilometers"], 12.5)
        self.assertEqual(db.dashboard()["unbilled_hours"], 4.0)
        public = db.customer_view(customer["id"])
        self.assertEqual(public["customer"]["name"], "Testkunde")
        self.assertEqual(public["worklogs"][0]["activity"], "Rasenpflege")

    def test_voice_parser(self):
        result = server.parse_voice(
            "Heute von 8 bis 12 Uhr gearbeitet, 30 Minuten Pause, 18 Kilometer, Material drei Säcke Erde."
        )
        self.assertEqual(result["start_time"], "08:00")
        self.assertEqual(result["end_time"], "12:00")
        self.assertEqual(result["break_minutes"], 30)
        self.assertEqual(result["hours"], 3.5)
        self.assertEqual(result["kilometers"], 18)
        self.assertIn("drei Säcke Erde", result["material"])

    def test_invoice_positions_and_preserved_worklogs(self):
        customer = db.save_customer({'name': 'Rechnungstest'})
        worklog = db.save_worklog({'customer_id': customer['id'], 'activity': 'Pflege', 'hours': 2})
        payload = {'customer_id': customer['id'], 'invoice_number': 'Entwurf-Test',
                   'positions': [{'title': 'Pflege', 'quantity': 2, 'price': '50,00', 'tax_rate': 0, 'discount': 10}],
                   'worklog_ids': [worklog['id']]}
        invoice = db.save_invoice(payload)
        self.assertEqual(invoice['amount'], 90)
        self.assertEqual(db.list_invoices()[0]['net_total'], 90)
        self.assertFalse(db.list_worklogs()[0]['billed'])
        db.save_invoice({**payload, 'id': invoice['id']})
        self.assertEqual(len(db.list_invoices()), 1)
        with self.assertRaises(ValueError):
            db.save_invoice({**payload, 'status': 'Gestellt'})
        with self.assertRaises(ValueError):
            db.save_invoice({**payload, 'positions': [{'title': 'Ungültig', 'quantity': -1}]})
        folder = db.DATA_DIR / 'drive'
        folder.mkdir()
        (folder / 'HausGarten-Mobile-test.json').write_text(json.dumps({'actions': [
            {'client_id': 'invoice-change-1', 'kind': 'invoice', 'payload': {**payload, 'id': invoice['id']}}
        ]}), encoding='utf-8')
        self.assertEqual(server.drive_sync(str(folder))['imported'], 1)
        self.assertEqual(server.drive_sync(str(folder))['imported'], 0)
        snapshot = json.loads((folder / 'HausGarten-Laptop.json').read_text(encoding='utf-8'))
        self.assertEqual(snapshot['invoices'][0]['positions'][0]['title'], 'Pflege')

    def test_small_business_issue_archive_and_lock(self):
        customer = db.save_customer({'name':'Testkunde', 'street':'Teststraße 1', 'postal_code':'9560', 'city':'Feldkirchen', 'country':'Österreich'})
        worklog = db.save_worklog({'customer_id':customer['id'],'activity':'Heckenpflege','hours':2})
        draft = db.save_invoice({'customer_id':customer['id'],'invoice_number':'Entwurf-Test',
            'invoice_date':invoicing.date.today().isoformat(),'service_start':'2026-10-01','service_end':'2026-10-01',
            'domestic_service_confirmed':True,'worklog_ids':[worklog['id']],
            'positions':[{'title':'Heckenpflege - Thuja occidentalis','quantity':2,'price':50,'worklog_id':worklog['id']}]})
        with self.assertRaises(ValueError): invoicing.issue(draft['id'],True)
        db.save_settings({'owner_name':'Hafner Michael','business_street':'Goethestraße 2','business_postal_code':'9560',
            'business_city':'Feldkirchen','tax_mode':'small_business','tax_exemption_text':'Umsatzsteuerfrei gemäß § 6 Abs. 1 Z 27 UStG.','invoice_issuer_confirmed':'1','invoice_prefix':'RE','invoice_numbering_confirmed':'1',
            'invoice_number_format':'sevdesk','invoice_sevdesk_last_number':'94','invoice_sevdesk_checked_at':db.now()})
        self.assertTrue(invoicing.preview(draft['id']).startswith(b'%PDF-'))
        with db.connect() as database: layout=invoicing.prepare(database,draft['id'])
        layout['positions']=[{**layout['positions'][0],'description':'Längere Tätigkeitsbeschreibung mit Umlauten: Äste, Rückschnitt, Grünfläche.'} for _ in range(35)]
        import re
        self.assertGreater(len(re.findall(rb'/Type /Page\b',invoicing.render_pdf(layout,draft=True))),1)
        issued=invoicing.issue(draft['id'],True)
        self.assertEqual(issued['invoice_number'],f'RE-95/{invoicing.date.today().year}')
        self.assertEqual(invoicing.issue(draft['id'],True)['invoice_number'],issued['invoice_number'])
        self.assertTrue(invoicing.issued_pdf(draft['id']).startswith(b'%PDF-'))
        self.assertTrue(db.list_worklogs()[0]['billed'])
        with self.assertRaises(ValueError): db.save_invoice({**draft,'status':'Entwurf'})
        with self.assertRaises(ValueError): db.remove_entity('invoice',draft['id'])
        with self.assertRaises(ValueError): db.calculate_invoice({'positions':[{'title':'Falsch','quantity':1,'price':10,'tax_rate':20}]})
        folder=db.DATA_DIR/'drive';folder.mkdir()
        server.drive_sync(str(folder))
        snapshot=json.loads((folder/'HausGarten-Laptop.json').read_text(encoding='utf-8'))
        self.assertNotIn('pdf_base64',snapshot['invoices'][0])
        self.assertEqual((folder/'HausGarten-Dokumente'/snapshot['invoices'][0]['pdf_storage_name']).read_bytes(),invoicing.issued_pdf(draft['id']))
        self.assertTrue(list((folder/'Rechnungen').rglob('*.pdf')))
        next_draft=db.save_invoice({**draft,'id':db.uid('inv'),'invoice_number':'Entwurf-2','worklog_ids':[], 'positions':[{**p,'worklog_id':''} for p in draft['positions']]})
        self.assertEqual(invoicing.issue(next_draft['id'],True)['invoice_number'],f'RE-96/{invoicing.date.today().year}')
        with self.assertRaises(ValueError): db.remove_entity('worklog',worklog['id'])

    def test_photo_and_backup(self):
        customer = db.save_customer({"name": "Fotokunde"})
        photo = server.save_photo({
            "customer_id": customer["id"], "original_name": "test.gif",
            "data": VALID_GIF_DATA_URI,
            "customer_visible": True,
        })
        self.assertTrue((db.PHOTO_DIR / photo["filename"]).exists())
        documents=db.DATA_DIR/'documents';documents.mkdir();(documents/'qa.pdf').write_bytes(b'%PDF-synthetic')
        thumbnails=db.PHOTO_DIR/'thumbnails';thumbnails.mkdir();(thumbnails/'qa.jpg').write_bytes(b'synthetic-preview')
        result = server.create_backup()
        self.assertTrue(Path(result["path"]).exists())
        import zipfile
        with zipfile.ZipFile(result['path']) as archive:
            self.assertEqual(archive.read('documents/qa.pdf'),b'%PDF-synthetic')
            self.assertEqual(archive.read('photos/thumbnails/qa.jpg'),b'synthetic-preview')

    def test_invoice_links_come_from_positions_and_cannot_cross_customers(self):
        c=db.save_customer({'name':'Erster Kunde'})
        other=db.save_customer({'name':'Anderer Kunde'})
        w=db.save_worklog({'customer_id':c['id'],'activity':'Schnitt','hours':3})
        raw={'customer_id':c['id'],'invoice_number':'Entwurf-Test','positions':[{'title':'Schnitt','quantity':3,'price':40,'worklog_id':w['id']}], 'worklog_ids':['falsche-id']}
        saved=db.save_invoice(raw)
        self.assertEqual(saved['worklog_ids'],[w['id']])
        with self.assertRaises(ValueError):db.save_invoice({**raw,'customer_id':other['id']})
        saved=db.save_invoice({**saved,'positions':[{**p,'worklog_id':''} for p in saved['positions']]})
        self.assertEqual(saved['worklog_ids'],[])

    def test_invalid_mobile_issue_requires_fresh_confirmation_and_other_actions_continue(self):
        c=db.save_customer({'name':'Rechnungskunde'})
        draft=db.save_invoice({'customer_id':c['id'],'invoice_number':'Entwurf-Test','positions':[{'title':'Pflege','quantity':1,'price':40}]})
        folder=db.DATA_DIR/'drive';folder.mkdir()
        actions=[{'client_id':'bad-issue','kind':'invoice_issue','payload':{'id':draft['id'],'confirmed':True}},
            {'client_id':'valid-customer','kind':'customer','payload':{'id':'extra-customer','name':'Weiterer Kunde'}}]
        (folder/'HausGarten-Mobile-test.json').write_text(json.dumps({'actions':actions}),encoding='utf-8')
        result=server.drive_sync(str(folder))
        self.assertEqual(result['imported'],1)
        snapshot=json.loads((folder/'HausGarten-Laptop.json').read_text(encoding='utf-8'))
        self.assertIn('bad-issue',snapshot['rejected_invoice_ids'])
        self.assertNotIn('bad-issue',snapshot['accepted_ids'])
        self.assertEqual(db.list_invoices()[0]['status'],'Entwurf')
        self.assertEqual(server.drive_sync(str(folder))['imported'],0)

    def test_held_old_deletions_do_not_block_new_work(self):
        c=db.save_customer({'name':'Bestandskunde'})
        w=db.save_worklog({'customer_id':c['id'],'activity':'Pflege','hours':1})
        folder=db.DATA_DIR/'drive';folder.mkdir()
        (db.DATA_DIR/'drive-held-deletions.json').write_text(json.dumps(['old-delete']),encoding='utf-8')
        actions=[{'client_id':'old-delete','kind':'delete','payload':{'entity':'worklog','id':w['id']}},
            {'client_id':'new-work','kind':'worklog','payload':{'customer_id':c['id'],'activity':'Neue Arbeit','hours':2}}]
        (folder/'HausGarten-Mobile-test.json').write_text(json.dumps({'actions':actions}),encoding='utf-8')
        self.assertEqual(server.drive_sync(str(folder))['imported'],1)
        self.assertEqual(len(db.list_worklogs()),2)
        self.assertEqual(server.drive_sync(str(folder))['imported'],0)

    def test_mobile_photo_snapshot_adds_small_preview_without_changing_photo(self):
        try:
            from PIL import Image
        except ImportError:
            self.skipTest("Pillow is optional; photo metadata remains usable without thumbnails.")
        import io

        customer = db.save_customer({"name": "Vorschaubildkunde"})
        image = Image.new("RGB", (640, 480), "forestgreen")
        source = io.BytesIO()
        image.save(source, format="JPEG")
        photo = server.save_photo({
            "customer_id": customer["id"], "order_id": None,
            "original_name": "garten.jpg",
            "data": "data:image/jpeg;base64," + base64.b64encode(source.getvalue()).decode(),
        })
        snapshot_photo = db.mobile_photo_thumbnails(db.list_photos())[0]
        self.assertNotIn("thumbnail_data",snapshot_photo)
        self.assertTrue((db.PHOTO_DIR/"thumbnails"/snapshot_photo["thumbnail_storage_name"]).read_bytes().startswith(b"\xff\xd8"))
        self.assertEqual(snapshot_photo["filename"], photo["filename"])

    def test_upsert_does_not_duplicate(self):
        identifier = "cus_fixed"
        db.save_customer({"id": identifier, "name": "Erster Name"})
        db.save_customer({"id": identifier, "name": "Neuer Name"})
        self.assertEqual(len(db.list_customers()), 1)
        self.assertEqual(db.list_customers()[0]["name"], "Neuer Name")

    def test_safe_remove_preserves_linked_history(self):
        customer = db.save_customer({"name": "Kunde mit Historie"})
        order = db.save_order({"customer_id": customer["id"], "title": "Heckenpflege"})
        worklog = db.save_worklog({"customer_id": customer["id"], "order_id": order["id"], "activity": "Schnitt", "hours": 1})
        archived = db.remove_entity("customer", customer["id"])
        cancelled = db.remove_entity("order", order["id"])
        self.assertTrue(archived["archived"])
        self.assertTrue(cancelled["archived"])
        self.assertEqual(db.list_customers()[0]["active"], 0)
        self.assertEqual(db.list_orders()[0]["status"], "Storniert")
        self.assertEqual(db.list_worklogs()[0]["id"], worklog["id"])

    def test_safe_remove_rejects_external_or_issued_invoice(self):
        customer = db.save_customer({"name": "Rechnungskunde"})
        invoice = db.save_invoice({"customer_id": customer["id"], "invoice_number": "R-1", "status": "Gestellt"})
        with self.assertRaisesRegex(ValueError, "Nur Entwürfe"):
            db.remove_entity("invoice", invoice["id"])
        with db.connect() as database:
            database.execute("UPDATE invoices SET sevdesk_id='sevdesk-1',status='Entwurf' WHERE id=?", (invoice["id"],))
        with self.assertRaisesRegex(ValueError, "sevDesk"):
            db.remove_entity("invoice", invoice["id"])

    def test_drive_sync_is_idempotent(self):
        customer = db.save_customer({"name": "Mobiler Kunde"})
        drive = db.DATA_DIR / "drive"
        drive.mkdir()
        action_id = "wrk_mobile_test"
        package = {
            "actions": [{
                "client_id": action_id,
                "kind": "worklog",
                "payload": {
                    "id": action_id, "customer_id": customer["id"],
                    "work_date": "2026-10-01", "activity": "Mobil erfasst", "hours": 1,
                },
            }]
        }
        (drive / "HausGarten-Mobile-test.json").write_text(json.dumps(package), encoding="utf-8")
        first = server.drive_sync(str(drive))
        second = server.drive_sync(str(drive))
        self.assertEqual(first["imported"], 1, first.get("failed"))
        self.assertEqual(second["imported"], 0)
        self.assertEqual(len(db.list_worklogs()), 1)
        snapshot = json.loads((drive / "HausGarten-Laptop.json").read_text(encoding="utf-8"))
        self.assertIn(action_id, snapshot["accepted_ids"])
        self.assertIn("invoices", snapshot)
        self.assertIn("photos", snapshot)
        chatgpt_id = "wrk_chatgpt_test"
        chatgpt_package = {
            "actions": [{
                "client_id": chatgpt_id,
                "kind": "worklog",
                "payload": {
                    "id": chatgpt_id, "customer_id": customer["id"],
                    "work_date": "2026-10-02", "activity": "Von ChatGPT vorbereitet", "hours": 1,
                },
            }]
        }
        (drive / "HausGarten-ChatGPT.json").write_text(json.dumps(chatgpt_package), encoding="utf-8")
        third = server.drive_sync(str(drive))
        self.assertEqual(third["imported"], 1, third.get("failed"))
        self.assertEqual(len(db.list_worklogs()), 2)


if __name__ == "__main__":
    unittest.main()

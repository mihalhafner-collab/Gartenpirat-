"""Isolated regressions on the operative database, journal and numbering boundary."""
import json
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path
import db
import offers
import owner_journal
import server
import sync_review

class OfferTests(unittest.TestCase):
    def setUp(self):
        self.previous=db.DATA_DIR,db.DB_PATH,db.PHOTO_DIR,db.BACKUP_DIR
        self.temp=tempfile.TemporaryDirectory(prefix='hg-offers-isolated-');root=Path(self.temp.name)
        db.DATA_DIR,db.DB_PATH,db.PHOTO_DIR,db.BACKUP_DIR=root,root/'test.db',root/'photos',root/'backups';db.init()
        self.customer=db.save_customer(dict(name='QA Hofer',street='Test 1',postal_code='1000',city='Wien',country='AT'))
        db.save_settings(dict(owner_name='QA Aussteller',business_phone='+4300000000',business_email='qa@example.invalid',business_street='Test 2',business_postal_code='1000',business_city='Wien',invoice_issuer_confirmed='1',tax_mode='small_business',tax_exemption_text='Umsatzsteuerfrei gemäß §6 Abs1 Z27 UStG.'))
    def tearDown(self):
        db.DATA_DIR,db.DB_PATH,db.PHOTO_DIR,db.BACKUP_DIR=self.previous;self.temp.cleanup()
    def draft(self,**patch):
        p=dict(id='off',offer_number='',customer_id=self.customer['id'],title='QA Pflege',document_type='fixed_price',issue_date='2026-10-18',valid_until='2026-11-18',execution_window='November',payment_terms='14 Tage',customer_type='b2b',scope_confirmed=True,binding_confirmed=True,positions=[dict(title='Pflege',quantity=14,price=50,unit='h',tax_rate=0,price_source='user_confirmed')],_mcp_create=True,updated_at='2026-10-18T10:00:00Z',**patch)
        return p
    def action(self,id,kind,p):return dict(client_id=id,kind=kind,payload=p,created_at='2026-10-18T12:00:00Z')
    def state(self):
        with db.connect() as c:return offers.snapshot(c)
    def test_four_offer_types_and_missing_price(self):
        for kind in offers.TYPES:
            p=self.draft();p['document_type']=kind;r=offers.normalize(p,None,self.state());self.assertEqual(r['gross_total'],700);self.assertTrue(offers.validate(r,self.state())['valid'])
        p=self.draft();p['positions'][0]['price']=None;r=offers.normalize(p,None,self.state());self.assertIsNone(r['gross_total']);self.assertFalse(offers.validate(r,self.state())['valid'])
    def test_draft_replay_and_stale_hash(self):
        action=self.action('create-request','offer',self.draft());r=owner_journal.run_action(action);self.assertEqual(r,owner_journal.run_action(action));self.assertEqual(len(offers.list_records()),1)
        patch={**r,'title':'Geändert','_sync_base':deepcopy(r)};patch['_sync_base']['updated_at']='stale'
        with self.assertRaisesRegex(ValueError,'WRITE_CONFLICT'):owner_journal.run_action(self.action('update-request','offer',patch))
        self.assertEqual(offers.get('off')['title'],'QA Pflege')
    def test_finalize_requires_context_and_confirmation_no_invoice(self):
        row=offers.save(self.draft());state=self.state();p=dict(id='off',expected_record_hash=sync_review.digest(row),expected_context_hash=sync_review.digest(offers.context(row,state)),confirmed=False)
        with self.assertRaisesRegex(ValueError,'Bestätigung'):owner_journal.run_action(self.action('final-negative','offer_finalize',p))
        p['confirmed']=True;db.save_settings({'owner_name':'Geändert'})
        with self.assertRaisesRegex(ValueError,'WRITE_CONFLICT'):owner_journal.run_action(self.action('context-changed','offer_finalize',p))
        self.assertEqual(db.list_invoices(),[])
    def test_final_offer_to_order_preserves_customer_and_never_invoices(self):
        row=offers.save(self.draft());p=dict(id='off',expected_record_hash=sync_review.digest(row),expected_context_hash=sync_review.digest(offers.context(row,self.state())),confirmed=True)
        row=owner_journal.run_action(self.action('final','offer_finalize',p));self.assertTrue(row['offer_number'].startswith('ANG-2026-'))
        conversion=self.action('convert','offer_convert',dict(id='off',expected_record_hash=sync_review.digest(row),confirmed=True))
        result=owner_journal.run_action(conversion);self.assertEqual(result,owner_journal.run_action(conversion));order=db.list_orders()[0];self.assertEqual(order['customer_id'],self.customer['id']);self.assertEqual(order['offer_id'],'off');self.assertEqual(result['converted_order_id'],order['id']);self.assertEqual(db.list_invoices(),[])
        current=offers.get('off')
        with self.assertRaisesRegex(ValueError,'Finalisierte'):offers.save({**current,'_sync_base':current,'title':'Verboten'})
    def test_catalog_price_source_and_b2c(self):
        entry=offers.list_records('service_template')[0];self.assertIsNone(entry['default_price']);self.assertGreaterEqual(len(offers.list_records('service_template')),32)
        entry=offers.save({**entry,'default_price':42,'price_confirmed':True,'_sync_base':deepcopy(entry)},'service_template')
        row=self.draft();row['positions'][0].update(price=42,catalog_entry_id=entry['id'],catalog_hash=sync_review.digest(entry));offers.normalize(row,None,self.state());self.assertTrue(offers.validate(row,self.state())['valid'])
        row['positions'][0]['price']=43;self.assertFalse(offers.validate(row,self.state())['valid'])
        row=self.draft();row.update(customer_type='consumer',contract_channel='distance');offers.normalize(row,None,self.state());self.assertFalse(offers.validate(row,self.state())['valid']);row.update(consumer_information='QA geprüfte Information',consumer_information_confirmed=True);self.assertTrue(offers.validate(row,self.state())['valid'])
    def test_standard_tax_decimal_and_no_hardcoded_tax_status(self):
        db.save_settings({'tax_mode':'standard','business_vat_id':'ATU00000000'});row=self.draft();row['positions'][0].update(quantity=3,price=.335,tax_rate=20);offers.normalize(row,None,self.state());self.assertEqual((row['net_total'],row['tax_total'],row['gross_total']),(1.01,.20,1.21));self.assertTrue(offers.validate(row,self.state())['valid'])
        inv=db.calculate_invoice(dict(positions=[dict(title='QA',quantity=3,price=.335,tax_rate=20)]));self.assertEqual(inv['gross_total'],1.21);self.assertEqual(inv['tax_exemption'],'')
        db.save_settings({'tax_mode':'small_business'})
        with self.assertRaises(ValueError):db.calculate_invoice(dict(positions=[dict(title='QA',quantity=1,price=1,tax_rate=20)]))
    def test_large_standard_invoice_requires_recipient_uid_and_preserves_draft(self):
        import invoicing
        db.save_settings(dict(tax_mode='standard',business_vat_id='ATU00000000',invoice_prefix='QA',invoice_number_format='own',invoice_numbering_confirmed='1'))
        payload=dict(customer_id=self.customer['id'],invoice_number='Entwurf-QA',invoice_date=invoicing.date.today().isoformat(),service_start='2026-10-01',service_end='2026-10-01',domestic_service_confirmed=True,positions=[dict(title='QA Pflege',quantity=200,price=50,tax_rate=20)])
        row=db.save_invoice(payload)
        with self.assertRaisesRegex(ValueError,'Unternehmer oder Verbraucher'):invoicing.issue(row['id'],True)
        row=db.save_invoice({**row,'recipient_type':'b2b'})
        with self.assertRaisesRegex(ValueError,'UID des unternehmerischen Empfängers'):invoicing.issue(row['id'],True)
        row=db.save_invoice({**row,'recipient_vat_id':'ATU99999999'})
        issued=invoicing.issue(row['id'],True);self.assertEqual(issued['status'],'Gestellt');self.assertEqual(db.list_invoices()[0]['recipient']['vat_id'],'ATU99999999');self.assertTrue(invoicing.issued_pdf(row['id']).startswith(b'%PDF-'))

    def test_offer_pdf_transfer_preserves_exact_bytes_and_backup(self):
        from io import BytesIO
        from reportlab.pdfgen.canvas import Canvas
        import hashlib,zipfile
        row=offers.save(self.draft());output=BytesIO();pdf=Canvas(output);pdf.drawString(30,800,'Synthetic offer PDF');pdf.save();raw=output.getvalue();sha=hashlib.sha256(raw).hexdigest();drive=db.DATA_DIR/'drive';files=drive/'HausGarten-Dokumente';files.mkdir(parents=True);(files/(sha+'.pdf')).write_bytes(raw)
        p=dict(id='off',expected_record_hash=sync_review.digest(row),expected_context_hash=sync_review.digest(offers.context(row,self.state())),confirmed=True,document_id='offer:off',pdf_storage_name=sha+'.pdf',pdf_sha256=sha,pdf_byte_size=len(raw))
        action=self.action('pdf-final','offer_finalize',p);final=owner_journal.run_action(action,drive/'HausGarten-Fotos');self.assertEqual(final,owner_journal.run_action(action,drive/'HausGarten-Fotos'));self.assertNotIn('pdf_base64',final);self.assertEqual((db.DATA_DIR/'documents'/(sha+'.pdf')).read_bytes(),raw)
        backup=server.create_backup()
        with zipfile.ZipFile(backup['path']) as archive:self.assertEqual(archive.read('documents/'+sha+'.pdf'),raw)

    def test_conversion_into_existing_order_checks_version_and_keeps_location(self):
        order=db.save_order(dict(customer_id=self.customer['id'],title='QA Bestehend',location='QA Einsatzgarten',description='Bestehender Inhalt'))
        row=offers.save(self.draft(order_id=order['id'],location='QA Einsatzgarten'));p=dict(id='off',expected_record_hash=sync_review.digest(row),expected_context_hash=sync_review.digest(offers.context(row,self.state())),confirmed=True)
        row=owner_journal.run_action(self.action('final-existing','offer_finalize',p));keys=('id','updated_at','customer_id','title','description','location','scheduled_date','status','notes','customer_visible','billing_customer_id','offer_id');base={k:order.get(k) for k in keys};args=dict(id='off',expected_record_hash=sync_review.digest(row),existing_order_id=order['id'],expected_order_hash=sync_review.digest(base),_expected_order=base,confirmed=True)
        stale={**args,'expected_order_hash':'a'*64}
        with self.assertRaisesRegex(ValueError,'WRITE_CONFLICT'):owner_journal.run_action(self.action('existing-stale','offer_convert',stale))
        owner_journal.run_action(self.action('existing-accept','offer_convert',args));self.assertEqual(len(db.list_orders()),1);saved=db.list_orders()[0];self.assertEqual(saved['location'],'QA Einsatzgarten');self.assertIn('Bestehender Inhalt',saved['description']);self.assertEqual(saved['offer_id'],'off');self.assertEqual(db.list_invoices(),[])

    def test_standard_missing_rate_can_remain_a_draft_but_cannot_finalize(self):
        db.save_settings({'tax_mode':'standard','business_vat_id':'ATU00000000'});row=self.draft();row['positions'][0]['tax_rate']=None;row=offers.save(row);self.assertIsNone(row['gross_total']);self.assertFalse(offers.validate(row,self.state())['valid'])

    def test_unknown_customer_and_binary_pdf_integrity(self):
        with self.assertRaises(ValueError):offers.save({**self.draft(),'customer_id':'Hofer ohne eindeutige ID'})
        row=offers.save(self.draft());p=dict(id='off',expected_record_hash=sync_review.digest(row),expected_context_hash=sync_review.digest(offers.context(row,self.state())),confirmed=True,pdf_sha256='a'*64,pdf_storage_name='a'*64+'.pdf',pdf_byte_size=5)
        with self.assertRaises(ValueError):owner_journal.run_action(self.action('missing-pdf','offer_finalize',p))
        self.assertEqual(offers.get('off')['status'],'draft')
    def test_drive_sync_supersedes_obsolete_resolution_after_target_applies(self):
        drive=db.DATA_DIR/'drive';drive.mkdir()
        offer_action=self.action('offer-before-resolution','offer',self.draft())
        decision=self.action('obsolete-resolution','conflict_resolution',dict(
            client_id=offer_action['client_id'],
            expected_action_hash=sync_review.digest(offer_action),
            expected_current_hash=sync_review.digest(None),
            resolution='keep_current',
            _original_action=deepcopy(offer_action),
            _current_base=None,
        ))
        (drive/'HausGarten-Mobile-test.json').write_text(json.dumps({'actions':[offer_action,decision]}),encoding='utf-8')
        result=server.drive_sync(str(drive))
        self.assertTrue(result['ok'],result['failed']);self.assertEqual(result['imported'],2)
        self.assertEqual(offers.get('off')['title'],'QA Pflege')
        snapshot=json.loads((drive/'HausGarten-Laptop.json').read_text(encoding='utf-8'))
        self.assertIn(offer_action['client_id'],snapshot['accepted_ids']);self.assertIn(decision['client_id'],snapshot['accepted_ids'])
        self.assertFalse(any(item.get('client_id')==decision['client_id'] for item in snapshot['sync_errors']))

    def test_old_snapshot_export_guard_preserves_new_offer(self):
        previous={key:[] for key in ('customers','orders','worklogs','photos','invoices')};previous['offers']=[{'id':'remote-offer'}]
        candidate={**previous,'offers':[],'service_catalog':[]}
        with self.assertRaises(ValueError):server.guard_drive_export(previous,candidate,set())
        previous.pop('offers');server.guard_drive_export(previous,candidate,set())

if __name__=='__main__':unittest.main()

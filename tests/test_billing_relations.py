"""Recipient relations, migrations and existing Drive channel; disposable data only."""
import json, sqlite3, tempfile, unittest
from pathlib import Path
import db, server, billing

class BillingTests(unittest.TestCase):
    def setUp(self):
        self.tmp=__import__("synthetic_fixtures").temporary_directory();p=Path(self.tmp.name)
        self.old=(db.DATA_DIR,db.DB_PATH,db.PHOTO_DIR,db.BACKUP_DIR)
        db.DATA_DIR,db.DB_PATH,db.PHOTO_DIR,db.BACKUP_DIR=p,p/'qa.db',p/'photos',p/'backups'
        __import__("synthetic_fixtures").configure_db(db.DATA_DIR); db.init()
        self.direct=db.save_customer({'name':'QA Müller'})
        self.pay=db.save_customer({'name':'QA Brennter'})
        self.site=db.save_customer({'name':'QA Kirschnerhof','billing_mode':'via_customer','default_billing_customer_id':self.pay['id']})
        self.other=db.save_customer({'name':'QA Thomas Hofer','billing_mode':'via_customer','default_billing_customer_id':self.pay['id']})
    def tearDown(self):
        db.DATA_DIR,db.DB_PATH,db.PHOTO_DIR,db.BACKUP_DIR=self.old;self.tmp.cleanup()
    def work(self,customer,**values):
        return db.save_worklog({'customer_id':customer['id'],'work_date':'2026-10-13','activity':'QA Pflege','hours':3,**values})
    def test_direct_default(self):
        w=self.work(self.direct);self.assertEqual(w['customer_id'],w['billing_customer_id'])
    def test_two_sites_share_recipient(self):
        a=self.work(self.site);b=self.work(self.other)
        self.assertNotEqual(a['customer_id'],b['customer_id']);self.assertEqual(a['billing_customer_id'],b['billing_customer_id'])
    def test_individual_override_preserves_customer(self):
        w=self.work(self.site,billing_customer_id=self.site['id'])
        self.assertEqual(w['billing_customer_id'],self.site['id'])
        with db.connect() as c:self.assertEqual(c.execute('SELECT default_billing_customer_id FROM customers WHERE id=?',(self.site['id'],)).fetchone()[0],self.pay['id'])
    def test_old_work_remains_direct_after_default_changes(self):
        w=self.work(self.direct)
        with db.connect() as c:c.execute('UPDATE worklogs SET billing_customer_id=NULL WHERE id=?',(w['id'],))
        db.save_customer({'id':self.direct['id'],'billing_mode':'via_customer','default_billing_customer_id':self.pay['id']})
        updated=db.save_worklog({'id':w['id'],'notes':'Nachtrag'})
        self.assertEqual(updated['billing_customer_id'],self.direct['id'])
    def test_invalid_references_and_cycle_rejected(self):
        for payload in ({'name':'QA X','billing_mode':'via_customer','default_billing_customer_id':'qa_unknown'},
                        {'id':self.pay['id'],'billing_mode':'via_customer','default_billing_customer_id':self.site['id']},
                        {'name':'QA X','billing_mode':'direct','default_billing_customer_id':self.pay['id']}):
            with self.assertRaises(ValueError):db.save_customer(payload)
        with self.assertRaises(ValueError):self.work(self.site,billing_customer_id='qa_unknown')
    def test_order_recipient_override(self):
        o=db.save_order({'customer_id':self.site['id'],'title':'QA Sonderauftrag','billing_customer_id':self.direct['id']})
        self.assertEqual(self.work(self.site,order_id=o['id'])['billing_customer_id'],self.direct['id'])
        with self.assertRaises((ValueError,sqlite3.IntegrityError)):db.save_order({'customer_id':self.site['id'],'title':'QA Falsch','billing_customer_id':'qa_unknown'})
    def test_invoice_multiple_sites_existing_relations(self):
        a=self.work(self.site);b=self.work(self.other)
        invoice=db.save_invoice({'customer_id':self.pay['id'],'invoice_number':'Entwurf-QA','positions':[
            {'title':'QA Kirschnerhof','quantity':3,'price':40,'worklog_id':a['id']},
            {'title':'QA Hofer','quantity':3,'price':40,'worklog_id':b['id']}]})
        self.assertEqual(set(invoice['worklog_ids']),{a['id'],b['id']})
        self.assertEqual(invoice['customer_id'],self.pay['id'])
        with self.assertRaises(ValueError):db.save_worklog({'id':a['id'],'billing_customer_id':self.site['id']})
    def test_wrong_invoice_recipient_rejected(self):
        a=self.work(self.site)
        with self.assertRaises(ValueError):db.save_invoice({'customer_id':self.site['id'],'invoice_number':'Entwurf-QA','positions':[{'title':'QA','quantity':1,'price':20,'worklog_id':a['id']}]})
    def test_recipient_delete_archives_preserving_relations(self):
        w=self.work(self.site);out=db.remove_entity('customer',self.pay['id'])
        self.assertTrue(out['archived']);self.assertTrue(db.integrity_report()['ok'])
        with db.connect() as c:self.assertEqual(c.execute('SELECT billing_customer_id FROM worklogs WHERE id=?',(w['id'],)).fetchone()[0],self.pay['id'])
    def test_offline_journal_replay_preserves_id_and_recipient(self):
        p={'id':'qa_wrk_qa_offline','customer_id':self.site['id'],'billing_customer_id':self.pay['id'],'work_date':'2026-10-13','activity':'QA offline','hours':2}
        action={'kind':'worklog','client_id':'qa-billing-request','payload':p}
        a=server.apply_sync_action(action);b=server.apply_sync_action(action)
        self.assertEqual(a['id'],b['id']);self.assertEqual(b['billing_customer_id'],self.pay['id'])
    def test_legacy_pending_record_keeps_actual_customer(self):
        p={'id':'qa_wrk_qa_legacy','customer_id':self.site['id'],'work_date':'2026-10-13','activity':'QA alter Auftrag','hours':2}
        row=server.apply_sync_action({'kind':'worklog','client_id':'qa-old-billing','payload':p})
        self.assertEqual(row['billing_customer_id'],self.site['id'])
    def test_additive_migration_retains_legacy_rows(self):
        w=self.work(self.direct)
        with db.connect() as c:
            # Restore old schema on a separate copied fixture, never the productive database.
            legacy=Path(self.tmp.name)/'legacy.db'
            target=sqlite3.connect(legacy);c.backup(target);target.close()
        oldpath=db.DB_PATH;db.DB_PATH=legacy
        with sqlite3.connect(legacy) as c:
            c.execute('DROP INDEX ix_worklogs_billing')
            for table,col in [('worklogs','billing_customer_id'),('orders','billing_customer_id'),('customers','default_billing_customer_id'),('customers','billing_mode')]:
                c.execute(f'ALTER TABLE {table} DROP COLUMN {col}')
        c.close()
        __import__("synthetic_fixtures").configure_db(db.DATA_DIR); db.init()
        with db.connect() as c:
            self.assertEqual(c.execute('SELECT id,customer_id FROM worklogs').fetchone()[0],w['id'])
            self.assertIsNone(c.execute('SELECT billing_customer_id FROM worklogs').fetchone()[0])
            self.assertEqual(c.execute('SELECT billing_mode FROM customers WHERE id=?',(self.direct['id'],)).fetchone()[0],'direct')
        db.DB_PATH=oldpath

if __name__=='__main__':unittest.main()

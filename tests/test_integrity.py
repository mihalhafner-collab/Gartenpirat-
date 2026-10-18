import tempfile, unittest
from pathlib import Path
import db

class IntegrityTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        root=Path(self.temp.name); db.DATA_DIR=root; db.DB_PATH=root/'test.db'; db.PHOTO_DIR=root/'photos'; db.BACKUP_DIR=root/'backups'; db.init()
    def tearDown(self): self.temp.cleanup()
    def test_duplicate_customer_including_inactive_warns(self):
        first=db.save_customer({'name':'Georg Seidl','city':'Klagenfurt','active':0})
        with self.assertRaises(db.DuplicateWarning) as ctx: db.save_customer({'name':'Georg Seidl','city':'Klagenfurt'})
        self.assertEqual(ctx.exception.code,'DUPLICATE'); self.assertEqual(ctx.exception.matches[0]['id'],first['id'])
    def test_duplicate_order_warns_but_allow_duplicate_keeps_new_id(self):
        c=db.save_customer({'name':'Werner'})
        first=db.save_order({'customer_id':c['id'],'title':'Neuanlage Hecke','description':'Besichtigung','location':'Klagenfurt','scheduled_date':'2026-10-04','status':'Geplant'})
        with self.assertRaises(db.DuplicateWarning): db.save_order({'customer_id':c['id'],'title':'Neuanlage Hecke','description':'Besichtigung','location':'Klagenfurt','scheduled_date':'2026-10-04','status':'Geplant'})
        second=db.save_order({'customer_id':c['id'],'title':'Neuanlage Hecke','description':'Besichtigung','location':'Klagenfurt','scheduled_date':'2026-10-04','status':'Geplant','allow_duplicate':1})
        self.assertNotEqual(first['id'],second['id'])
    def test_strict_worklog_autofill_and_mismatch(self):
        c=db.save_customer({'name':'Kunde'}); other=db.save_customer({'name':'Andere'})
        o=db.save_order({'customer_id':c['id'],'title':'Hecke','location':'Gartenweg 1'})
        row=db.save_worklog({'customer_id':c['id'],'order_id':o['id'],'_require_order':1,'work_date':'2026-10-08','start_time':'08:00','end_time':'12:00','break_minutes':30,'activity':'Pflege','kilometers':0})
        self.assertEqual(row['hours'],3.5); self.assertEqual(row['location'],'Gartenweg 1')
        with self.assertRaises(ValueError): db.save_worklog({'customer_id':other['id'],'order_id':o['id'],'_require_order':1,'hours':1,'activity':'Pflege'})
        with self.assertRaises(ValueError): db.save_worklog({'customer_id':c['id'],'_require_order':1,'hours':1,'activity':'Pflege'})
    def test_integrity_report_is_clean_for_valid_relations(self):
        c=db.save_customer({'name':'Kunde'}); o=db.save_order({'customer_id':c['id'],'title':'Auftrag'})
        db.save_worklog({'customer_id':c['id'],'order_id':o['id'],'hours':1,'activity':'Arbeit'})
        report=db.integrity_report(); self.assertTrue(report['ok']); self.assertEqual(report['counts']['worklogs_customer_order_mismatch'],0)

if __name__=='__main__': unittest.main()